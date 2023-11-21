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

    public ArrayList<GameState.Coord> findValidSnakePosition() {
        Random random = new Random();
        int maxAttempts = 1000;
        int minSnakeSize = 3;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = random.nextInt(minSnakeSize,width - minSnakeSize - 3); // Убедимся, что есть место для всей змейки
            int y = random.nextInt(height);

            ArrayList<GameState.Coord> initialPosition = new ArrayList<>();
            boolean validPosition = true;

            for (int i = 0; i < minSnakeSize; i++) {
                GameState.Coord cell = GameState.Coord.newBuilder()
                        .setX(x - i)
                        .setY(y)
                        .build();

                if (isCellOccupied(cell)) {
                    validPosition = false;
                    break;
                }
                initialPosition.add(cell);
            }

            if (validPosition) {
                return initialPosition;
            }
        }

        throw new RuntimeException("Unable to find a valid position for the new snake");
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
    public void updateSnake(Snake snake) {
        for (Snake snk : snakes) {
            if (snk.getPlayerID() == snake.getPlayerID()) {
                snk.setBody(snake.getBody());
                return;
            }
        }
        System.err.println("Add new snake(" + snake.getPlayerID() + ")");
        addSnake(snake);
    }
    public void addSnake(Snake snake) {
        snakes.add(snake);
    }
    public void removeSnake(Snake snake) {
        snakes.remove(snake);
    }
    public int amountOfFoodNeeded(int playerCount) {
        return foodCoefficientA * playerCount + foodCoefficientB;
    }
    public List<Snake> getSnakes() {
        return new ArrayList<>(snakes);
    }
    public List<GameState.Coord> getFoods() {
        return new ArrayList<>(foods);
    }
    public void setFoods(List<GameState.Coord> foods) {
        this.foods = foods;
    }
    public void setSnakes(List<Snake> snakes) {
        this.snakes = snakes;
    }
}
