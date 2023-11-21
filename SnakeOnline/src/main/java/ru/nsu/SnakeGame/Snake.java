package ru.nsu.SnakeGame;

import javafx.scene.paint.Color;
import ru.nsu.SnakesProto.*;

import java.util.*;

public class Snake {
    private Deque<GameState.Coord> body; // Тело змеи, где body.peekFirst() - это голова
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

            // Добавляем каждую точку смещения между ключевыми точками
            for (int j = 0; j < Math.abs(offset.getX()); j++) {
                x += Integer.signum(offset.getX()); // Увеличиваем или уменьшаем x в зависимости от знака смещения
                bodySnake.add(GameState.Coord.newBuilder().setX(x).setY(y).build());
            }
            for (int j = 0; j < Math.abs(offset.getY()); j++) {
                y += Integer.signum(offset.getY()); // Увеличиваем или уменьшаем y в зависимости от знака смещения
                bodySnake.add(GameState.Coord.newBuilder().setX(x).setY(y).build());
            }
        }

        return new Snake(bodySnake, snake.getPlayerId());
    }

    public static GameState.Snake generateSnakeProto(Snake snake) {
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

                // Проверяем, изменилось ли направление
                if ((deltaX != 0 && cumulativeY != 0) || (deltaY != 0 && cumulativeX != 0)) {
                    // Направление изменилось, добавляем накопленное смещение и обнуляем накопитель
                    snakeBuilder.addPoints(GameState.Coord.newBuilder().setX(cumulativeX).setY(cumulativeY).build());
                    cumulativeX = 0;
                    cumulativeY = 0;
                }

                // Накапливаем смещение
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

    public void move(GameField gameField) {
        GameState.Coord head = body.peekFirst();
        assert head != null;
        int dx = 0,dy = 0;

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

