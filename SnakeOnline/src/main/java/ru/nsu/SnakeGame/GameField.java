package ru.nsu.SnakeGame;

import ru.nsu.SnakesProto.*;

import java.util.ArrayList;
import java.util.List;

public class GameField {
    private int width;
    private int height;
    private List<Snake> snakes;
    private List<GameState.Coord> foods;

    public GameField(int width, int height) {
        this.width = width;
        this.height = height;
        this.snakes = new ArrayList<>();
        this.foods = new ArrayList<>();
    }
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public List<Snake> getSnakes() {
        return snakes;
    }
    public List<GameState.Coord> getFoods() {
        return foods;
    }
    public void addSnake(Snake snake) {
        snakes.add(snake);
    }
    public void addFood(GameState.Coord food) {
        foods.add(food);
    }
    public void removeFood(GameState.Coord food) {
        foods.remove(food);
    }
}
