package ru.nsu.SnakeGame;

import ru.nsu.SnakesProto.*;
import ru.nsu.UI.ServerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameField {
    private final int width;
    private final int height;
    private final int foodCoefficientA;
    private final int foodCoefficientB;
    private final int delayMS;
    private List<Snake> snakes;
    private ArrayList<GameState.Coord> foods;
    public GameField(ServerInfo serverInfo) {
        String[] numbers = serverInfo.areaSizeProperty().get().split("[^0-9]+");
        this.width = Integer.parseInt(numbers[0]);
        this.height = Integer.parseInt(numbers[1]);
        this.foodCoefficientA = serverInfo.foodCoefficientAProperty().get();
        this.foodCoefficientB = serverInfo.foodCoefficientBProperty().get();
        this.delayMS = serverInfo.stateDelayMsProperty().get();

        this.snakes = new ArrayList<>();
        this.foods = new ArrayList<>();
    }

    public GameField(int width, int height, int foodCoefficientA, int foodCoefficientB, int delayMS) {
        this.width = width;
        this.height = height;
        this.foodCoefficientA = foodCoefficientA;
        this.foodCoefficientB = foodCoefficientB;
        this.delayMS = delayMS;

        this.snakes = new ArrayList<>();
        this.foods = new ArrayList<>();
    }

    public synchronized ArrayList<GameState.Coord> findValidSnakePosition() {
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
                snk.setHeadDirection(snake.getHeadDirection());
                snk.setUpdated();
                return;
            }
        }
        addSnake(snake);
        snake.setUpdated();
    }
    public void addSnake(Snake snake) {
        snakes.add(snake);
    }
    public synchronized void removeSnake(Snake snake) {
        Random random = new Random();
        for (GameState.Coord body : snake.getBody()) {
            if (body.equals(snake.getHead())) continue;
            if (random.nextInt(100) < 50) { // 50% to spawn apple
                foods.add(body);
            }
        }
        snakes.remove(snake);
    }
    public int amountOfFoodNeeded(int playerCount) {
        return foodCoefficientA * playerCount + foodCoefficientB;
    }
    public int getFoodCoefficientA() {
        return foodCoefficientA;
    }
    public int getFoodCoefficientB() {
        return foodCoefficientB;
    }
    public List<Snake> getSnakes() {
        return new ArrayList<>(snakes);
    }
    public ArrayList<GameState.Coord> getFoods() {
        return foods;
    }
    public int getDelayMS() {
        return delayMS;
    }

    public boolean hasPlace() {
        int foodSize = foods.size();
        int snakeSize = 0;
        for (Snake snake : getSnakes()) {
            snakeSize += snake.getBody().size();
        }
        return (foodSize + snakeSize) < (width * height - 1);
    }
}
