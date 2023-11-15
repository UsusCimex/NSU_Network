package ru.nsu.SnakeGame;

import ru.nsu.SnakesProto.*;

public class GameLogic {
    private final GameField gameField;

    public GameLogic(int width, int height) {
        this.gameField = new GameField(width, height);
    }

    public void update() {
        for (Snake snake : gameField.getSnakes()) {
            snake.move(gameField);
            if (!snake.isAlive()) continue;
            // Проверяем столкновения с едой
            if (gameField.getFoods().contains(snake.getHead())) {
                snake.grow();
                gameField.removeFood(snake.getHead());
                placeFood(); // Размещаем новую еду
            }

            for (Snake anotherSnake : gameField.getSnakes()) {
                if (anotherSnake.equals(snake)) continue;
                if (anotherSnake.getBody().contains(snake.getHead())) {
                    snake.setAlive(false);
                    break;
                }
            }
        }

        gameField.getSnakes().removeIf(snake -> !snake.isAlive());
    }

    public void placeFood() {
        // Размещаем еду в случайном месте на поле
        int x = (int) (Math.random() * gameField.getWidth());
        int y = (int) (Math.random() * gameField.getHeight());
        GameState.Coord foodPosition = GameState.Coord.newBuilder()
                .setX(x)
                .setY(y)
                .build();
        // Убедимся, что еда не появится в занятой клетке
        if (!isCellOccupied(foodPosition)) {
            gameField.addFood(foodPosition);
        } else {
            placeFood();
        }
    }

    private boolean isCellOccupied(GameState.Coord cell) {
        for (Snake snake : gameField.getSnakes()) {
            if (snake.getBody().contains(cell)) {
                return true;
            }
        }
        return gameField.getFoods().contains(cell);
    }

    public GameField getGameField() {
        return gameField;
    }
}
