package ru.nsu;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static ru.nsu.AddressController.resolveDomain;

public class ProxyServer {
    private static final Map<SocketChannel, ConnectionInfo> connections = new HashMap<>();
    private static Selector selector;
    private final InetSocketAddress address;
    public ProxyServer(int port) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        this.address = new InetSocketAddress(Objects.requireNonNull(AddressController.getAddress("Wi-Fi")), port);
        serverSocketChannel.socket().bind(address);
        serverSocketChannel.configureBlocking(false);

        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
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
                            // Принимаем входящее соединение и запускаем обработку в отдельном потоке
                            acceptConnection(key);
                        } else if (key.isReadable()) {
                            // Транслируем соединение между remoteSocket и ClientSocket
                            readConnection(key);
                        }
                    } catch (Exception e) {
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
                        key.cancel();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
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
                    connectionInfo.setState(ConnectionInfo.State.DATA_TRANSFER);
                    connections.put(connectionInfo.getRemoteChannel(), new ConnectionInfo(connectionInfo));
                    break;
                case DATA_TRANSFER:
                    transferData(connectionInfo);
                    break;
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
                destinationAddress = InetAddress.getByAddress(ipAddress);
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
                    destinationAddress = resolveDomain(domain);
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

        int destinationPort = buffer.getShort(); // Порт
        System.out.println("Client(" + clientChannel.getRemoteAddress() + ") went to the address: " + destinationAddress + ":" + destinationPort);

        // Устанавливаем соединение с удаленным сервером
        SocketChannel remoteChannel = SocketChannel.open();
        remoteChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));
        remoteChannel.configureBlocking(false);
        remoteChannel.register(selector, SelectionKey.OP_READ);
        connectionInfo.setRemoteChannel(remoteChannel);

        // Отправляем ответ клиенту
        ByteBuffer responseBuffer = ByteBuffer.allocate(10); //IPv4 - 10 bytes, IPv6 - 22 bytes
        responseBuffer.put((byte) 5); // Версия SOCKS5
        responseBuffer.put((byte) 0); // 0 - Успех, 1 - ошибка SOCKS сервера
        responseBuffer.put((byte) 0); // Зарезервировано
        responseBuffer.put((byte) 1); // Тип последующего адреса, 1 - IPv4, 3 - DNS
        responseBuffer.put(destinationAddress.getAddress()); // Выданный сервером адрес
        responseBuffer.putShort((short) destinationPort); // Выданный сервером порт
        responseBuffer.flip();
        clientChannel.write(responseBuffer);
    }

    private void transferData(ConnectionInfo connectionInfo) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(143360);
        SocketChannel clientChannel = connectionInfo.getClientChannel();
        SocketChannel remoteChannel = connectionInfo.getRemoteChannel();

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
}
