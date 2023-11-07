package ru.nsu;

import javafx.scene.paint.Color;

import java.util.LinkedList;

public class Snake {
    private final String name;
    private final Color color;
    private LinkedList<SnakeGame.Coord> body;
    private Direction direction;

    private Direction nextDirection;
    private int bodyParts;
    private int score;

    public Snake(String name, Color color) {
        this.name = name;
        this.color = color;
    }

    // RETURN:
    // 0, if nothing happened
    // Snake name, if crashed into snake
    public String move(GameContext gameContext) {
        // Сохраняем координаты головы
        SnakeGame.Coord head = body.getFirst();
        int newX = head.x();
        int newY = head.y();

        direction = nextDirection;
        switch (direction) {
            case UP -> newY -= 1;
            case DOWN -> newY += 1;
            case LEFT -> newX -= 1;
            case RIGHT -> newX += 1;
        }

        // Зацикливание через стены
        if (newX >= gameContext.getAreaWidth()) newX = 0;
        if (newX < 0) newX = gameContext.getAreaWidth() - 1;
        if (newY >= gameContext.getAreaHeight()) newY = 0;
        if (newY < 0) newY = gameContext.getAreaHeight() - 1;
        SnakeGame.Coord newHead = new SnakeGame.Coord(newX, newY);
        body.addFirst(newHead);
        if (!gameContext.getApples().contains(newHead)) body.removeLast();
        for (var snake : gameContext.getSnakes().values()) {
            if (snake.getBody().contains(newHead)) return snake.getName();
        }
        return null;
    }
    public LinkedList<SnakeGame.Coord> getBody() {
        return body;
    }

    public String getName() {
        return name;
    }

    public void setNextDirection(Direction nextDirection) {
        if (nextDirection == Direction.LEFT) {
            if (direction != Direction.RIGHT) this.nextDirection = Direction.LEFT;
        } else if (nextDirection == Direction.RIGHT) {
            if (direction != Direction.LEFT) this.nextDirection = Direction.RIGHT;
        } else if (nextDirection == Direction.DOWN) {
            if (direction != Direction.UP) this.nextDirection = Direction.DOWN;
        } else if (nextDirection == Direction.UP) {
            if (direction != Direction.DOWN) this.nextDirection = Direction.UP;
        }
    }

    public Color getColor() {
        return color;
    }
}
