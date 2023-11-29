package ru.nsu;

import ru.nsu.SnakeGame.GameField;
import ru.nsu.SnakeGame.GameLogic;
import ru.nsu.SnakeGame.Snake;
import ru.nsu.SnakesProto.*;
import ru.nsu.UI.ServerInfo;
import ru.nsu.patterns.Observer;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SnakeNet {
    public static final String MULTICAST_ADDRESS = "224.0.0.1";
    public static final int MULTICAST_PORT = 8888;

    private final Observer observer;

    private final ConcurrentHashMap<Integer, Long> lastAckTime = new ConcurrentHashMap<>(); // Для отслеживания времени последнего Ack
    private final ConcurrentHashMap<Integer, Long> lastMsgSeqReceived = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, SentMessageInfo> sentMessages = new ConcurrentHashMap<>();
    private static final long ACK_TIMEOUT = 5000; // Таймаут в миллисекундах

    private final long announcementDelayMS = 1000;

    private GameField gameField;

    private int senderId = 1;

    private InetAddress serverAddress;
    private int serverPort;

    private final AtomicLong msgSeq = new AtomicLong(0);
    private int stateOrder = 0;
    private GameLogic snakeGame = null;
    private final ConcurrentHashMap<Integer, GamePlayer> players = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<InetSocketAddress, Integer> addressToPlayerId = new ConcurrentHashMap<>();
    private int currentMaxId = 0;
    private int playerCount = 0;
    private int maxPlayerCount = 5;
    private DatagramSocket socket;
    private final MulticastSocket multicastSocket;

    private ServerInfo serverInfo;

    private Thread messageReceiverLoop;
    private Thread announcementSendThread;
    private Thread playerChecker;
    private Thread gameLoop;
    private Thread messageResenderThread;

    private boolean isServer;
    private NodeRole nodeRole;

    public SnakeNet(ServerInfo serverInfo, Observer observer) throws IOException {
        this.serverInfo = serverInfo;
        this.observer = observer;

        this.gameField = new GameField(serverInfo);

        String serverAddress = serverInfo.serverIPProperty().get();
        System.err.println("[SnakeNet] Address: " + serverAddress);

        multicastSocket = new MulticastSocket(MULTICAST_PORT);
        NetworkInterface networkInterface = Controller.findNetworkInterface("Wi-Fi");
        if (networkInterface == null) {
            System.err.println("Failed to find a suitable network interface for multicast");
            return;
        }
        multicastSocket.setNetworkInterface(networkInterface);

        messageReceiverLoop = new Thread(this::receiveMessageLoop);
        messageResenderThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    long currentTime = System.currentTimeMillis();
                    for (Map.Entry<Long, SentMessageInfo> entry : sentMessages.entrySet()) {
                        SentMessageInfo info = entry.getValue();
                        if (currentTime - info.getTimestamp() > ACK_TIMEOUT) {
                            sendGameMessage(info.getMessage(), info.getAddress(), info.getPort());
                        }
                    }
                    Thread.sleep(500); // Проверка каждые 500 мс
                } catch (InterruptedException | IOException e) {
                    System.err.println("Resend message error!");
                    break;
                }
            }
        });

        messageReceiverLoop.start();
        messageResenderThread.start();
    }

    // Инициализация в качестве клиента
    public void startAsClient(String playerName, InetAddress serverAddress, int serverPort) throws IOException {
        this.isServer = false;
        this.nodeRole = NodeRole.NORMAL;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.socket = new DatagramSocket(); // Привязываем к конкретному адресу

        sendJoinRequest(playerName, serverAddress, serverPort);
    }

    // Инициализация в качестве сервера
    public void startAsServer(String playerName, InetAddress address, int port) throws SocketException {
        this.isServer = true;
        this.nodeRole = NodeRole.MASTER;
        this.snakeGame = new GameLogic(gameField);
        this.serverAddress = address;
        this.serverPort = port;
        this.socket = new DatagramSocket(serverPort, serverAddress); // Привязываем к конкретному адресу

        // Добавление себя как игрока и инициализация игрового поля
        int playerId = addNewPlayer(playerName, address, port, NodeRole.MASTER);
        ArrayList<GameState.Coord> initialPosition = snakeGame.getGameField().findValidSnakePosition();
        Snake newSnake = new Snake(initialPosition, playerId);
        snakeGame.addSnake(newSnake);

        announcementSendThread = new Thread(() -> {
            while (!announcementSendThread.isInterrupted()) {
                try {
                    sendAnnouncement(InetAddress.getByName(MULTICAST_ADDRESS), MULTICAST_PORT);
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

        announcementSendThread.start();
        gameLoop.start();
        playerChecker.start();
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
                                .setGameName(serverInfo.serverNameProperty().get())
                                .build())
                        .build())
                .build();
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

    private void receiveMessageLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            if (socket == null) continue;
            try {
                // Пример логики получения и обработки сообщений
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] trimmedData = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());

                InetAddress address = packet.getAddress();
                int port = packet.getPort();

                GameMessage message = GameMessage.parseFrom(trimmedData);

                if (message.getTypeCase() != GameMessage.TypeCase.ACK) {
                    System.err.println("listened " + message.getTypeCase() + " from " + address + ":" + port);
                    if (message.getTypeCase() != GameMessage.TypeCase.JOIN) {
                        int playerId = message.getSenderId();
                        long playerMsgSeq = message.getMsgSeq();
                        if (lastMsgSeqReceived.getOrDefault(playerId, -1L) >= playerMsgSeq) {
//                            return; // Игнорируем устаревшее или дублированное сообщение
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
                        System.err.println("Unknown message type (" + message.getTypeCase() + ") from " + address.toString() + port);
                        sendError("Unknown message type", address, port);
                        return;
                    }
                }

                if (message.getTypeCase() != GameMessage.TypeCase.ANNOUNCEMENT && message.getTypeCase() != GameMessage.TypeCase.ACK) {
                    sendAcknowledgement(message.getMsgSeq(), address, port);
                }
            } catch (IOException e) {
                System.err.println("Message receive error: " + e.getMessage());
                break;
            }
        }
    }

    public void sendJoinRequest(String playerName, InetAddress address, int port) throws IOException {
        GameMessage joinMessage = GameMessage.newBuilder()
                .setMsgSeq(msgSeq.incrementAndGet())
                .setJoin(GameMessage.JoinMsg.newBuilder()
                        .setPlayerName(playerName)
                        .setGameName(serverInfo.serverNameProperty().get())
                        .setRequestedRole(nodeRole)
                        .build())
                .build();

        sendGameMessage(joinMessage, address, port);
    }

    private void handlePing(GameMessage message, InetAddress address, int port) {
        // Обработка Ping сообщения, например, обновление времени последнего активного взаимодействия с этим клиентом
        lastAckTime.put(message.getSenderId(), System.currentTimeMillis());
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

    public void sendSteer(Direction direction) throws IOException {
        GameMessage steerMessage = GameMessage.newBuilder()
                .setMsgSeq(msgSeq.incrementAndGet())
                .setSteer(GameMessage.SteerMsg.newBuilder()
                        .setDirection(direction)
                        .build())
                .build();

        sendGameMessage(steerMessage, serverAddress, serverPort);
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
        GameMessage.StateMsg stateMsg = message.getState();
        GameLogic.editGameFieldFromState(gameField, stateMsg);
        observer.update(stateMsg, address, port);
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
        //write logic here...
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
    public void sendRoleChange(NodeRole newRole, int receiverId) throws IOException {
        GameMessage roleChangeMessage = GameMessage.newBuilder()
                .setMsgSeq(msgSeq.incrementAndGet())
                .setRoleChange(GameMessage.RoleChangeMsg.newBuilder()
                        .setReceiverRole(newRole)
                        .build())
                .setSenderId(this.senderId)
                .setReceiverId(receiverId)
                .build();

        // Отправка сообщения о смене роли
        GamePlayer receiver = players.get(receiverId);
        if (receiver != null) {
            sendGameMessage(roleChangeMessage, InetAddress.getByName(receiver.getIpAddress()), receiver.getPort());
        }
    }
    private void sendGameMessage(GameMessage gameMessage, InetAddress address, int port) throws IOException {
        GameMessage updatedMessage = GameMessage.newBuilder(gameMessage)
                .setSenderId(this.senderId)
                .setReceiverId(addressToPlayerId.getOrDefault(new InetSocketAddress(address, port), -1))
                .build();
        byte[] buffer = updatedMessage.toByteArray();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        if (updatedMessage.getTypeCase() != GameMessage.TypeCase.ACK) {
            System.err.println("Send message " + updatedMessage.getTypeCase() + " to " + address + ":" + port);
        }
        if (updatedMessage.getTypeCase() == GameMessage.TypeCase.ANNOUNCEMENT) {
            multicastSocket.send(packet);
        } else {
            socket.send(packet);
            sentMessages.put(updatedMessage.getMsgSeq(), new SentMessageInfo(updatedMessage, address, port, System.currentTimeMillis()));
        }
    }
    // Поиск ID игрока по адресу и порту
    private int getPlayerIdByAddress(InetAddress address, int port) {
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

    public boolean isServer() {
        return isServer;
    }

    public void stop() {
        if (socket != null) {
            socket.close();
        }
        if (multicastSocket != null) {
            multicastSocket.close();
        }

        if (messageReceiverLoop != null) messageReceiverLoop.interrupt();
        if (announcementSendThread != null) announcementSendThread.interrupt();
        if (playerChecker != null) playerChecker.interrupt();
        if (gameLoop != null) gameLoop.interrupt();
        if (messageResenderThread != null) messageResenderThread.interrupt();
    }
}
