package ru.nsu.patterns;
import java.net.InetAddress;

public interface Observer {
    void update (Object message, InetAddress address, int port);
}
