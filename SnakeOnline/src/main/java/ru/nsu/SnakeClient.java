package ru.nsu;

import javafx.application.Application;
import ru.nsu.SnakeGame.GameField;
import ru.nsu.SnakeGame.GameUI;
import ru.nsu.SnakesProto.*;
import java.io.IOException;
import java.net.*;

public class SnakeClient {
    private DatagramSocket socket;
    private InetAddress address;
    private int serverPort;
    private boolean running;
    private byte[] buf = new byte[256];

    public SnakeClient(String address, int serverPort) throws UnknownHostException, SocketException {
        this.socket = new DatagramSocket();
        this.address = InetAddress.getByName(address);
        this.serverPort = serverPort;
    }

    public void sendJoinRequest(String playerName) throws IOException {
        GameMessage joinMessage = GameMessage.newBuilder()
                .setJoin(GameMessage.JoinMsg.newBuilder()
                        .setPlayerName(playerName)
                        .setGameName("Example Game") // Это значение должно соответствовать имени игры на сервере
                        .setRequestedRole(NodeRole.NORMAL)
                        .build())
                .build();

        buf = joinMessage.toByteArray();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, serverPort);
        socket.send(packet);
    }

    public void sendSteer(Direction direction) throws IOException {
        GameMessage steerMessage = GameMessage.newBuilder()
                .setSteer(GameMessage.SteerMsg.newBuilder()
                        .setDirection(direction)
                        .build())
                .build();

        buf = steerMessage.toByteArray();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, serverPort);
        socket.send(packet);
    }

    public void receiveState() throws IOException {
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        GameMessage message = GameMessage.parseFrom(packet.getData());
        switch (message.getTypeCase()) {
            case PING  -> handlePing(message, address, serverPort);
            case STEER -> handleSteer(message, address, serverPort);
            case JOIN  -> handleJoin(message, address, serverPort);
            case ANNOUNCEMENT -> handleAnnouncement(message, address, serverPort);
            case STATE -> handleState(message, address, serverPort);
            case ACK   -> handleAck(message, address, serverPort);
            case ERROR -> handleError(message, address, serverPort);
            case ROLE_CHANGE -> handleRoleChange(message, address, serverPort);
            default    -> {
                System.err.println("Unknown message type (" + message.getTypeCase() + ") from " + address.toString() + serverPort);
                sendError("Unknown message type", address, serverPort);
            }
        }
    }

    private void handlePing(GameMessage message, InetAddress address, int port) {
        // Обработка Ping сообщения
    }

    private void handleSteer(GameMessage message, InetAddress address, int port) {
        // Обрабатка изменения направления змеи.
    }

    private void handleJoin(GameMessage message, InetAddress address, int port) throws IOException {
        // Обработка запроса на присоединение к игре.
    }
    private void handleAnnouncement(GameMessage message, InetAddress address, int port) {
        // Обработка оповещения о существующий играх
    }

    private void handleState(GameMessage message, InetAddress address, int port) {
        // Обработка сообщения состояния игры
    }

    private void handleAck(GameMessage message, InetAddress address, int port) {
        // Обработка подтверждения сообщения.
    }

    private void handleError(GameMessage message, InetAddress address, int port) {
        // Обработка сообщения об ошибке.
    }
    private void handleRoleChange(GameMessage message, InetAddress address, int port) {
        // Обработка сообщения о смене роли игрока или сервера.
    }

    private void sendError(String errorMessage, InetAddress address, int port) throws IOException {
        GameMessage error = GameMessage.newBuilder()
                .setError(GameMessage.ErrorMsg.newBuilder().setErrorMessage(errorMessage).build())
                .build();

        sendGameMessage(error, address, port);
    }
    private void sendAcknowledgement(int playerId, InetAddress address, int port) throws IOException {
        GameMessage ack = GameMessage.newBuilder()
                .setAck(GameMessage.AckMsg.newBuilder().build())
                .build();

        sendGameMessage(ack, address, port);
    }
    private void sendGameMessage(GameMessage gameMessage, InetAddress address, int port) throws IOException {
        byte[] buffer = gameMessage.toByteArray();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        socket.send(packet);
    }

    public void start() {
        running = true;
        while (running) {
            try {
                receiveState();
            } catch (IOException e) {
                e.printStackTrace();
                running = false;
            }
        }
    }

    public static void main(String[] args) {
        try {
            int port = Integer.parseInt(args[0]);
            SnakeClient client = new SnakeClient("localhost", port);
            System.out.println("Client started and listening for game state updates...");
            Application.launch(GameUI.class, new String());
            client.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
