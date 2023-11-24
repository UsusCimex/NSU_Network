package ru.nsu;
import java.net.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import ru.nsu.SnakeGame.GameField;
import ru.nsu.SnakeGame.GameLogic;
import ru.nsu.SnakeGame.Snake;
import ru.nsu.SnakesProto.*;

public class SnakeServer {
    // NE WORKING!
//    public static final String MULTICAST_ADDRESS = "239.192.0.4";
//    public static final int GAME_MULTICAST_PORT = 9192;

    public static final String MULTICAST_ADDRESS = "224.0.0.1";
    public static final int GAME_MULTICAST_PORT = 8888;

    private final long announcementDelayMS = 1000;

    private InetAddress serverAddress;

    private final AtomicLong msgSeq = new AtomicLong(0);
    private int stateOrder = 0;
    private final String serverName;
    private final long delayMS = 500;
    private GameLogic snakeGame = null;
    private final ConcurrentHashMap<Integer, GamePlayer> players = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<InetSocketAddress, Integer> addressToPlayerId = new ConcurrentHashMap<>();
    private int currentMaxId = 0;  // Простой способ генерировать ID для новых игроков
    private int playerCount = 0;
    private int maxPlayerCount = 5;
    private final DatagramSocket socket;

    Thread serverListener;
    Thread announcementThread;
    Thread gameLoop;

    public SnakeServer(String name, int port, GameField gameField, String serverIP) throws IOException {
        this.serverAddress = InetAddress.getByName(serverIP);
        serverName = name;
        socket = new DatagramSocket(port, serverAddress); // Привязываем к конкретному адресу
        snakeGame = new GameLogic(gameField);
    }

    public void start() throws IOException {
        serverListener = new Thread(() -> {
            while (!serverListener.isInterrupted()) {
                try {
                    receiveMessage();
                } catch (IOException e) {
                    System.err.println("[Server] Receive message error!");
                    break;
                }
            }
        });

        announcementThread = new Thread(() -> {
            while (!announcementThread.isInterrupted()) {
                try {
                    sendAnnouncement(InetAddress.getByName(SnakeServer.MULTICAST_ADDRESS), SnakeServer.GAME_MULTICAST_PORT);
                    Thread.sleep(announcementDelayMS);
                } catch (InterruptedException | IOException e) {
                    System.err.println("[Server] Announcement send error!");
                    break;
                }
            }
        });

        gameLoop = new Thread(() -> {
            while (!gameLoop.isInterrupted()) {
                try {
                    Thread.sleep(delayMS);
                    snakeGame.update();
                    updatePlayersScore();
                    sendStateForAll();
                } catch (IOException | InterruptedException e) {
                    System.err.println("[Server] Game loop destroyed...");
                    break;
                }
            }
        });

        serverListener.start();
        announcementThread.start();
        gameLoop.start();
    }

    public void updatePlayersScore() {
        for (Snake snake : snakeGame.getGameField().getSnakes()) {
            int playerId = snake.getPlayerID();
            GamePlayer player = players.get(playerId);
            if (player != null) {
                GamePlayer updatedPlayer = GamePlayer.newBuilder()
                        .mergeFrom(player)
                        .setScore(snake.getScore())
                        .build();
                players.put(playerId, updatedPlayer);
            }
        }
    }
    public void stop() {
        if (socket != null) socket.close();

        if(serverListener != null) serverListener.interrupt();
        if(announcementThread != null) announcementThread.interrupt();
        if(gameLoop != null) gameLoop.interrupt();
    }

    public void receiveMessage() throws IOException {
        byte[] buf = new byte[256];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);

        byte[] trimmedData = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());

        InetAddress address = packet.getAddress();
        int port = packet.getPort();

        GameMessage message = GameMessage.parseFrom(trimmedData);
        if (message.getTypeCase() != GameMessage.TypeCase.ACK) {
            System.err.println("[Server] listened " + message.getTypeCase() + " from " + address + ":" + port);
        }
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
                return;
            }
        }

        if (message.getTypeCase() != GameMessage.TypeCase.ANNOUNCEMENT && message.getTypeCase() != GameMessage.TypeCase.ACK) {
            sendAcknowledgement(message.getMsgSeq(), address, port);
        }
    }

    public void sendAnnouncement(InetAddress address, int port) throws IOException {
        GameMessage announcement = createAnnouncementMessage();
        sendGameMessage(announcement, address, port);
    }

    private GameMessage createAnnouncementMessage() {
        GameField field = snakeGame.getGameField();
        return GameMessage.newBuilder()
                .setMsgSeq(msgSeq.incrementAndGet())
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
        GameMessage.SteerMsg steer = message.getSteer();
        int playerId = getPlayerIdByAddress(address, port);

        if (playerId != -1) {
            Direction direction = steer.getDirection(); // Получение нового направления из сообщения
            snakeGame.updateDirection(playerId, direction); // Обновление направления для змеи игрока
        } else {
            System.err.println("Unknown player from " + address + ":" + port);
        }
    }

    private void handleJoin(GameMessage message, InetAddress address, int port) throws IOException {
        GameMessage.JoinMsg join = message.getJoin();
        if (canPlayerJoin()) {
            int playerId = addNewPlayer(join.getPlayerName(), address, port, join.getRequestedRole());

            ArrayList<GameState.Coord> initialPosition = snakeGame.getGameField().findValidSnakePosition();

            Snake newSnake = new Snake(initialPosition, playerId);
            snakeGame.addSnake(newSnake);

            sendAcknowledgement(message.getMsgSeq(), address, port);
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

    public void sendStateForAll() throws IOException {
        GameMessage stateMessage = createStateMessage();
        for (GamePlayer player : players.values()) {
            sendGameMessage(stateMessage, InetAddress.getByName(player.getIpAddress()), player.getPort());
        }
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
                .setMsgSeq(msgSeq.incrementAndGet())
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
                .setMsgSeq(msgSeq.incrementAndGet())
                .setError(GameMessage.ErrorMsg.newBuilder().setErrorMessage(errorMessage).build())
                .build();

        sendGameMessage(error, address, port);
    }
    private void sendAcknowledgement(long msg_seq, InetAddress address, int port) throws IOException {
        GameMessage ack = GameMessage.newBuilder()
                .setMsgSeq(msg_seq)
                .setAck(GameMessage.AckMsg.newBuilder().build())
                .build();

        sendGameMessage(ack, address, port);
    }
    private void sendGameMessage(GameMessage gameMessage, InetAddress address, int port) throws IOException {
        byte[] buffer = gameMessage.toByteArray();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        if (gameMessage.getTypeCase() != GameMessage.TypeCase.ACK) {
            System.err.println("[Server] Send message " + gameMessage.getTypeCase() + " to " + address + ":" + port);
        }
        socket.send(packet);
    }
    private int getPlayerIdByAddress(InetAddress address, int port) {
        // Поиск ID игрока по адресу и порту
        return addressToPlayerId.getOrDefault(new InetSocketAddress(address, port), -1);
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
                .setIpAddress(address.getHostAddress())
                .setPort(port)
                .setScore(0)
                .build();
        players.put(playerId, player);
        addressToPlayerId.put(new InetSocketAddress(address, port), playerId);
        return playerId;
    }
}