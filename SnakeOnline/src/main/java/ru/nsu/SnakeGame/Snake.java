package ru.nsu.SnakeGame;

import javafx.scene.paint.Color;
import ru.nsu.SnakesProto.*;

import java.util.*;

public class Snake {
    private final Deque<GameState.Coord> body; // Тело змеи, где body.peekFirst() - это голова
    private Direction direction; // Текущее направление змеи
    private final Queue<Direction> nextDirection;
    private GameState.Snake.SnakeState state;
    private boolean alive;
    private final Color color;
    private final int playerID;

    public Snake(ArrayList<GameState.Coord> initialPosition, int playerID) {
        body = new ArrayDeque<>();
        body.addAll(initialPosition);
        this.playerID = playerID;
        this.direction = Direction.RIGHT; // Начальное направление
        this.nextDirection = new LinkedList<>();
        this.state = GameState.Snake.SnakeState.ALIVE;
        alive = true;
        Random random = new Random();
        this.color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }

    public static Snake parseSnake(GameState.Snake snake) {
        ArrayList<GameState.Coord> bodySnake = new ArrayList<>();
        GameState.Coord head = snake.getPoints(0);
        bodySnake.add(head);

        int x = head.getX();
        int y = head.getY();

        for (int i = 1; i < snake.getPointsCount(); i++) {
            GameState.Coord offset = snake.getPoints(i);
            x += offset.getX();
            y += offset.getY();
            GameState.Coord newPoint = GameState.Coord.newBuilder().setX(x).setY(y).build();
            bodySnake.add(newPoint);
        }

        return new Snake(bodySnake, snake.getPlayerId());
    }

    public static GameState.Snake generateSnakeProto(Snake snake) {
        GameState.Snake.Builder snakeBuilder = GameState.Snake.newBuilder();

        if (!snake.getBody().isEmpty()) {
            Iterator<GameState.Coord> iterator = snake.getBody().iterator();
            GameState.Coord prevCoord = iterator.next();
            snakeBuilder.addPoints(prevCoord);

            int cumulativeX = 0;
            int cumulativeY = 0;

            while (iterator.hasNext()) {
                GameState.Coord currentCoord = iterator.next();
                int offsetX = currentCoord.getX() - prevCoord.getX();
                int offsetY = currentCoord.getY() - prevCoord.getY();

                // Если змейка продолжает двигаться в том же направлении, накапливаем смещение
                if ((cumulativeX == 0 || offsetX == 0) && (cumulativeY == 0 || offsetY == 0)) {
                    cumulativeX += offsetX;
                    cumulativeY += offsetY;
                } else {
                    // Если направление изменилось, добавляем накопленное смещение и начинаем заново
                    snakeBuilder.addPoints(GameState.Coord.newBuilder().setX(cumulativeX).setY(cumulativeY).build());
                    cumulativeX = offsetX;
                    cumulativeY = offsetY;
                }

                prevCoord = currentCoord;
            }

            // Добавляем оставшееся смещение
            if (cumulativeX != 0 || cumulativeY != 0) {
                snakeBuilder.addPoints(GameState.Coord.newBuilder().setX(cumulativeX).setY(cumulativeY).build());
            }
        }

        snakeBuilder.setPlayerId(snake.getPlayerID());
        return snakeBuilder.build();
    }

    public void move(GameField gameField) {
        GameState.Coord head = body.peekFirst();
        assert head != null;
        int dx = 0,dy = 0;

        // Изменяем направление только если не пытаемся двигаться в противоположную сторону

        while (!nextDirection.isEmpty()) {
            Direction dir = nextDirection.remove();
            if (dir == direction) continue;
            boolean dirChanged = false;
            switch (dir) {
                case LEFT : { if (direction != Direction.RIGHT) direction = Direction.LEFT; dirChanged = true; break; }
                case RIGHT: { if (direction != Direction.LEFT)  direction = Direction.RIGHT; dirChanged = true; break; }
                case UP   : { if (direction != Direction.DOWN)  direction = Direction.UP; dirChanged = true; break; }
                case DOWN : { if (direction != Direction.UP)    direction = Direction.DOWN; dirChanged = true; break; }
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
                .setX(head.getX() + dx)
                .setY(head.getY() + dy)
                .build();

        // Проверяем, не вышла ли змея за границы поля или не врезалась ли в себя
        if (newHead.getX() < 0 || newHead.getY() < 0 || newHead.getX() >= gameField.getWidth()
                || newHead.getY() >= gameField.getHeight() || body.contains(newHead)) {
            alive = false;
            return;
        }

        // Двигаем змею
        body.addFirst(newHead);
        body.removeLast(); // Удаляем последний элемент из очереди, чтобы змея двигалась
    }

    public void grow() {
        GameState.Coord tail = body.peekLast();
        body.addLast(tail);
    }

    public void setNextDirection(Direction newDirection) {
        nextDirection.add(newDirection);
    }
    public Deque<GameState.Coord> getBody() {
        return body;
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
    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean status) {
        alive = status;
    }
    public int getPlayerID() {
        return playerID;
    }
}

