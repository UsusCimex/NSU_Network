package ru.nsu;

import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyServer {
    private static final String USERNAME = "login";
    private static final String PASSWORD = "password";
    private static ExecutorService threadPool = Executors.newFixedThreadPool(5);
    private static Map<SocketChannel, SocketChannel> sockets = new HashMap<>();
    private static Selector selector;
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: java ProxyServer port");
            throw new RuntimeException("The parameters are set incorrectly");
        }
        int proxyPort = Integer.parseInt(args[0]); // Порт прокси-сервера

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(proxyPort));
        serverSocketChannel.configureBlocking(false);

        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("The proxy server is running on port " + proxyPort);

        // Основной цикл сервера
        while (true) {
            int readyChannels = selector.select();
            if (readyChannels == 0) continue;

            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();

                if (key.isAcceptable()) {
                    // Принимаем входящее соединение и запускаем обработку в отдельном потоке
                    threadPool.execute( () -> acceptConnection(key) );
                } else if (key.isReadable()) {
                    // Транслируем соединение между remoteSocket и ClientSocket
                    threadPool.execute( () -> readConnection(key) );
                }
            }
        }
    }

    private static void acceptConnection(SelectionKey key) {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        try {
            SocketChannel clientChannel = serverChannel.accept();
            if (clientChannel != null) {
                System.out.println("A new connection to the client has been accepted: " + clientChannel.getRemoteAddress());

                SocketChannel remoteChannel = handleSocksRequest(clientChannel);

                clientChannel.configureBlocking(false);
                remoteChannel.configureBlocking(false);

                clientChannel.register(selector, SelectionKey.OP_READ);
                sockets.put(clientChannel, remoteChannel);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void readConnection(SelectionKey key) {
        try {
            System.err.println("DEBUG1");
            SocketChannel clientChannel = (SocketChannel) key.channel();
            SocketChannel remoteChannel = sockets.get(clientChannel);
            if (remoteChannel == null) {
                clientChannel.close();
                return;
            }
            System.err.println("DEBUG2");
            transferData(clientChannel, remoteChannel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static SocketChannel handleSocksRequest(SocketChannel clientChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(256); // Размер буфера может быть изменен в зависимости от ваших требований.

        // Читаем данные от клиента в буфер.
        int bytesRead = clientChannel.read(buffer);
        if (bytesRead == -1) {
            // Если клиент закрыл соединение, закрываем и его соединение и завершаем обработку.
            clientChannel.close();
            throw new IOException("Session closed");
        }

        SocketChannel remoteChannel = null;
        // Парсим SOCKS5 протокол.
        try {
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

            // Отправляем ответ клиенту, говоря ему, что мы поддерживаем анонимный доступ.
            ByteBuffer responseBuffer = ByteBuffer.allocate(2);
            responseBuffer.put((byte) 5); // Версия SOCKS5
            responseBuffer.put((byte) 0); // Метод аутентификации: 0 - не требуется, 1 - GSSAPI, 2 - USERNAME/PASSWORD
            responseBuffer.flip();
            clientChannel.write(responseBuffer);

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
            remoteChannel = SocketChannel.open();
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
        } catch (BufferUnderflowException e) {
            // Обработка ошибки BufferUnderflowException
            e.printStackTrace();
            clientChannel.close();
        }
        return remoteChannel;
    }
    private static InetAddress resolveDomain(String domain) throws IOException {
        Lookup lookup = new Lookup(domain, Type.A);
        Record[] records = lookup.run();

        if (records == null || records.length == 0) {
            throw new IOException("Failed to resolve domain: " + domain);
        }

        for (Record record : records) {
            if (record instanceof ARecord) {
                ARecord aRecord = (ARecord) record;
                String ipAddress = aRecord.getAddress().getHostAddress();
                return InetAddress.getByName(ipAddress);
            }
        }

        throw new IOException("No IPv4 address found for domain: " + domain);
    }
    private static boolean authenticate(SocketChannel clientChannel) throws IOException {
        // Читаем данные аутентификации
        ByteBuffer authData = ByteBuffer.allocate(513); // Максимальный размер логина и пароля
        int authBytesRead = clientChannel.read(authData);

        if (authBytesRead <= 0) {
            // Некорректные данные аутентификации
            return false;
        }

        authData.flip();
        byte authVersion = authData.get();
        byte usernameLength = authData.get();
        byte[] usernameBytes = new byte[usernameLength];
        authData.get(usernameBytes);
        String username = new String(usernameBytes, StandardCharsets.UTF_8);
        byte passwordLength = authData.get();
        byte[] passwordBytes = new byte[passwordLength];
        authData.get(passwordBytes);
        String password = new String(passwordBytes, StandardCharsets.UTF_8);

        if (authVersion != 0x01 || !username.equals(USERNAME) || !password.equals(PASSWORD)) {
            // Некорректные логин и/или пароль
            return false;
        }

        // Аутентификация успешна
        return true;
    }
    private static void transferData(SocketChannel clientChannel, SocketChannel remoteChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(14336);

        while (true) {
            // Читаем данные из клиентского канала и пишем их в буфер
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                // Клиент закрыл соединение
                break;
            }
            if (bytesRead > 0) {
                buffer.flip(); // Переключаем буфер в режим чтения
                remoteChannel.write(buffer); // Записываем данные в удаленный канал
                buffer.compact(); // Освобождаем буфер для дополнительного чтения
            }

            // Читаем данные из удаленного канала и пишем их в буфер
            bytesRead = remoteChannel.read(buffer);
            if (bytesRead == -1) {
                // Удаленный сервер закрыл соединение
                break;
            }
            if (bytesRead > 0) {
                buffer.flip(); // Переключаем буфер в режим чтения
                clientChannel.write(buffer); // Записываем данные в клиентский канал
                buffer.compact(); // Освобождаем буфер для дополнительного чтения
            }
        }
    }
}
