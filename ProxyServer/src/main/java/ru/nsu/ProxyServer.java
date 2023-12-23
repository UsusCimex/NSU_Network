package ru.nsu;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.xbill.DNS.*;
import org.xbill.DNS.Record;

public class ProxyServer {
    private final Map<Integer, ConnectionInfo> dnsQueryConnections = new HashMap<>();
    private final Map<Integer, String> pendingDNSRequests = new HashMap<>();
    private static final Map<SocketChannel, ConnectionInfo> connections = new HashMap<>();
    private static Selector selector;
    private final InetSocketAddress address;
    private final DatagramChannel dnsChannel;
    private static final InetSocketAddress dnsServerAddress = new InetSocketAddress("8.8.8.8", 53);
    public ProxyServer(int port) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        this.address = new InetSocketAddress(Objects.requireNonNull(AddressController.getAddress("Wi-Fi")), port);
        serverSocketChannel.socket().bind(address);
        serverSocketChannel.configureBlocking(false);

        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        dnsChannel = DatagramChannel.open();
        dnsChannel.configureBlocking(false);
        dnsChannel.register(selector, SelectionKey.OP_READ);
    }
    public void start() {
        System.out.println("The proxy server is running on address " + address);
        try {
            while (true) {
                int readyChannels = selector.select();
                if (readyChannels == 0) continue;

                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    try {
                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isAcceptable()) {
                            acceptConnection(key);
                        } else if (key.isReadable()) {
                            readConnection(key);
                        } else if (key.isConnectable()) {
                            finishConnection(key);
                        }
                    } catch (Exception e) {
                        if (key.channel() instanceof SocketChannel) {
                            SocketChannel sc = (SocketChannel) key.channel();
                            System.err.println(e.getMessage() + " from " + sc.getRemoteAddress());
                            if (sc != null) {
                                SocketChannel rc = connections.get(sc).getRemoteChannel();
                                sc.close();
                                connections.remove(sc);
                                if (rc != null) {
                                    rc.close();
                                    connections.remove(rc);
                                }
                            }
                        } else if (key.channel() instanceof DatagramChannel) {
                            DatagramChannel dc = (DatagramChannel) key.channel();
                            System.err.println("dcError");
                            e.printStackTrace();
                        }
                        key.cancel();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void finishConnection(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        try {
            if (channel.isConnectionPending()) {
                channel.finishConnect();
            }
            channel.register(selector, SelectionKey.OP_READ);

            ConnectionInfo connectionInfo = connections.get(channel);
            if (connectionInfo == null) {
                channel.close();
                return;
            }
            SocketChannel clientChannel = connectionInfo.getRemoteChannel();
            InetAddress destinationAddress = channel.socket().getInetAddress();
            int destinationPort = channel.socket().getPort();

            // Отправляем ответ клиенту
            ByteBuffer responseBuffer = ByteBuffer.allocate(10); //IPv4 - 10 bytes, IPv6 - 22 bytes
            responseBuffer.put((byte) 5); // Версия SOCKS5
            responseBuffer.put((byte) 0); // 0 - Успех, 1 - ошибка SOCKS сервера
            responseBuffer.put((byte) 0); // Зарезервировано
            responseBuffer.put((byte) 1); // Тип адреса, 1 - IPv4, 3 - DNS
            responseBuffer.put(destinationAddress.getAddress()); // IP-адрес
            responseBuffer.putShort((short) destinationPort); // Порт
            responseBuffer.flip();
            clientChannel.write(responseBuffer);
        } catch (IOException e) {
            key.cancel();
            channel.close();
            // Обработка ошибки подключения
        }
    }


    public static void main(String[] args) throws IOException {
        int port = 1080; // Порт прокси-сервера default 1080
        if (args.length != 0) port = Integer.parseInt(args[0]);
        ProxyServer proxy = new ProxyServer(port);
        proxy.start();
    }

    private void acceptConnection(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel != null) {
            System.out.println("A new connection to the client has been accepted: " + clientChannel.getRemoteAddress());
            clientChannel.configureBlocking(false);

            ConnectionInfo connectionInfo = new ConnectionInfo(clientChannel);
            connectionInfo.setState(ConnectionInfo.State.SOCKS_AUTHORIZATION);

            connections.put(clientChannel, connectionInfo);
            clientChannel.register(selector, SelectionKey.OP_READ);
        }
    }

    private void readConnection(SelectionKey key) throws IOException {
        if (key.channel() instanceof DatagramChannel) {
            DatagramChannel datagramChannel = (DatagramChannel) key.channel();
            ByteBuffer buffer = ByteBuffer.allocate(512); // Размер буфера может быть изменен в зависимости от потребностей
            datagramChannel.receive(buffer);
            buffer.flip();

            Message response = new Message(buffer.array());
            Record[] records = response.getSectionArray(Section.ANSWER);
            if (records.length == 0) {
                // Обработка ситуации, когда запись не найдена
                return;
            }

            InetAddress destinationAddress = null;
            for (Record record : records) {
                if (record instanceof ARecord) {
                    destinationAddress = ((ARecord) record).getAddress();
                    break; // Нашли A запись, выходим из цикла
                }
            }
            if (destinationAddress == null) {
                // Обработка ситуации, когда A запись не найдена
                return;
            }
            int queryId = response.getHeader().getID();

            // Найти соответствующее соединение
            String domain = pendingDNSRequests.get(queryId);
            if (domain != null) {
                // Получаем ConnectionInfo по ID запроса
                ConnectionInfo connectionInfo = dnsQueryConnections.get(queryId);
                if (connectionInfo == null) {
                    // Обработка ситуации, когда ConnectionInfo не найдено
                    System.err.println("Con info = null");
                    return;
                }

                SocketChannel clientChannel = connectionInfo.getClientChannel();
                int destinationPort = connectionInfo.getDestinationPort();

                // Устанавливаем соединение с удаленным сервером
                SocketChannel remoteChannel = SocketChannel.open();
                remoteChannel.configureBlocking(false);
                remoteChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));
                remoteChannel.register(selector, SelectionKey.OP_CONNECT);

                connectionInfo.setRemoteChannel(remoteChannel);
                connectionInfo.setState(ConnectionInfo.State.DATA_TRANSFER);
                connections.put(connectionInfo.getRemoteChannel(), new ConnectionInfo(connectionInfo));

                pendingDNSRequests.remove(queryId); // Удалить обработанный запрос из списка ожидания
            }
        } else if (key.channel() instanceof SocketChannel) {
            SocketChannel clientChannel = (SocketChannel) key.channel();
            ConnectionInfo connectionInfo = connections.get(clientChannel);
            if (connectionInfo != null) {
                switch (connectionInfo.getState()) {
                    case INITIAL:
                        break;
                    case SOCKS_AUTHORIZATION:
                        handleSocksAuthorization(connectionInfo);
                        connectionInfo.setState(ConnectionInfo.State.SOCKS_CONNECTION);
                        break;
                    case SOCKS_CONNECTION:
                        handleSocksConnection(connectionInfo);
                        break;
                    case DATA_TRANSFER:
                        transferData(connectionInfo);
                        break;
                }
            }
        }
    }

    private void handleSocksAuthorization(ConnectionInfo connectionInfo) throws IOException {
        SocketChannel clientChannel = connectionInfo.getClientChannel();
        ByteBuffer buffer = ByteBuffer.allocate(256);
        // Читаем данные от клиента в буфер.
        int bytesRead = clientChannel.read(buffer);
        if (bytesRead == -1) {
            throw new IOException("Authorization failed! (first read)");
        }

        // Парсим SOCKS5 протокол.
        buffer.flip();
        byte version = buffer.get();
        byte authMethodsCount = buffer.get();

        // Проверяем версию и количество методов аутентификации.
        if (version != 5 || authMethodsCount == 0) {
            throw new IOException("Authorization failed! (Version or authmethod exception)");
        }

        // Считываем список методов аутентификации, но мы не будем их анализировать, так как будем использовать анонимный доступ.
        byte[] authMethods = new byte[authMethodsCount];
        buffer.get(authMethods);

        // Отправляем ответ клиенту, говоря ему код аутенфикации
        ByteBuffer responseBuffer = ByteBuffer.allocate(2);
        responseBuffer.put((byte) 5); // Версия SOCKS5
        responseBuffer.put((byte) 0);
        responseBuffer.flip();
        clientChannel.write(responseBuffer);
    }

    private void handleSocksConnection(ConnectionInfo connectionInfo) throws IOException {
        SocketChannel clientChannel = connectionInfo.getClientChannel();
        ByteBuffer buffer = ByteBuffer.allocate(256);

        // Считываем команды от клиента
        int bytesRead = clientChannel.read(buffer);
        if (bytesRead == -1) {
            throw new IOException("Connection failed! (first read)");
        }
        buffer.flip();

        byte ver = buffer.get(); // Версия протокола (5)
        byte cmd = buffer.get(); // Команда 1 - CONNECT, 2 - BIND, 3 - UDP ASSOCIATE
        byte reserved = buffer.get(); // Зарезервировано, должно быть 0
        byte addressType = buffer.get(); // Тип адреса 1 - IPv4, 3 - IPv6, 2 - доменное имя

        if (ver != 5 || cmd != 1) {
            throw new IOException("Connection failed! (version or command exception)");
        }

        InetAddress destinationAddress;
        // В зависимости от типа адреса, вы можете обработать соответствующий запрос.
        if (addressType == 1) {
            // IPv4 адрес
            if (buffer.remaining() >= 4) {
                byte[] ipAddress = new byte[4];
                buffer.get(ipAddress); // IP адрес
                String domain = new String(ipAddress, StandardCharsets.US_ASCII);
                connectionInfo.setDestinationPort(buffer.getShort());
                resolveDomainAsync(domain, connectionInfo);
            } else {
                throw new IOException("Connection failed! (need 4 bytes to ipv4)");
            }
        } else if (addressType == 3) {
            // Доменное имя
            if (buffer.remaining() >= 1) {
                int domainLength = buffer.get() & 0xFF; // Здесь мы преобразуем в положительное значение
                if (buffer.remaining() >= domainLength) {
                    byte[] domainBytes = new byte[domainLength];
                    buffer.get(domainBytes);
                    String domain = new String(domainBytes, StandardCharsets.US_ASCII);
                    System.out.println("Client(" + clientChannel.getRemoteAddress() + ") is trying to reach a DNS address: " + domain);
                    connectionInfo.setDestinationPort(buffer.getShort());
                    resolveDomainAsync(domain, connectionInfo);
                } else {
                    throw new IOException("Connection failed! (domain >= domainLength)");
                }
            } else {
                throw new IOException("Connection failed! (no information about domain)");
            }
        } else {
            // Неподдерживаемый тип адреса, 4 - IPv6
            System.out.println("Client(" + clientChannel.getRemoteAddress() + ") have unsupported address type: " + addressType);
            throw new IOException("Connection failed! (unsupported address type)");
        }
    }

    private void transferData(ConnectionInfo connectionInfo) throws IOException {
        SocketChannel clientChannel = connectionInfo.getClientChannel();
        SocketChannel remoteChannel = connectionInfo.getRemoteChannel();

        ByteBuffer buffer = ByteBuffer.allocate(143360);
        int bytesRead = clientChannel.read(buffer);
        if (bytesRead > 0) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                remoteChannel.write(buffer);
            }
            buffer.compact();
        } else if (bytesRead == -1) {
            clientChannel.shutdownOutput();
            if (remoteChannel.socket().isInputShutdown()) {
                throw new IOException("Transfer ended!");
            }
        }
    }

    void resolveDomainAsync(String domain, ConnectionInfo connectionInfo) throws IOException {
        Record rec = Record.newRecord(new Name(domain + "."), Type.A, DClass.IN);
        Message query = Message.newQuery(rec);
        int queryId = query.getHeader().getID();

        // Связываем ID запроса с ConnectionInfo
        dnsQueryConnections.put(queryId, connectionInfo);
        pendingDNSRequests.put(queryId, domain);

        byte[] queryBytes = query.toWire();
        ByteBuffer buffer = ByteBuffer.wrap(queryBytes);
        dnsChannel.send(buffer, dnsServerAddress);
    }
}
