package ru.nsu;

import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

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

public class ProxyServer {
    private static final Map<SocketChannel, SocketChannel> sockets = new HashMap<>();
    private static Selector selector;
    public static void main(String[] args) throws IOException {
        int proxyPort = 1080; // Порт прокси-сервера default 1080
        if (args.length != 0) proxyPort = Integer.parseInt(args[0]);

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        InetSocketAddress address = new InetSocketAddress(Objects.requireNonNull(AddressController.getAddress("Wi-Fi")), proxyPort);
        serverSocketChannel.socket().bind(address);
        serverSocketChannel.configureBlocking(false);

        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("The proxy server is running on address " + address);

        // Основной цикл сервера
        while (true) {
            int readyChannels = selector.select();
            if (readyChannels == 0) continue;

            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();

                try {
                    if (!key.isValid()) {
                        // Проверяем, что ключ валиден
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
                    if (key.channel() instanceof ServerSocketChannel) {
                        continue;
                    }
                    SocketChannel sc = (SocketChannel) key.channel();
                    if (sc != null) {
                        SocketChannel rc = sockets.get(sc);
                        sc.close();
                        sockets.remove(sc);
                        if (rc != null) {
                            rc.close();
                            sockets.remove(rc);
                        }
                    }
                    key.cancel();
                }
            }
        }
    }

    private static void acceptConnection(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel != null) {
            System.out.println("A new connection to the client has been accepted: " + clientChannel.getRemoteAddress());
            clientChannel.configureBlocking(true);

            SocketChannel remoteChannel = handleSocksRequest(clientChannel);

            clientChannel.configureBlocking(false);
            remoteChannel.configureBlocking(false);

            clientChannel.register(selector, SelectionKey.OP_READ);
            remoteChannel.register(selector, SelectionKey.OP_READ);
            sockets.put(clientChannel, remoteChannel);
            sockets.put(remoteChannel, clientChannel);
        }
    }
    private static void readConnection(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        SocketChannel remoteChannel = sockets.get(clientChannel);
        if (remoteChannel == null) {
            clientChannel.close();
            return;
        }

        transferData(clientChannel, remoteChannel);
    }
    private static SocketChannel handleSocksRequest(SocketChannel clientChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        // Читаем данные от клиента в буфер.
        int bytesRead = clientChannel.read(buffer);
        if (bytesRead == -1) {
            // Если клиент закрыл соединение, закрываем и его соединение и завершаем обработку.
            clientChannel.close();
            throw new IOException("Session closed");
        }

        // Парсим SOCKS5 протокол.
        buffer.flip();
        byte version = buffer.get();
        byte authMethodsCount = buffer.get();

        // Проверяем версию и количество методов аутентификации.
        if (version != 5 || authMethodsCount == 0) {
            // Версия или количество методов не поддерживается.
            clientChannel.close();
            throw new IOException("Session closed");
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

        System.err.println(clientChannel.getRemoteAddress() + " entered the correct password");
        responseBuffer = ByteBuffer.allocate(2);
        responseBuffer.put((byte) 5);
        responseBuffer.put((byte) 0);
        responseBuffer.flip();
        clientChannel.write(responseBuffer);
        clientChannel.close();

        buffer = ByteBuffer.allocate(256);
        // Считываем команды от клиента
        bytesRead = clientChannel.read(buffer);
        if (bytesRead == -1) {
            clientChannel.close();
            throw new IOException("Session closed");
        }
        buffer.flip();

        byte ver = buffer.get(); // Версия протокола (5)
        byte cmd = buffer.get(); // Команда 1 - CONNECT, 2 - BIND, 3 - UDP ASSOCIATE
        byte reserved = buffer.get(); // Зарезервировано, должно быть 0
        byte addressType = buffer.get(); // Тип адреса 1 - IPv4, 3 - IPv6, 2 - доменное имя

        InetAddress destinationAddress;
        // В зависимости от типа адреса, вы можете обработать соответствующий запрос.
        if (addressType == 1) {
            // IPv4 адрес
            if (buffer.remaining() >= 4) {
                byte[] ipAddress = new byte[4];
                buffer.get(ipAddress); // IP адрес
                destinationAddress = InetAddress.getByAddress(ipAddress);
            } else {
                // Обработка недостаточного количества данных
                clientChannel.close();
                throw new IOException("Session closed");
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
                    // Обработка недостаточного количества данных
                    clientChannel.close();
                    throw new IOException("Session closed");
                }
            } else {
                // Обработка недостаточного количества данных
                clientChannel.close();
                throw new IOException("Session closed");
            }
        } else {
            // Неподдерживаемый тип адреса, 4 - IPv6
            System.out.println("Client(" + clientChannel.getRemoteAddress() + ") have unsupported address type: " + addressType);
            clientChannel.close();
            throw new IOException("Session closed");
        }

        int destinationPort = buffer.getShort(); // Порт
        System.out.println("Client(" + clientChannel.getRemoteAddress() + ") went to the address: " + destinationAddress + ":" + destinationPort);

        // Устанавливаем соединение с удаленным сервером
        SocketChannel remoteChannel = SocketChannel.open();
        remoteChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));

        // Отправляем ответ клиенту
        responseBuffer = ByteBuffer.allocate(10); //IPv4 - 10 bytes, IPv6 - 22 bytes
        responseBuffer.put((byte) 5); // Версия SOCKS5
        responseBuffer.put((byte) 0); // 0 - Успех, 1 - ошибка SOCKS сервера
        responseBuffer.put((byte) 0); // Зарезервировано
        responseBuffer.put((byte) 1); // Тип последующего адреса, 1 - IPv4, 3 - DNS
        responseBuffer.put(destinationAddress.getAddress()); // Выданный сервером адрес
        responseBuffer.putShort((short) destinationPort); // Выданный сервером порт
        responseBuffer.flip();
        clientChannel.write(responseBuffer);

        return remoteChannel;
    }
    private static InetAddress resolveDomain(String domain) throws IOException {
        Lookup lookup = new Lookup(domain, Type.A);
        Record[] records = lookup.run();

        if (records == null || records.length == 0) {
            throw new IOException("Failed to resolve domain: " + domain);
        }

        for (Record record : records) {
            if (record instanceof ARecord aRecord) {
                String ipAddress = aRecord.getAddress().getHostAddress();
                return InetAddress.getByName(ipAddress);
            }
        }

        throw new IOException("No IPv4 address found for domain: " + domain);
    }
    private static void transferData(SocketChannel clientChannel, SocketChannel remoteChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4096);

        int bytesRead = clientChannel.read(buffer);
        if (bytesRead == -1) {
            clientChannel.close();
            remoteChannel.close();
            sockets.remove(clientChannel);
            sockets.remove(remoteChannel);
            return;
        }
        buffer.flip();

        int bytesWrite = remoteChannel.write(buffer);
        if (bytesWrite == -1) {
            clientChannel.close();
            remoteChannel.close();
            sockets.remove(clientChannel);
            sockets.remove(remoteChannel);
            return;
        }
        buffer.flip();
    }
}
