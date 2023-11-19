package ru.nsu.UI;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class PlayerInfo {
    private StringProperty place;
    private StringProperty playerName;
    private IntegerProperty score;

    public PlayerInfo(String place, String playerName, int score) {
        this.place = new SimpleStringProperty(place);
        this.playerName = new SimpleStringProperty(playerName);
        this.score = new SimpleIntegerProperty(score);
    }

    public StringProperty placeProperty() {
        return place;
    }

    public StringProperty playerNameProperty() {
        return playerName;
    }

    public IntegerProperty scoreProperty() {
        return score;
    }
}

