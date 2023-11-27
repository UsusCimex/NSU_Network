package ru.nsu;
import java.net.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
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
    public static final int MULTICAST_PORT = 8888;

    private final ConcurrentHashMap<Integer, Long> lastAckTime = new ConcurrentHashMap<>(); // Для отслеживания времени последнего Ack
    private final ConcurrentHashMap<Integer, Long> lastMsgSeqReceived = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, SentMessageInfo> sentMessages = new ConcurrentHashMap<>();
    private static final long ACK_TIMEOUT = 5000; // Таймаут в миллисекундах

    private final long announcementDelayMS = 1000;

    private int senderId = 1;
    private int deputyId = -1;

    private InetAddress serverAddress;

    private final AtomicLong msgSeq = new AtomicLong(0);
    private int stateOrder = 0;
    private final String serverName;
    private GameLogic snakeGame = null;
    private final ConcurrentHashMap<Integer, GamePlayer> players = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<InetSocketAddress, Integer> addressToPlayerId = new ConcurrentHashMap<>();
    private int currentMaxId = 0;
    private int playerCount = 0;
    private int maxPlayerCount = 5;
    private final DatagramSocket socket;
    private final MulticastSocket multicastSocket;

    private Thread serverListener;
    private Thread announcementThread;
    private Thread playerChecker;
    private Thread gameLoop;

    public SnakeServer(String name, GameField gameField, MulticastSocket multicastSocket) throws IOException {
        String myAddress = Controller.getAddress("Wi-Fi");
        System.err.println("My address: " + myAddress);
        this.serverAddress = InetAddress.getByName(myAddress);
        serverName = name;
        socket = new DatagramSocket(MULTICAST_PORT, serverAddress); // Привязываем к конкретному адресу

        if (gameField == null) {
            throw new IllegalArgumentException("GameField must not be null");
        }

        snakeGame = new GameLogic(gameField);

        this.multicastSocket = multicastSocket;
        // Находим подходящий сетевой интерфейс
        NetworkInterface networkInterface = Controller.findNetworkInterface("Wi-Fi");
        if (networkInterface == null) {
            System.err.println("Failed to find a suitable network interface for multicast");
            return;
        }
        multicastSocket.setNetworkInterface(networkInterface);
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
                    sendAnnouncement(InetAddress.getByName(SnakeServer.MULTICAST_ADDRESS), SnakeServer.MULTICAST_PORT);
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
                    Thread.sleep(snakeGame.getGameField().getDelayMS());
                    snakeGame.update();
                    updatePlayersScore();
                    sendStateForAll();
                } catch (InterruptedException | IOException e) {
                    System.err.println("[Server] Game loop destroyed...");
                    break;
                }
            }
        });

        playerChecker = new Thread(() -> {
            while (!playerChecker.isInterrupted()) {
                try {
                    Thread.sleep(ACK_TIMEOUT);
                    checkInactivePlayers();
                } catch (InterruptedException e) {
                    System.err.println("[Server] player checker destroyed...");
                    break;
                }
            }
        });

        serverListener.start();
        announcementThread.start();
        gameLoop.start();
        playerChecker.start();
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
        if(playerChecker != null) playerChecker.interrupt();
    }

    private void checkInactivePlayers() {
        long currentTime = System.currentTimeMillis();

        // Повторная отправка сообщений
        sentMessages.entrySet().removeIf(entry -> {
            SentMessageInfo info = entry.getValue();
            boolean shouldResend = currentTime - info.getTimestamp() > ACK_TIMEOUT;
            if (shouldResend) {
                try {
                    sendGameMessage(info.getMessage(), info.getAddress(), info.getPort());
                } catch (IOException e) {
                    System.err.println("Error resending message: " + e.getMessage());
                }
            }
            return !players.containsKey(info.getMessage().getReceiverId()) || shouldResend;
        });
    }

    private void removePlayer(int playerId) {
        players.remove(playerId);
        addressToPlayerId.values().removeIf(id -> id == playerId);
        // Удалить все сообщения, отправленные этому игроку
        sentMessages.entrySet().removeIf(entry -> entry.getValue().getMessage().getReceiverId() == playerId);
    }

    public void receiveMessage() throws IOException {
        byte[] buf = new byte[4096];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);

        byte[] trimmedData = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());

        InetAddress address = packet.getAddress();
        int port = packet.getPort();

        GameMessage message = GameMessage.parseFrom(trimmedData);

        if (message.getTypeCase() != GameMessage.TypeCase.ACK) {
            System.err.println("[Server] listened " + message.getTypeCase() + " from " + address + ":" + port);
            if (message.getTypeCase() != GameMessage.TypeCase.JOIN) {
                int playerId = message.getSenderId();
                long playerMsgSeq = message.getMsgSeq();
                if (lastMsgSeqReceived.getOrDefault(playerId, -1L) >= playerMsgSeq) {
                    return; // Игнорируем устаревшее или дублированное сообщение
                }
                lastMsgSeqReceived.put(playerId, playerMsgSeq);
            }
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
                System.err.println("[Server] Unknown message type (" + message.getTypeCase() + ") from " + address.toString() + port);
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
                .setMsgSeq(msgSeq.get())
                .setAnnouncement(GameMessage.AnnouncementMsg.newBuilder()
                        .addGames(GameAnnouncement.newBuilder()
                                .setPlayers(GamePlayers.newBuilder()
                                        .addAllPlayers(players.values())
                                        .build())
                                .setConfig(GameConfig.newBuilder()
                                        .setWidth(field.getWidth())
                                        .setHeight(field.getHeight())
                                        .setFoodStatic(field.getFoodCoefficientB())
                                        .setStateDelayMs(field.getDelayMS())
                                        .build())
                                .setCanJoin(players.size() < maxPlayerCount)
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
            System.err.println("[Server] Unknown player from " + address + ":" + port);
        }
    }

    private void handleJoin(GameMessage message, InetAddress address, int port) throws IOException {
        GameMessage.JoinMsg join = message.getJoin();
        if (canPlayerJoin()) {
            int playerId = addNewPlayer(join.getPlayerName(), address, port, join.getRequestedRole());
            ArrayList<GameState.Coord> initialPosition = snakeGame.getGameField().findValidSnakePosition();
            Snake newSnake = new Snake(initialPosition, playerId);
            snakeGame.addSnake(newSnake);
        } else {
            System.err.println("[Server] Player " + join.getPlayerName() + " cannot join the game");
            sendError("Cannot join the game: no space", address, port);
        }
    }

    public void sendStateForAll() throws IOException {
        GameMessage stateMessage = createStateMessage();
        for (GamePlayer player : new ArrayList<>(players.values())) {
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
            gameStateBuilder.addSnakes(Snake.generateSnakeProto(snake, snakeGame.getGameField().getHeight(), snakeGame.getGameField().getWidth()));
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
        sentMessages.remove(message.getMsgSeq());
        int playerId = getPlayerIdByAddress(address, port);
        if (playerId != -1) {
            lastAckTime.put(playerId, System.currentTimeMillis());
        }
    }

    private void handleError(GameMessage message, InetAddress address, int port) {
        // Обработка сообщения об ошибке.
        GameMessage.ErrorMsg error = message.getError();
        System.err.println("[Server] Error: " + error.getErrorMessage() + " from " + address.toString() + port);
    }

    private void handleRoleChange(GameMessage message, InetAddress address, int port) {

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
        GameMessage updatedMessage = GameMessage.newBuilder(gameMessage)
                .setSenderId(this.senderId)
                .setReceiverId(addressToPlayerId.getOrDefault(new InetSocketAddress(address, port), -1))
                .build();
        byte[] buffer = updatedMessage.toByteArray();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        if (updatedMessage.getTypeCase() != GameMessage.TypeCase.ACK) {
            System.err.println("[Server] Send message " + updatedMessage.getTypeCase() + " to " + address + ":" + port);
        }
        if (updatedMessage.getTypeCase() == GameMessage.TypeCase.ANNOUNCEMENT) {
            multicastSocket.send(packet);
        } else {
            socket.send(packet);
            sentMessages.put(updatedMessage.getMsgSeq(), new SentMessageInfo(updatedMessage, address, port, System.currentTimeMillis()));
        }
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

    public String getAddress() {
        return serverAddress.getHostAddress();
    }
}