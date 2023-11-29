package ru.nsu.UI;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ServerInfo {
    private final StringProperty serverName;
    private final IntegerProperty online;
    private final StringProperty areaSize;
    private final IntegerProperty foodCoefficientA;
    private final IntegerProperty foodCoefficientB;
    private final StringProperty serverIP;
    private final IntegerProperty serverPort;
    private final IntegerProperty stateDelayMs;

    public ServerInfo(String serverName, int online, String areaSize, int foodCoefficientA, int foodCoefficientB, int gameSpeed, String serverIP, int serverPort) {
        this.serverName = new SimpleStringProperty(serverName);
        this.online = new SimpleIntegerProperty(online);
        this.areaSize = new SimpleStringProperty(areaSize);
        this.foodCoefficientA = new SimpleIntegerProperty(foodCoefficientA);
        this.foodCoefficientB = new SimpleIntegerProperty(foodCoefficientB);
        this.stateDelayMs = new SimpleIntegerProperty(gameSpeed);
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

    public IntegerProperty foodCoefficientAProperty() {
        return foodCoefficientA;
    }
    public IntegerProperty foodCoefficientBProperty() {
        return foodCoefficientB;
    }
    public IntegerProperty stateDelayMsProperty() { return stateDelayMs; }
}

