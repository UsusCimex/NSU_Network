package ru.nsu;
import java.net.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import ru.nsu.SnakeGame.GameField;
import ru.nsu.SnakeGame.GameLogic;
import ru.nsu.SnakeGame.Snake;
import ru.nsu.SnakesProto.*;

public class SnakeServer {
    public static final String MULTICAST_ADDRESS = "224.0.0.1";
    public static final int GAME_MULTICAST_PORT = 8888;
    public static final int CLIENT_MULTICAST_PORT = 8889;

    private long announcementDelayMS = 1000;
    private long stateDelayMS = 1000;

    private InetAddress serverAddress;

    private long msgSeq = 0;
    private int stateOrder = 0;
    private String serverName;
    private long delayMS = stateDelayMS;
    private GameLogic snakeGame = null;
    private ConcurrentHashMap<Integer, GamePlayer> players = new ConcurrentHashMap<>();
    private ConcurrentHashMap<InetSocketAddress, Integer> addressToPlayerId = new ConcurrentHashMap<>();
    private int currentMaxId = 0;  // Простой способ генерировать ID для новых игроков
    private int playerCount = 0;
    private int maxPlayerCount = 5;
    boolean running = false;
    private DatagramSocket socket;

    public SnakeServer(String name, int port, GameField gameField, String serverIP) throws IOException {
        this.serverAddress = InetAddress.getByName(serverIP);
        serverName = name;
        socket = new DatagramSocket(port, serverAddress); // Привязываем к конкретному адресу
        snakeGame = new GameLogic(gameField);
    }

    public void start() throws IOException {
        running = true;
        Thread serverListener = new Thread(() -> {
            while (running) {
                try {
                    receiveMessage();
                } catch (IOException e) {
                    System.err.println("[Server] Receive message error!");
                    running = false;
                }
            }
        });

        Thread announcementThread = new Thread(() -> {
            while (running) {
                try {
                    sendAnnouncement(InetAddress.getByName(SnakeServer.MULTICAST_ADDRESS), SnakeServer.GAME_MULTICAST_PORT);
                    Thread.sleep(announcementDelayMS);
                } catch (InterruptedException | IOException e) {
                    System.err.println("[Server] Announcement send error!");
                    running = false;
                }
            }
        });

        Thread stateThread = new Thread(() -> {
            while (running) {
                try {
                    sendState(InetAddress.getByName(SnakeServer.MULTICAST_ADDRESS), SnakeServer.CLIENT_MULTICAST_PORT);
                    Thread.sleep(stateDelayMS);
                } catch (InterruptedException | IOException e) {
                    System.err.println("[Server] State send error!");
                    running = false;
                }
            }
        });

        Thread gameLoop = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(delayMS);
                    snakeGame.update();
                } catch (InterruptedException e) {
                    System.err.println("[Server] Game loop destroyed...");
                    running = false;
                    e.printStackTrace();
                }
            }
        });

        serverListener.start();
        announcementThread.start();
        stateThread.start();
        gameLoop.start();
    }

    public void stop() {
        running = false;
        socket.close();
    }

    public void receiveMessage() throws IOException {
        byte[] buf = new byte[256];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);

        byte[] trimmedData = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());

        InetAddress address = packet.getAddress();
        int port = packet.getPort();

        GameMessage message = GameMessage.parseFrom(trimmedData);
        System.err.println("[Server] listened " + message.getTypeCase() + " from " + address + ":" + port);
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
    }

    public void sendAnnouncement(InetAddress address, int port) throws IOException {
        GameMessage announcement = createAnnouncementMessage();
        sendGameMessage(announcement, address, port);
    }

    private GameMessage createAnnouncementMessage() {
        GameField field = snakeGame.getGameField();
        return GameMessage.newBuilder()
                .setMsgSeq(++msgSeq)
                .setAnnouncement(GameMessage.AnnouncementMsg.newBuilder()
                        .addGames(GameAnnouncement.newBuilder()
                                .setPlayers(GamePlayers.newBuilder()
                                        .addAllPlayers(players.values())
                                        .build())
                                .setConfig(GameConfig.newBuilder()
                                        .setWidth(field.getWidth())
                                        .setHeight(field.getHeight())
                                        .setFoodStatic(field.getFoods().size())
                                        .setStateDelayMs((int) delayMS)
                                        .build())
                                .setCanJoin(true)
                                .setGameName(serverName)
                                .build())
                        .build())
                .build();
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
        GameMessage.JoinMsg join = message.getJoin();
        if (canPlayerJoin()) {
            int playerId = addNewPlayer(join.getPlayerName(), address, port, join.getRequestedRole());

            // Find a valid position for the new snake
            GameState.Coord initialPosition = snakeGame.getGameField().findValidSnakePosition();

            // Create a new snake for the player and add it to the game
            Snake newSnake = new Snake(new ArrayList<>(Collections.singletonList(initialPosition)), playerId);
            snakeGame.getGameField().addSnake(newSnake);

            sendAcknowledgement(playerId, address, port);
            sendState(address, port);
        } else {
            System.err.println("Player " + join.getPlayerName() + " cannot join the game");
            sendError("Cannot join the game: no space", address, port);
        }
    }

    public void sendState(InetAddress address, int port) throws IOException {
        GameMessage stateMessage = createStateMessage();
        sendGameMessage(stateMessage, address, port);
    }

    private GameMessage createStateMessage() {
        GameState.Builder gameStateBuilder = GameState.newBuilder()
                .setStateOrder(++stateOrder);

        // Добавляем еду
        gameStateBuilder.addAllFoods(snakeGame.getGameField().getFoods());

        // Преобразуем каждую змею в структуру Snake из библиотеки Protobuf
        for (Snake snake : snakeGame.getGameField().getSnakes()) {
            gameStateBuilder.addSnakes(Snake.generateSnakeProto(snake));
        }

        // Добавляем игроков
        gameStateBuilder.setPlayers(GamePlayers.newBuilder().addAllPlayers(players.values()).build());

        return GameMessage.newBuilder()
                .setMsgSeq(++msgSeq)
                .setState(GameMessage.StateMsg.newBuilder().setState(gameStateBuilder.build()).build())
                .build();
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
    // edit it
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
                .setMsgSeq(++msgSeq)
                .setError(GameMessage.ErrorMsg.newBuilder().setErrorMessage(errorMessage).build())
                .build();

        sendGameMessage(error, address, port);
    }
    private void sendAcknowledgement(int playerId, InetAddress address, int port) throws IOException {
        GameMessage ack = GameMessage.newBuilder()
                .setMsgSeq(++msgSeq)
                .setAck(GameMessage.AckMsg.newBuilder().build())
                .build();

        sendGameMessage(ack, address, port);
    }
    private void sendGameMessage(GameMessage gameMessage, InetAddress address, int port) throws IOException {
        byte[] buffer = gameMessage.toByteArray();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        System.err.println("[Server] Send message " + gameMessage.getTypeCase() + " to " + address + ":" + port);
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
}