package ru.nsu.SnakeGame;

import javafx.scene.paint.Color;
import ru.nsu.SnakesProto.*;

import java.util.*;

public class Snake {
    private Deque<GameState.Coord> body; // Тело змеи, где body.peekFirst() - это голова
    private Direction direction; // Текущее направление змеи
    private final Queue<Direction> nextDirection;
    private GameState.Snake.SnakeState state;
    private boolean updated; //Need to UI drow
    private final Color color;
    private final int playerID;
    private int score;

    public Snake(ArrayList<GameState.Coord> initialPosition, int playerID) {
        body = new ArrayDeque<>();
        body.addAll(initialPosition);
        this.playerID = playerID;
        this.direction = Direction.RIGHT; // Начальное направление
        this.nextDirection = new LinkedList<>();
        this.state = GameState.Snake.SnakeState.ALIVE;
        score = 0;
        Random random = new Random();
        this.color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }

    public static Snake parseSnake(GameState.Snake snake, int height, int width) {
        ArrayList<GameState.Coord> bodySnake = new ArrayList<>();
        GameState.Coord head = snake.getPoints(0);
        bodySnake.add(head);

        int x = head.getX();
        int y = head.getY();

        for (int i = 1; i < snake.getPointsCount(); i++) {
            GameState.Coord offset = snake.getPoints(i);

            // Добавляем каждую точку смещения между ключевыми точками
            for (int j = 0; j < Math.abs(offset.getX()); j++) {
                x += Integer.signum(offset.getX());
                if (x < 0) x += width; // Обработка выхода за левую границу
                else if (x >= width) x -= width; // Обработка выхода за правую границу
                bodySnake.add(GameState.Coord.newBuilder().setX(x).setY(y).build());
            }

            for (int j = 0; j < Math.abs(offset.getY()); j++) {
                y += Integer.signum(offset.getY());
                if (y < 0) y += height; // Обработка выхода за верхнюю границу
                else if (y >= height) y -= height; // Обработка выхода за нижнюю границу
                bodySnake.add(GameState.Coord.newBuilder().setX(x).setY(y).build());
            }
        }

        return new Snake(bodySnake, snake.getPlayerId());
    }

    public static GameState.Snake generateSnakeProto(Snake snake, int height, int width) {
        GameState.Snake.Builder snakeBuilder = GameState.Snake.newBuilder();
        List<GameState.Coord> body = new ArrayList<>(snake.getBody());

        if (!body.isEmpty()) {
            snakeBuilder.addPoints(body.get(0)); // Добавляем голову

            int prevX = body.get(0).getX();
            int prevY = body.get(0).getY();
            int cumulativeX = 0;
            int cumulativeY = 0;

            for (int i = 1; i < body.size(); i++) {
                int currentX = body.get(i).getX();
                int currentY = body.get(i).getY();

                int deltaX = currentX - prevX;
                int deltaY = currentY - prevY;

                // Корректируем deltaX и deltaY для обработки перехода через границы
                if (Math.abs(deltaX) > width / 2) {
                    deltaX = width - Math.abs(deltaX); // Корректировка для X
                    if (currentX > prevX) {
                        deltaX = -deltaX;
                    }
                }

                if (Math.abs(deltaY) > height / 2) {
                    deltaY = height - Math.abs(deltaY); // Корректировка для Y
                    if (currentY > prevY) {
                        deltaY = -deltaY;
                    }
                }

                // Проверяем, изменилось ли направление
                if ((deltaX != 0 && cumulativeY != 0) || (deltaY != 0 && cumulativeX != 0)) {
                    snakeBuilder.addPoints(GameState.Coord.newBuilder().setX(cumulativeX).setY(cumulativeY).build());
                    cumulativeX = 0;
                    cumulativeY = 0;
                }

                cumulativeX += deltaX;
                cumulativeY += deltaY;

                prevX = currentX;
                prevY = currentY;
            }

            // Добавляем последнее накопленное смещение
            if (cumulativeX != 0 || cumulativeY != 0) {
                snakeBuilder.addPoints(GameState.Coord.newBuilder().setX(cumulativeX).setY(cumulativeY).build());
            }
        }

        snakeBuilder.setPlayerId(snake.getPlayerID());
        snakeBuilder.setHeadDirection(snake.getHeadDirection());
        snakeBuilder.setState(snake.getState());
        return snakeBuilder.build();
    }

    // return successfully move
    public synchronized boolean move(GameField gameField) {
        GameState.Coord head = body.peekFirst();
        assert head != null;
        int dx = 0,dy = 0;

        while (!nextDirection.isEmpty()) {
            Direction dir = nextDirection.remove();
            if (dir == direction) continue;
            boolean dirChanged = false;
            switch (dir) {
                case LEFT -> {
                    if (direction != Direction.RIGHT) direction = Direction.LEFT;
                    dirChanged = true;
                }
                case RIGHT -> {
                    if (direction != Direction.LEFT) direction = Direction.RIGHT;
                    dirChanged = true;
                }
                case UP -> {
                    if (direction != Direction.DOWN) direction = Direction.UP;
                    dirChanged = true;
                }
                case DOWN -> {
                    if (direction != Direction.UP) direction = Direction.DOWN;
                    dirChanged = true;
                }
            }
            if (dirChanged) break;
        }

        switch (direction) {
            case UP    -> dy = -1;
            case DOWN  -> dy =  1;
            case LEFT  -> dx = -1;
            case RIGHT -> dx =  1;
        }
        GameState.Coord newHead = GameState.Coord.newBuilder()
                .setX((gameField.getWidth() + head.getX() + dx) % gameField.getWidth())
                .setY((gameField.getHeight() + head.getY() + dy) % gameField.getHeight())
                .build();

        // Проверяем не врезалась ли в себя
        if (body.contains(newHead)) {
            return false;
        }

        // Двигаем змею
        body.addFirst(newHead);
        body.removeLast(); // Удаляем последний элемент из очереди, чтобы змея двигалась
        return true;
    }

    public void grow() {
        GameState.Coord tail = body.peekLast();
        body.addLast(tail);
    }

    public synchronized void setNextDirection(Direction newDirection) {
        if (!nextDirection.isEmpty()) {
            // Предотвращение добавления направления, если оно противоположно текущему
            Direction lastDirection = nextDirection.peek();
            if (lastDirection == Direction.LEFT && newDirection == Direction.RIGHT ||
                    lastDirection == Direction.RIGHT && newDirection == Direction.LEFT ||
                    lastDirection == Direction.UP && newDirection == Direction.DOWN ||
                    lastDirection == Direction.DOWN && newDirection == Direction.UP) {
                return;
            }
        }
        nextDirection.add(newDirection);
    }
    public Deque<GameState.Coord> getBody() {
        return body;
    }
    public void setBody(Deque<GameState.Coord> newBody) {
        this.body = newBody;
    }
    public GameState.Coord getHead() {
        return body.peekFirst();
    }
    public void setState(GameState.Snake.SnakeState state) {
        this.state = state;
    }
    public Color getColor() {
        return color;
    }

    public Direction getHeadDirection() {
        return direction;
    }
    public GameState.Snake.SnakeState getState() {
        return state;
    }
    public boolean isUpdated() {
        return updated;
    }

    public void setUpdated() {
        updated = true;
    }
    public void clearUpdated() {
        updated = false;
    }
    public int getPlayerID() {
        return playerID;
    }
    public void setScore(int score) {
        this.score = score;
    }
    public int getScore() {
        return score;
    }
    public void addScore(int val) {
        score += val;
    }
}

