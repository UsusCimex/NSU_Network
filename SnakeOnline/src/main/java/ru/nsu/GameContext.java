package ru.nsu;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

public class GameContext {
    private final int areaWidth;
    private final int areaHeight;
    private HashMap<String, Snake> snakes;
    private List<SnakeGame.Coord> apples;

    private boolean gameStatus = false;

    public GameContext(int areaWidth, int areaHeight) {
        this.areaWidth = areaWidth;
        this.areaHeight = areaHeight;

        gameStatus = true;
    }
    public int getAreaWidth() {
        return areaWidth;
    }

    public int getAreaHeight() {
        return areaHeight;
    }
    public HashMap<String, Snake> getSnakes() {
        return snakes;
    }

    public List<SnakeGame.Coord> getApples() {
        return apples;
    }
    public boolean isGameStatus() {
        return gameStatus;
    }
}
