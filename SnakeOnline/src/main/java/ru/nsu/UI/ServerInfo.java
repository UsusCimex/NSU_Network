package ru.nsu.UI;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ServerInfo {
    private StringProperty serverName;
    private IntegerProperty online;
    private StringProperty areaSize;
    private IntegerProperty food;
    private StringProperty serverIP;
    private IntegerProperty serverPort;

    public ServerInfo(String serverName, int online, String areaSize, int food, String serverIP, int serverPort) {
        this.serverName = new SimpleStringProperty(serverName);
        this.online = new SimpleIntegerProperty(online);
        this.areaSize = new SimpleStringProperty(areaSize);
        this.food = new SimpleIntegerProperty(food);
        this.serverIP = new SimpleStringProperty(serverIP);
        this.serverPort = new SimpleIntegerProperty(serverPort);
    }

    public StringProperty serverIPProperty() {return serverIP;}
    public IntegerProperty serverPortProperty() {return serverPort;}

    public StringProperty serverNameProperty() {
        return serverName;
    }

    public IntegerProperty onlineProperty() {
        return online;
    }

    public StringProperty areaSizeProperty() {
        return areaSize;
    }

    public IntegerProperty foodProperty() {
        return food;
    }
}

