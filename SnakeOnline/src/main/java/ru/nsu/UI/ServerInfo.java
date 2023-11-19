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

    public ServerInfo(String serverName, int online, String areaSize, int food) {
        this.serverName = new SimpleStringProperty(serverName);
        this.online = new SimpleIntegerProperty(online);
        this.areaSize = new SimpleStringProperty(areaSize);
        this.food = new SimpleIntegerProperty(food);
    }

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

