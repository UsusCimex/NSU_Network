package ru.nsu;

import ru.nsu.SnakesProto.*;
import java.net.InetAddress;

public class SentMessageInfo {
    private final GameMessage message;
    private final InetAddress address;
    private final int port;
    private final long timestamp;

    SentMessageInfo(GameMessage message, InetAddress address, int port, long timestamp) {
        this.message = message;
        this.address = address;
        this.port = port;
        this.timestamp = timestamp;
    }

    public GameMessage getMessage() {
        return message;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
