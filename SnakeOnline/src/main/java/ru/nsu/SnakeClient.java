package ru.nsu;

import ru.nsu.SnakesProto.*;
import ru.nsu.patterns.Observer;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

public class SnakeClient {
    private Observer observer;
    private AtomicLong msgSeq = new AtomicLong(0);
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;

    private MulticastSocket multicastSocket;
    private InetAddress multicastGroup;

    Thread clientThread;
    Thread multicastClientThread;

    public SnakeClient(String serverIP, int serverPort, Observer observer) throws IOException {
        this.serverAddress = InetAddress.getByName(serverIP);
        this.socket = new DatagramSocket();
        this.multicastSocket = new MulticastSocket(SnakeServer.CLIENT_MULTICAST_PORT);
        this.multicastGroup = InetAddress.getByName(SnakeServer.MULTICAST_ADDRESS);
        this.multicastSocket.joinGroup(multicastGroup);
        this.serverPort = serverPort;
        this.observer = observer;
    }

    public void start(String playerName) throws IOException {
        sendJoinRequest(playerName);

        clientThread = new Thread(() -> {
            while (!clientThread.isInterrupted()) {
                try {
                    receiveMessage();
                } catch (IOException ex) {
                    System.err.println("[Client] Receive message error!");
                    break;
                }
            }
        });
        multicastClientThread = new Thread(() -> {
            while (!multicastClientThread.isInterrupted()) {
                try {
                    receiveMulticastMessage();
                } catch (IOException ex) {
                    System.err.println("[Client] Receive multicast message error!");
                    break;
                }
            }
        });

        clientThread.start();
        multicastClientThread.start();
    }

    public void stop() {
        if (socket != null) socket.close();
        if (multicastSocket != null) multicastSocket.close();

        if (clientThread != null) clientThread.interrupt();
        if (multicastClientThread != null) multicastClientThread.interrupt();
    }


    public void sendJoinRequest(String playerName) throws IOException {
        GameMessage joinMessage = GameMessage.newBuilder()
                .setMsgSeq(msgSeq.incrementAndGet())
                .setJoin(GameMessage.JoinMsg.newBuilder()
                        .setPlayerName(playerName)
                        .setGameName("Example Game") // Это значение должно соответствовать имени игры на сервере
                        .setRequestedRole(NodeRole.NORMAL)
                        .build())
                .build();

        sendGameMessage(joinMessage, serverAddress, serverPort);
    }

    public void sendSteer(Direction direction) throws IOException {
        GameMessage steerMessage = GameMessage.newBuilder()
                .setMsgSeq(msgSeq.incrementAndGet())
                .setSteer(GameMessage.SteerMsg.newBuilder()
                        .setDirection(direction)
                        .build())
                .build();

        sendGameMessage(steerMessage, serverAddress, serverPort);
    }

    private void receiveMessage() throws IOException {
        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        handlePacket(packet);
    }

    private void receiveMulticastMessage() throws IOException {
        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        multicastSocket.receive(packet);
        handlePacket(packet);
    }

    private void handlePacket(DatagramPacket packet) throws IOException {
        byte[] trimmedData = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());

        GameMessage message = GameMessage.parseFrom(trimmedData);
        System.err.println("[Client] listened " + message.getTypeCase());
        switch (message.getTypeCase()) {
            case PING  -> handlePing(message, serverAddress, serverPort);
            case STEER -> handleSteer(message, serverAddress, serverPort);
            case JOIN  -> handleJoin(message, serverAddress, serverPort);
            case ANNOUNCEMENT -> handleAnnouncement(message, serverAddress, serverPort);
            case STATE -> handleState(message, serverAddress, serverPort);
            case ACK   -> handleAck(message, serverAddress, serverPort);
            case ERROR -> handleError(message, serverAddress, serverPort);
            case ROLE_CHANGE -> handleRoleChange(message, serverAddress, serverPort);
            default    -> {
                System.err.println("Unknown message type (" + message.getTypeCase() + ") from " + serverAddress.toString() + serverPort);
                sendError("Unknown message type", serverAddress, serverPort);
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
        GameMessage.StateMsg stateMsg = message.getState();
        observer.update(stateMsg, address, port);
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
                .setMsgSeq(msgSeq.incrementAndGet())
                .setError(GameMessage.ErrorMsg.newBuilder().setErrorMessage(errorMessage).build())
                .build();

        sendGameMessage(error, address, port);
    }
    private void sendAcknowledgement(int playerId, InetAddress address, int port) throws IOException {
        GameMessage ack = GameMessage.newBuilder()
                .setMsgSeq(msgSeq.incrementAndGet())
                .setAck(GameMessage.AckMsg.newBuilder().build())
                .build();

        sendGameMessage(ack, address, port);
    }
    private void sendGameMessage(GameMessage gameMessage, InetAddress address, int port) throws IOException {
        byte[] buffer = gameMessage.toByteArray();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        System.err.println("[Client] Send message " + gameMessage.getTypeCase() + " to " + address + ":" + port);
        socket.send(packet);
    }
}
