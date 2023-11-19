package ru.nsu.SnakeGame;

import javafx.scene.paint.Color;
import ru.nsu.SnakesProto.*;

import java.util.*;

public class Snake {
    private final Deque<GameState.Coord> body; // Тело змеи, где body.peekFirst() - это голова
    private Direction direction; // Текущее направление змеи
    private Queue<Direction> nextDirection;
    private GameState.Snake.SnakeState state;
    private boolean alive;
    private final Color color;
    private int playerID;

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
        GameState.Coord snakePointer = snake.getPoints(0);
        bodySnake.add(snakePointer);
        for (int i = 1; i < snake.getPointsCount(); ++i) {
            GameState.Coord tailMove = bodySnake.get(i);
            int x = snakePointer.getX();
            int y = snakePointer.getY();
            if (x > 0) {
                for (int j = 0; j < tailMove.getX(); ++j) {
                    snakePointer = GameState.Coord.newBuilder().setX(++x).setY(y).build();
                    bodySnake.add(snakePointer);
                }
            } else if (x < 0) {
                for (int j = 0; j > tailMove.getX(); --j) {
                    snakePointer = GameState.Coord.newBuilder().setX(--x).setY(y).build();
                    bodySnake.add(snakePointer);
                }
            } else if (y > 0) {
                for (int j = 0; j < tailMove.getY(); ++j) {
                    snakePointer = GameState.Coord.newBuilder().setX(x).setY(++y).build();
                    bodySnake.add(snakePointer);
                }
            } else if (y < 0) {
                for (int j = 0; j > tailMove.getY(); --j) {
                    snakePointer = GameState.Coord.newBuilder().setX(x).setY(--y).build();
                    bodySnake.add(snakePointer);
                }
            }
        }
        return new Snake(bodySnake, snake.getPlayerId());
    }

    public static GameState.Snake generateSnakeProto(Snake snake) {
        GameState.Snake.Builder snakeBuilder = GameState.Snake.newBuilder();
        Iterator<GameState.Coord> iterator = snake.getBody().iterator();

        if (iterator.hasNext()) {
            GameState.Coord headCoord = iterator.next();
            snakeBuilder.addPoints(headCoord);

            int pointX = headCoord.getX();
            int pointY = headCoord.getY();

            int oldOffsetX = 0;
            int oldOffsetY = 0;

            while (iterator.hasNext()) {
                GameState.Coord coord = iterator.next();

                int nPointX = coord.getX();
                int nPointY = coord.getY();

                int offsetX = nPointX - pointX;
                int offsetY = nPointY - pointY;

                if (offsetX != 0 && offsetY != 0) {
                    snakeBuilder.addPoints(GameState.Coord.newBuilder().setX(oldOffsetX).setY(oldOffsetX).build());
                    pointX = nPointX;
                    pointY = nPointY;
                    offsetX -= oldOffsetX;
                    offsetY -= oldOffsetY;
                }

                oldOffsetX = offsetX;
                oldOffsetY = offsetY;
            }

            if (oldOffsetX != 0 && oldOffsetY != 0) {
                snakeBuilder.addPoints(GameState.Coord.newBuilder().setX(oldOffsetX).setY(oldOffsetX).build());
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

