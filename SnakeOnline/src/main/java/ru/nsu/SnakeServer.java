package ru.nsu;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

import ru.nsu.SnakeGame.GameLogic;
import ru.nsu.SnakesProto.*;
public class SnakeServer {
    private GameLogic snakeGameLogic;
    private ConcurrentHashMap<Integer, GamePlayer> players = new ConcurrentHashMap<>();
    private ConcurrentHashMap<InetSocketAddress, Integer> addressToPlayerId = new ConcurrentHashMap<>();
    private int currentMaxId = 0;  // Простой способ генерировать ID для новых игроков
    private int playerCount = 0;
    private int maxPlayerCount = 5;

    private DatagramSocket socket;
    private boolean running;
    private byte[] buf = new byte[256];

    public SnakeServer(int port) throws IOException {
        socket = new DatagramSocket(port);
        snakeGameLogic = new GameLogic(30, 20);
    }

    public void setSnakeGame(GameLogic snakeGameLogic) {
        this.snakeGameLogic = snakeGameLogic;
    }

    public void start() {
        running = true;
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                InetAddress address = packet.getAddress();
                int port = packet.getPort();

                GameMessage message = GameMessage.parseFrom(packet.getData());
                switch (message.getTypeCase()) {
                    case PING  -> handlePing(message, address, port);
                    case STEER -> handleSteer(message, address, port);
                    case JOIN  -> handleJoin(message, address, port);
                    case ANNOUNCEMENT -> handleAnnouncement(message, address, port);
                    case STATE -> handleState(message, address, port);
                    case ACK   -> handleAck(message, address, port);
                    case ERROR -> handleError(message, address, port);
                    case ROLE_CHANGE -> handleRoleChange(message, address, port);
                    default    -> {
                        System.err.println("Unknown message type (" + message.getTypeCase() + ") from " + address.toString() + port);
                        sendError("Unknown message type", address, port);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                running = false;
            }
        }
        socket.close();
    }

    private void handlePing(GameMessage message, InetAddress address, int port) {
        // Обработка Ping сообщения
    }

    private void handleSteer(GameMessage message, InetAddress address, int port) {
        // Обрабатка изменения направления змеи.
        GameMessage.SteerMsg steer = message.getSteer();
        int playerId = getPlayerIdByAddress(address, port);
        updatePlayerDirection(playerId, steer.getDirection());
    }

    private void handleJoin(GameMessage message, InetAddress address, int port) throws IOException {
        // Обработка запроса на присоединение к игре.
        GameMessage.JoinMsg join = message.getJoin();
        if (canPlayerJoin()) {
            int playerId = addNewPlayer(join.getPlayerName(), address, port, join.getRequestedRole());
            sendAcknowledgement(playerId, address, port);
        } else {
            System.err.println("Player " + join.getPlayerName() + " cannot join game");
            sendError("Cannot join game: no space", address, port);
        }
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
        GameMessage.ErrorMsg error = message.getError();
        System.err.println("Error: " + error.getErrorMessage() + " from " + address.toString() + port);
    }
///////////////////pomisli
    private void handleRoleChange(GameMessage message, InetAddress address, int port) {
        // Обработка сообщения о смене роли игрока или сервера.
        GameMessage.RoleChangeMsg roleChange = message.getRoleChange();
        int senderId = message.hasSenderId() ? message.getSenderId() : -1;
        NodeRole senderRole = roleChange.hasSenderRole() ? roleChange.getSenderRole() : NodeRole.NORMAL;

        int receiverId = message.hasReceiverId() ? message.getReceiverId() : -1;
        NodeRole receiverRole = roleChange.hasReceiverRole() ? roleChange.getReceiverRole() : NodeRole.NORMAL;

        if (senderRole == NodeRole.MASTER) {
            updatePlayerRole(senderId, NodeRole.MASTER);
        }

        if (receiverId != -1) {
            updatePlayerRole(receiverId, receiverRole);
        }
    }

    private void updatePlayerRole(int playerId, NodeRole newRole) {
        GamePlayer player = players.get(playerId);
        if (player != null) {
            GamePlayer updatedPlayer = GamePlayer.newBuilder()
                    .mergeFrom(player) // Копируем значения из существующего объекта
                    .setRole(newRole)   // Устанавливаем новую роль
                    .build();

            players.put(playerId, updatedPlayer);
        }
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
    private int getPlayerIdByAddress(InetAddress address, int port) {
        // Поиск ID игрока по адресу и порту
        return addressToPlayerId.getOrDefault(new InetSocketAddress(address, port), -1);
    }
    private void updatePlayerDirection(int playerId, Direction direction) {
        // Обновление направления движения игрока
        GamePlayer player = players.get(playerId);
        if (player != null) {
            // Здесь должен быть код для обновления направления игрока
        }
    }
    private boolean canPlayerJoin() {
        return playerCount < maxPlayerCount;
    }
    private int addNewPlayer(String playerName, InetAddress address, int port, NodeRole requestedRole) {
        int playerId = ++currentMaxId;
        GamePlayer player = GamePlayer.newBuilder()
                .setId(playerId)
                .setName(playerName)
                .setRole(requestedRole)
                .setScore(0)
                .build();
        players.put(playerId, player);
        addressToPlayerId.put(new InetSocketAddress(address, port), playerId);
        return playerId;
    }


    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        try {
            SnakeServer server = new SnakeServer(port);
            System.out.println("Server started on port " + port);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}