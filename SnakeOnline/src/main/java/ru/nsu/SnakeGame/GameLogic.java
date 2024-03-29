package ru.nsu.SnakeGame;

import ru.nsu.SnakesProto.*;

public class GameLogic {
    private final GameField gameField;

    public GameLogic(GameField gameField) {
        this.gameField = gameField;
    }

    public static void editGameFieldFromState(GameField gameField, GameMessage.StateMsg stateMsg) {
        if (gameField == null || stateMsg == null) return;
        gameField.getFoods().clear();
        for (GameState.Coord apple : stateMsg.getState().getFoodsList()) {
            gameField.getFoods().add(apple);
        }
        for (Snake snake : gameField.getSnakes()) {
            snake.clearUpdated();
        }
        for (GameState.Snake snake : stateMsg.getState().getSnakesList()) {
            gameField.updateSnake(Snake.parseSnake(snake, gameField.getHeight(), gameField.getWidth()));
        }
        for (Snake snake : gameField.getSnakes()) {
            if (!snake.isUpdated()) {
                gameField.removeSnake(snake);
            }
        }
    }

    public void update() {
        if (gameField.getFoods().size() < gameField.amountOfFoodNeeded(gameField.getSnakes().size())) {
            int appleNeeded = gameField.amountOfFoodNeeded(gameField.getSnakes().size()) - gameField.getFoods().size();
            System.err.println("[GameLogic] Needed " + appleNeeded + " apples");
            for (int i = 0; i < appleNeeded; ++i) {
                if (gameField.hasPlace()) {
                    placeFood();
                } else {
                    System.err.println("[GameLogic] Place not found...!");
                    break;
                }
            }
        }

        for (Snake snake : gameField.getSnakes()) {
            if (!snake.move(gameField)) {
                gameField.removeSnake(snake);
            }
            // Проверяем столкновения с едой
            if (gameField.getFoods().contains(snake.getHead())) {
                snake.addScore(1);

                gameField.getFoods().remove(snake.getHead());
            } else {
                snake.getBody().removeLast(); // Удаляем последний элемент из очереди, чтобы змея двигалась
            }
        }

        for (Snake snake : gameField.getSnakes()) {
            for (Snake anotherSnake : gameField.getSnakes()) {
                if (anotherSnake.equals(snake)) continue;
                if (anotherSnake.getBody().contains(snake.getHead())) {
                    anotherSnake.addScore(1);
                    gameField.removeSnake(snake);
                    break;
                }
            }
        }
    }

    public void updateDirection(int playerId, Direction newDirection) {
        for (Snake snake : gameField.getSnakes()) {
            if (snake.getPlayerID() == playerId) {
                snake.setNextDirection(newDirection);
                return;
            }
        }
        System.err.println("[GameLogic] updateDirection: PlayerID " + playerId + " not found!");
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
        if (!gameField.isCellOccupied(foodPosition)) {
            gameField.getFoods().add(foodPosition);
        } else {
            placeFood();
        }
    }

    public void addSnake(Snake snake) {
        gameField.addSnake(snake);
    }

    public GameField getGameField() {
        return gameField;
    }
}
