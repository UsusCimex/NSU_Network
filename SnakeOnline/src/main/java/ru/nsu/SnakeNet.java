package ru.nsu;

import ru.nsu.SnakeGame.GameField;
import ru.nsu.SnakeGame.GameLogic;
import ru.nsu.SnakeGame.Snake;
import ru.nsu.SnakesProto.*;
import ru.nsu.UI.ServerInfo;
import ru.nsu.patterns.Observer;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SnakeNet {
//    public static final String MULTICAST_ADDRESS = "224.0.0.1";
//    public static final int MULTICAST_PORT = 8888;
    public static final String MULTICAST_ADDRESS = "239.192.0.4";
    public static final int MULTICAST_PORT = 9192;
    private static final int MAX_ATTEMPTS = 5;

    private final Observer observer;

    private final ConcurrentHashMap<Integer, Long> lastMsgSeqReceived = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<Long, SentMessageInfo>> sentMessages = new ConcurrentHashMap<>();

    private final long announcementDelayMS = 1000;
    private final long pingDelayMS = 100;
    private final long resendTime;

    private GameField gameField;
    private GameMessage.StateMsg lastStateMsg;

    private int playerId = -1;
    private int deputyId = -1;
    private int masterId = -1;

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
    private String playerName;

    private Thread messageReceiverLoop;
    private Thread announcementSendThread;
    private Thread gameLoop;
    private Thread messageResenderThread;
    private Thread pingSender;

    private boolean isServer;
    private NodeRole nodeRole;

    public SnakeNet(ServerInfo serverInfo, Observer observer) throws IOException {
        this.serverInfo = serverInfo;
        this.observer = observer;

        this.resendTime = serverInfo.stateDelayMsProperty().get() / 10;
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
                    for (ConcurrentHashMap<Long, SentMessageInfo> playerMessages : sentMessages.values()) {
                        for (SentMessageInfo message : playerMessages.values()) {
                            boolean shouldResend = (currentTime - message.getTimestamp()) > resendTime;
                            if (shouldResend) {
                                message.setAttemptCount(message.getAttemptCount() + 1);
                                if (message.getAttemptCount() >= MAX_ATTEMPTS) {
                                    System.err.println("Player " + message.getMessage().getReceiverId() + " doesn't answer...");
                                    handlePlayerDisconnection(message.getMessage().getReceiverId());
                                    break;
                                }
                                sendGameMessage(message.getMessage(), message.getAddress(), message.getPort());
                            }
                        }
                    }
                    Thread.sleep(resendTime);
                } catch (InterruptedException e) {
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
        this.playerName = playerName;
        this.nodeRole = NodeRole.NORMAL;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.socket = new DatagramSocket();

        sendJoinRequest(playerName, serverAddress, serverPort);

        pingSender = new Thread(() -> {
            while (!pingSender.isInterrupted()) {
                try {
                    Thread.sleep(pingDelayMS);
                    if (masterId == -1) continue;
                    if (sentMessages.get(masterId).isEmpty()) {
                        GameMessage pingMsg = createPingMessage();
                        sendGameMessage(pingMsg, serverAddress, serverPort);
                    }
                } catch (InterruptedException e) {
                    System.err.println("Ping sender error...");
                    break;
                }
            }
        });
        pingSender.start();
    }

    // Инициализация в качестве сервера
    public void startAsServer(String playerName, InetAddress address, int port) throws SocketException {
        this.isServer = true;
        this.playerName = playerName;
        this.nodeRole = NodeRole.MASTER;
        this.snakeGame = new GameLogic(gameField);
        this.serverAddress = address;
        this.serverPort = port;
        this.socket = new DatagramSocket(serverPort, serverAddress); // Привязываем к конкретному адресу

        // Добавление себя как игрока и инициализация игрового поля
        int playerId = addNewPlayer(playerName, address, port, NodeRole.MASTER);
        this.playerId = playerId;
        ArrayList<GameState.Coord> initialPosition = snakeGame.getGameField().findValidSnakePosition();
        Snake newSnake = new Snake(initialPosition, playerId);
        snakeGame.addSnake(newSnake);

        startServerThreads();
    }

    private void startServerThreads() {
        if (pingSender != null) pingSender.interrupt();

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

        announcementSendThread.start();
        gameLoop.start();
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
    private GameMessage createPingMessage() {
        return GameMessage.newBuilder()
                .setMsgSeq(msgSeq.incrementAndGet())
                .setPing(GameMessage.PingMsg.newBuilder().build())
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

    private void removePlayer(int playerId) {
        System.err.println("Attempting to remove player with ID: " + playerId);
        if (!players.containsKey(playerId)) {
            System.err.println("Player ID " + playerId + " not found in the current players list.");
            return;
        }

        players.remove(playerId);
        addressToPlayerId.values().removeIf(id -> id == playerId);
        sentMessages.get(playerId).clear();
        System.err.println("Player " + playerId + " successfully removed!");
    }


    private void receiveMessageLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            if (socket == null) continue;
            try {
                // Пример логики получения и обработки сообщений
                byte[] buffer = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] trimmedData = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());

                InetAddress address = packet.getAddress();
                int port = packet.getPort();

                GameMessage message = GameMessage.parseFrom(trimmedData);
                if (players.get(message.getSenderId()) != null) {
                    System.err.println("listened " + message.getTypeCase() + "(rec: " + message.getReceiverId() + ", sen: " + message.getSenderId() + ", seq: " + message.getMsgSeq() + ") from " + address + ":" + port + "(" + players.get(message.getSenderId()).getRole() + ")");
                } else {
                    System.err.println("listened " + message.getTypeCase() + "(rec: " + message.getReceiverId() + ", sen: " + message.getSenderId() + ", seq: " + message.getMsgSeq() + ") from " + address + ":" + port);
                }

                if (message.getTypeCase() != GameMessage.TypeCase.ANNOUNCEMENT && message.getTypeCase() != GameMessage.TypeCase.ACK && message.getTypeCase() != GameMessage.TypeCase.JOIN) {
                    sendAcknowledgement(message.getMsgSeq(), address, port);
                } else if (message.getTypeCase() == GameMessage.TypeCase.ACK) {
                    handleAck(message, address, port);
                    continue;
                }

                int playerId = message.getSenderId();
                long playerMsgSeq = message.getMsgSeq();

                if (playerMsgSeq > lastMsgSeqReceived.getOrDefault(playerId, -1L)) {
                    if (playerId != -1) lastMsgSeqReceived.put(playerId, playerMsgSeq);
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
                }

                if (nodeRole == NodeRole.MASTER && deputyId == -1) {
                    selectNewDeputy();
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
        System.err.println("Try to connect new player! " + canPlayerJoin());
        if (canPlayerJoin()) {
            int playerId = addNewPlayer(join.getPlayerName(), address, port, join.getRequestedRole());
            System.err.println("Player(" + playerId + ") can join to the game!");
            ArrayList<GameState.Coord> initialPosition = snakeGame.getGameField().findValidSnakePosition();
            Snake newSnake = new Snake(initialPosition, playerId);
            snakeGame.addSnake(newSnake);
            sendAcknowledgement(message.getMsgSeq(), address, port);
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
        lastStateMsg = stateMsg;

        // Обновление списка игроков
        stateMsg.getState().getPlayers().getPlayersList().forEach(player -> {
            try {
                if (player.getId() != -1) {
                    if (player.getRole() == NodeRole.MASTER) {
                        serverAddress = InetAddress.getByName(player.getIpAddress());
                        serverPort = player.getPort();
                        masterId = player.getId();
                    } else if (player.getRole() == NodeRole.DEPUTY) {
                        deputyId = player.getId();
                    }
                    players.put(player.getId(), player);
                    addressToPlayerId.put(new InetSocketAddress(InetAddress.getByName(player.getIpAddress()), player.getPort()), player.getId());
                    if (sentMessages.get(playerId) == null) sentMessages.put(playerId, new ConcurrentHashMap<>());
                }
            } catch (UnknownHostException e) {
                System.err.println("Error updating server address: " + e.getMessage());
            }
        });

        observer.update(stateMsg, address, port);
    }


    private void handleAck(GameMessage message, InetAddress address, int port) {
        if (this.playerId == -1) {
            this.playerId = message.getReceiverId();
            sentMessages.put(message.getSenderId(), new ConcurrentHashMap<>());
            addressToPlayerId.put(new InetSocketAddress(address, port), message.getSenderId());
        }
        sentMessages.get(message.getSenderId()).remove(message.getMsgSeq());
    }

    private void handleError(GameMessage message, InetAddress address, int port) {
        // Обработка сообщения об ошибке.
        GameMessage.ErrorMsg error = message.getError();
        System.err.println("[Server] Error: " + error.getErrorMessage() + " from " + address.toString() + port);
        handlePlayerDisconnection(getPlayerIdByAddress(address, port));
    }

    private void handleRoleChange(GameMessage message, InetAddress address, int port) throws UnknownHostException {
        GameMessage.RoleChangeMsg roleChangeMsg = message.getRoleChange();
        NodeRole newRole = roleChangeMsg.getReceiverRole();
        this.nodeRole = newRole;
        if (newRole == NodeRole.MASTER) {
            currentMaxId = players.values().stream()
                    .map(GamePlayer::getId)
                    .max(Integer::compare)
                    .get();
            GamePlayer oldPlayer = players.get(message.getReceiverId());
            GamePlayer player = GamePlayer.newBuilder(oldPlayer).setRole(NodeRole.MASTER).build();
            players.put(player.getId(), player);
            if (deputyId == player.getId()) deputyId = -1;
            GamePlayer oldMaster = players.get(masterId);
            GamePlayer master = GamePlayer.newBuilder(oldMaster).setRole(NodeRole.NORMAL).build();
            players.put(master.getId(), master);
            masterId = player.getId();

            this.serverAddress = InetAddress.getByName(player.getIpAddress());
            this.serverPort = player.getPort();

            GameLogic.editGameFieldFromState(gameField, lastStateMsg);
            snakeGame = new GameLogic(gameField);
            startServerThreads();
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
    public void sendRoleChange(NodeRole newRole, int receiverId) throws IOException {
        GameMessage roleChangeMessage = GameMessage.newBuilder()
                .setMsgSeq(msgSeq.incrementAndGet())
                .setRoleChange(GameMessage.RoleChangeMsg.newBuilder()
                        .setReceiverRole(newRole)
                        .build())
                .build();

        GamePlayer receiver = players.get(receiverId);
        if (receiver != null) {
            sendGameMessage(roleChangeMessage, InetAddress.getByName(receiver.getIpAddress()), receiver.getPort());
        }
    }

    private void selectNewDeputy() {
        for (Map.Entry<Integer, GamePlayer> entry : players.entrySet()) {
            GamePlayer player = entry.getValue();
            if (player.getRole() == NodeRole.NORMAL) {
                // Предположим, что первый NORMAL узел становится новым DEPUTY или MASTER
                try {
                    deputyId = player.getId();
                    players.put(deputyId, GamePlayer.newBuilder(players.get(deputyId)).setRole(NodeRole.DEPUTY).build());
                    sendRoleChange(NodeRole.DEPUTY, player.getId());
                    break;
                } catch (IOException e) {
                    System.err.println("Error sending RoleChangeMsg: " + e.getMessage());
                }
            }
        }
    }
    private void handlePlayerDisconnection(int disconnectedPlayerId) {
        System.err.println("Handling disconnection for player with ID: " + disconnectedPlayerId);
        if (disconnectedPlayerId == -1) {
            System.err.println("Invalid player ID for disconnection: " + disconnectedPlayerId);
            return;
        }

        if (disconnectedPlayerId == masterId) {
            GamePlayer oldPlayer = players.get(playerId);
            GamePlayer player = GamePlayer.newBuilder(oldPlayer).setRole(NodeRole.MASTER).build();
            players.put(player.getId(), player);
            if (deputyId == player.getId()) deputyId = -1;
            GamePlayer oldMaster = players.get(masterId);
            GamePlayer master = GamePlayer.newBuilder(oldMaster).setRole(NodeRole.NORMAL).build();
            players.put(master.getId(), master);
            masterId = player.getId();
            try {
                this.serverAddress = InetAddress.getByName(player.getIpAddress());
            } catch (UnknownHostException e) {
                System.err.println("UnknownHostException...");
            }
            this.serverPort = player.getPort();
        } else if (disconnectedPlayerId == deputyId) {
            deputyId = -1;
        }

        if (nodeRole == NodeRole.DEPUTY && masterId == playerId) {
            System.err.println("Taking over as Master after Master disconnection.");
            GameLogic.editGameFieldFromState(gameField, lastStateMsg);
            nodeRole = NodeRole.MASTER;
            snakeGame = new GameLogic(gameField);
            currentMaxId = players.values().stream()
                    .map(GamePlayer::getId)
                    .max(Integer::compare)
                    .get();
            startServerThreads();
        }

        if (players.containsKey(disconnectedPlayerId)) {
            removePlayer(disconnectedPlayerId);
        }
        System.err.println("Player disconnection process completed for player ID: " + disconnectedPlayerId);

        if (deputyId == -1) {
            selectNewDeputy();
            System.err.println("New Deputy selected after disconnection.");
        }
    }

    private void sendGameMessage(GameMessage gameMessage, InetAddress address, int port) {
        try {
            GameMessage message = GameMessage.newBuilder(gameMessage)
                    .setSenderId(this.playerId)
                    .setReceiverId(addressToPlayerId.getOrDefault(new InetSocketAddress(address, port), -1))
                    .build();
            byte[] buffer = message.toByteArray();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
            if (players.get(message.getReceiverId()) != null) {
                System.err.println("Send message " + message.getTypeCase() + "(rec: " + message.getReceiverId() + ", sen: " + message.getSenderId() + ", seq: " + message.getMsgSeq() + ") to " + address + ":" + port + "(" + players.get(message.getReceiverId()).getRole() + ")");
            } else {
                System.err.println("Send message " + message.getTypeCase() + "(rec: " + message.getReceiverId() + ", sen: " + message.getSenderId() + ", seq: " + message.getMsgSeq() + ") to " + address + ":" + port);
            }
            if (message.getTypeCase() == GameMessage.TypeCase.ANNOUNCEMENT) {
                multicastSocket.send(packet);
            } else {
                socket.send(packet);
                if (message.getReceiverId() == -1 || message.getTypeCase() == GameMessage.TypeCase.ACK) return;
                if (sentMessages.get(message.getReceiverId()).get(message.getMsgSeq()) == null)
                    sentMessages.get(message.getReceiverId()).put(message.getMsgSeq(), new SentMessageInfo(message, address, port, System.currentTimeMillis()));
            }
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
            int playerId = getPlayerIdByAddress(address, port);
            handlePlayerDisconnection(playerId);
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
        if (requestedRole == NodeRole.MASTER && this.nodeRole != NodeRole.MASTER) {
            return -1;
        }
        if (requestedRole == NodeRole.DEPUTY) {
            if (deputyId == -1) {
                deputyId = playerId;
            } else {
                return -1;
            }
        }
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
        sentMessages.put(playerId, new ConcurrentHashMap<>());
        return playerId;
    }

    public String getAddress() {
        return serverAddress.getHostAddress();
    }

    public boolean isServer() {
        return isServer;
    }

    public void stop() {
        if (isServer) {
            try {
                if (deputyId != -1) {
                    sendRoleChange(NodeRole.MASTER, deputyId);
                }
            } catch(IOException e) {
                System.err.println("Error to send role change!");
                e.printStackTrace();
            }
        }

        if (socket != null) {
            socket.close();
        }
        if (multicastSocket != null) {
            multicastSocket.close();
        }

        if (messageReceiverLoop != null) messageReceiverLoop.interrupt();
        if (announcementSendThread != null) announcementSendThread.interrupt();
        if (gameLoop != null) gameLoop.interrupt();
        if (messageResenderThread != null) messageResenderThread.interrupt();
        if (pingSender != null) pingSender.interrupt();
    }
}
