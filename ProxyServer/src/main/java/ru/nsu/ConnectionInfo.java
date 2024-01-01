package ru.nsu;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ConnectionInfo {
    private final SocketChannel clientChannel;
    private SocketChannel remoteChannel;
    private int destinationPort;
    private final ByteBuffer readBuffer;
    private final ByteBuffer writeBuffer;
    private boolean isFinished = false;

    enum State {
        INITIAL,
        SOCKS_AUTHORIZATION,
        SOCKS_CONNECTION,
        DATA_TRANSFER
    }

    private State state;

    public ConnectionInfo(SocketChannel clientChannel) {
        this.clientChannel = clientChannel;
        this.state = State.INITIAL;
        this.readBuffer = ByteBuffer.allocate(48496);
        this.writeBuffer = ByteBuffer.allocate(48496);
    }

    public ConnectionInfo(ConnectionInfo connectionInfo) {
        // swap client and remove channels
        this.clientChannel = connectionInfo.getRemoteChannel();
        this.remoteChannel = connectionInfo.getClientChannel();
        this.state = connectionInfo.getState();
        this.readBuffer = ByteBuffer.allocate(48496);
        this.writeBuffer = ByteBuffer.allocate(48496);
    }

    public SocketChannel getClientChannel() {
        return clientChannel;
    }

    public SocketChannel getRemoteChannel() {
        return remoteChannel;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }
    public void setRemoteChannel(SocketChannel remoteChannel) {
        this.remoteChannel = remoteChannel;
    }

    public int getDestinationPort() {
        return this.destinationPort;
    }
    public void setDestinationPort(int port) {
        this.destinationPort = port;
    }
    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }
    public ByteBuffer getWriteBuffer() {
        return writeBuffer;
    }
    public boolean isFinished() {
        return isFinished;
    }

    public void setFinished(boolean finished) {
        isFinished = finished;
    }
}
