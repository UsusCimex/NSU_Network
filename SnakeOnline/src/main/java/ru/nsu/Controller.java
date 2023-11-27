package ru.nsu;

import javafx.scene.input.KeyCode;
import ru.nsu.SnakeGame.GameField;
import ru.nsu.UI.ServerInfo;
import ru.nsu.patterns.Observer;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

public class Controller {
    private final Observer UI;
    private String serverIP;
    private MulticastSocket multicastSocket = null;

    private SnakeServer server = null;
    private SnakeClient client = null;

    Thread serverListListener;

    public Controller(Observer UI) {
        this.UI = UI;
        try {
            this.serverIP = getAddress("Wi-Fi");
            System.err.println("My ip: " + serverIP);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    public static String getAddress(String networkName) throws IOException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            // Игнорирование loopback и неактивных интерфейсов
            if (iface.isLoopback() || !iface.isUp())
                continue;

            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                // Проверка на IPv4 адрес
                if (addr instanceof java.net.Inet4Address) {
                    String ip = addr.getHostAddress();
                    if (iface.getDisplayName().contains(networkName)) return ip;
                }
            }
        }
        return "localhost";
    }

    public static NetworkInterface findNetworkInterface(String networkName) throws SocketException {
        for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (iface.isUp() && !iface.isLoopback()) {
                for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
                    if (addr.getAddress() instanceof Inet4Address) {
                        if (iface.getDisplayName().contains(networkName)) {
                            return iface;
                        }
                    }
                }
            }
        }
        return null;
    }

    public void close() {
        if (multicastSocket != null) multicastSocket.close();
        if (serverListListener != null) serverListListener.interrupt();
        stopServer();
        stopClient();
    }

    public void start() {
        // Создадим поток, который будет получать ANNOUNCEMENT сообщения
        serverListListener = new Thread(() -> {
            try {
                multicastSocket = new MulticastSocket(SnakeServer.MULTICAST_PORT);
                InetAddress group = InetAddress.getByName(SnakeServer.MULTICAST_ADDRESS);

                // Находим подходящий сетевой интерфейс
                NetworkInterface networkInterface = findNetworkInterface("Wi-Fi");
                if (networkInterface == null) {
                    System.err.println("Failed to find a suitable network interface");
                    return;
                }

                SocketAddress socketAddress = new InetSocketAddress(group, SnakeServer.MULTICAST_PORT);
                multicastSocket.joinGroup(socketAddress, networkInterface);

                while (!serverListListener.isInterrupted()) {
                    try {
                        byte[] buf = new byte[256];
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        multicastSocket.receive(packet);

                        byte[] trimmedData = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());

                        SnakesProto.GameMessage message = SnakesProto.GameMessage.parseFrom(trimmedData);
                        System.err.println("[Controller] Get Multicast message: " + message.getTypeCase() + " from " + packet.getAddress() + ":" + packet.getPort());
                        if (message.getTypeCase() != SnakesProto.GameMessage.TypeCase.ANNOUNCEMENT) continue;

                        UI.update(message.getAnnouncement(), packet.getAddress(), packet.getPort());
                    } catch (IOException ex) {
                        System.err.println("[Controller] Announcement receive error!");
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("[Controller] MulticastServer create error!");
            }
        });
        serverListListener.start();
    }

    public void startClient(String playerName, ServerInfo serverInfo) throws IOException {
        client = new SnakeClient(serverInfo, UI);
        client.start(playerName);
    }

    public void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }
    public void stopClient() {
        if (client != null) {
            client.stop();
            client = null;
        }
    }

    public void startServer(String gameName, GameField serverGameField) throws IOException {
        server = new SnakeServer(gameName, SnakeServer.MULTICAST_PORT, serverGameField, serverIP, multicastSocket);
        server.start();
    }

    public void sendSteerMsg(KeyCode code) throws IOException {
        switch (code) {
            case A, LEFT  -> client.sendSteer(SnakesProto.Direction.LEFT);
            case S, DOWN  -> client.sendSteer(SnakesProto.Direction.DOWN);
            case D, RIGHT -> client.sendSteer(SnakesProto.Direction.RIGHT);
            case W, UP    -> client.sendSteer(SnakesProto.Direction.UP);
        }
    }

    public String getServerIP() {
        return serverIP;
    }
}
