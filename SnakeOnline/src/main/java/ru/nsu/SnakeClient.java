package ru.nsu;

import ru.nsu.SnakeGame.GameField;
import ru.nsu.SnakeGame.GameLogic;
import ru.nsu.SnakesProto.*;
import ru.nsu.UI.ServerInfo;
import ru.nsu.patterns.Observer;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SnakeClient {
    private final int ACK_TIMEOUT = 50;
    private final Observer observer;
    private final AtomicLong msgSeq = new AtomicLong(0);
    private final DatagramSocket socket;
    private final InetAddress serverAddress;
    private final int serverPort;
    private final String gameName;
    private int serverId = -1;
    private int clientId = -1;
    private final GameField gameField;
    private final NodeRole nodeRole = NodeRole.NORMAL;

    private final ConcurrentHashMap<Integer, Long> lastMsgSeqReceived = new ConcurrentHashMap<>(); // Для отслеживания последнего msg_seq
    private final ConcurrentHashMap<Long, SentMessageInfo> sentMessages = new ConcurrentHashMap<>();

    private Thread clientThread;
    private Thread messageResendThread;

    public SnakeClient(ServerInfo serverInfo, Observer observer, MulticastSocket multicastSocket) throws IOException {
        this.gameName = serverInfo.serverNameProperty().get();
        this.serverAddress = InetAddress.getByName(serverInfo.serverIPProperty().get());
        this.socket = new DatagramSocket();
        this.serverPort = serverInfo.serverPortProperty().get();
        this.observer = observer;

        String[] numbers = serverInfo.areaSizeProperty().get().split("[^0-9]+");
        int width = Integer.parseInt(numbers[0]);
        int height = Integer.parseInt(numbers[1]);
        this.gameField = new GameField(width, height, 0, serverInfo.foodProperty().get(), serverInfo.stateDelayMsProperty().get());

        // Находим подходящий сетевой интерфейс
        NetworkInterface networkInterface = Controller.findNetworkInterface("Wi-Fi");
        if (networkInterface == null) {
            System.err.println("Failed to find a suitable network interface for multicast");
            return;
        }
        multicastSocket.setNetworkInterface(networkInterface);
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

        messageResendThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    long currentTime = System.currentTimeMillis();
                    for (Map.Entry<Long, SentMessageInfo> entry : sentMessages.entrySet()) {
                        SentMessageInfo info = entry.getValue();
                        if (currentTime - info.getTimestamp() > ACK_TIMEOUT) {
                            try {
                                sendGameMessage(info.getMessage(), info.getAddress(), info.getPort());
                            } catch (IOException e) {
                                System.err.println("Error resending message: " + e.getMessage());
                            }
                        }
                    }
                    Thread.sleep(1000); // Проверка каждую секунду
                } catch (InterruptedException e) {
                    System.err.println("[Client] Resend message error!");
                    break;
                }
            }
        });

        clientThread.start();
        messageResendThread.start();
    }

    public void stop() {
        if (socket != null) socket.close();

        if (clientThread != null) clientThread.interrupt();
        if (messageResendThread != null) messageResendThread.interrupt();
    }

    public void sendJoinRequest(String playerName) throws IOException {
        GameMessage joinMessage = GameMessage.newBuilder()
                .setMsgSeq(msgSeq.incrementAndGet())
                .setJoin(GameMessage.JoinMsg.newBuilder()
                        .setPlayerName(playerName)
                        .setGameName(gameName)
                        .setRequestedRole(nodeRole)
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
        byte[] buffer = new byte[4096];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);

        byte[] trimmedData = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());

        InetAddress address = packet.getAddress();
        int port = packet.getPort();

        GameMessage message = GameMessage.parseFrom(trimmedData);

        if (serverId == -1) {
            serverId = message.getSenderId();
        }
        if (clientId == -1) {
            clientId = message.getReceiverId();
        }

        if (message.getTypeCase() != GameMessage.TypeCase.ACK) {
            System.err.println("[Client] listened " + message.getTypeCase() + " from " + address + ":" + port);

            int playerId = message.getSenderId();
            long playerMsgSeq = message.getMsgSeq();
            if (lastMsgSeqReceived.getOrDefault(playerId, -1L) >= playerMsgSeq) {
                return; // Игнорируем устаревшее или дублированное сообщение
            }
            lastMsgSeqReceived.put(playerId, playerMsgSeq);
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
                System.err.println("Unknown message type (" + message.getTypeCase() + ") from " + serverAddress.toString() + ":" + serverPort);
                sendError("Unknown message type", address, port);
                return;
            }
        }

        if (message.getTypeCase() != GameMessage.TypeCase.ANNOUNCEMENT && message.getTypeCase() != GameMessage.TypeCase.ACK) {
            sendAcknowledgement(message.getMsgSeq(), address, port);
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
        GameLogic.editGameFieldFromState(gameField, stateMsg);
        observer.update(stateMsg, address, port);
    }

    private void handleAck(GameMessage message, InetAddress address, int port) {
        sentMessages.remove(message.getMsgSeq());
    }

    private void handleError(GameMessage message, InetAddress address, int port) {
        // Обработка сообщения об ошибке.
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
                .setSenderId(clientId)
                .setReceiverId(serverId)
                .build();
        byte[] buffer = updatedMessage.toByteArray();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        if (updatedMessage.getTypeCase() != GameMessage.TypeCase.ACK) {
            System.err.println("[Client] Send message " + updatedMessage.getTypeCase() + " to " + address + ":" + port);
            sentMessages.put(updatedMessage.getMsgSeq(), new SentMessageInfo(updatedMessage, address, port, System.currentTimeMillis()));
        }
        socket.send(packet);
    }
}
