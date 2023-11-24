package ru.nsu;

import javafx.scene.input.KeyCode;
import ru.nsu.SnakeGame.GameField;
import ru.nsu.UI.ServerInfo;
import ru.nsu.patterns.Observer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Arrays;

public class Controller {
    private final Observer UI;
    private final String serverIP = "localhost";
    private MulticastSocket announcementMulticastSocket = null;

    private SnakeServer server = null;
    private SnakeClient client = null;

    Thread serverListListener;

    public Controller(Observer UI) {
        this.UI = UI;
    }

    public void close() {
        if (announcementMulticastSocket != null) announcementMulticastSocket.close();
        if (serverListListener != null) serverListListener.interrupt();
        stopServer();
        stopClient();
    }

    public void start() {
        // Создадим поток, который будет получать ANNOUNCEMENT сообщения
        serverListListener = new Thread(() -> {
            try {
                announcementMulticastSocket = new MulticastSocket(SnakeServer.GAME_MULTICAST_PORT);
                announcementMulticastSocket.joinGroup(InetAddress.getByName(SnakeServer.MULTICAST_ADDRESS));

                while (!serverListListener.isInterrupted()) {
                    try {
                        byte[] buf = new byte[256];
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        announcementMulticastSocket.receive(packet);

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
        client = new SnakeClient(serverInfo.serverIPProperty().get(), serverInfo.serverPortProperty().get(), UI);
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
        server = new SnakeServer(gameName, 21212, serverGameField, serverIP);
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
