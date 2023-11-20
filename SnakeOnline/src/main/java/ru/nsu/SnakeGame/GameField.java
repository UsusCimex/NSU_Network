package ru.nsu.SnakeGame;

import ru.nsu.SnakesProto.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameField {
    private final int width;
    private final int height;
    private final int foodCoefficientA;
    private final int foodCoefficientB;
    private List<Snake> snakes;
    private List<GameState.Coord> foods;

    public GameField(int width, int height, int foodCoefficientA, int foodCoefficientB) {
        this.width = width;
        this.height = height;
        this.foodCoefficientA = foodCoefficientA;
        this.foodCoefficientB = foodCoefficientB;
        this.snakes = new ArrayList<>();
        this.foods = new ArrayList<>();
    }

    public GameState.Coord findValidSnakePosition() {
        Random random = new Random();
        int maxAttempts = 1000;
        int minSnakeSize = 3;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);

            GameState.Coord potentialPosition = GameState.Coord.newBuilder()
                    .setX(x)
                    .setY(y)
                    .build();

            if (isPositionValidForSnake(potentialPosition, minSnakeSize)) {
                return potentialPosition;
            }
        }

        throw new RuntimeException("Unable to find a valid position for the new snake");
    }

    private boolean isPositionValidForSnake(GameState.Coord position, int minSize) {
        for (int i = 0; i < minSize; i++) {
            GameState.Coord cell = GameState.Coord.newBuilder()
                    .setX(position.getX() + i)
                    .setY(position.getY())
                    .build();

            if (isCellOccupied(cell)) {
                return false;
            }
        }
        return true;
    }

    public boolean isCellOccupied(GameState.Coord cell) {
        for (Snake snake : snakes) {
            if (snake.getBody().contains(cell)) {
                return true;
            }
        }
        return foods.contains(cell);
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
    public int amountOfFoodNeeded(int playerCount) {
        return foodCoefficientA * playerCount + foodCoefficientB;
    }
    public void addFood(GameState.Coord food) {
        foods.add(food);
    }
    public void setFoods(List<GameState.Coord> foods) {
        this.foods = foods;
    }
    public void setSnakes(List<Snake> snakes) {
        this.snakes = snakes;
    }
    public void removeFood(GameState.Coord food) {
        foods.remove(food);
    }
}
