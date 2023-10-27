package ru.nsu;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.LinkedList;

public class SnakeGame extends Application {
    public record Coord(int x, int y) {}
    private static final int WIDTH = 20;
    private static final int HEIGHT = 20;
    private static final int UNIT_SIZE = 20;
    private static final int DELAY = 75;

    private LinkedList<Coord> snake = new LinkedList<>();
    private int bodyParts = 6;
    private int applesEaten;
    private Coord apple;
    private char direction = 'R';
    private char nextDirection = direction;
    private boolean running = false;

    public void start(Stage primaryStage) {
        Pane root = new Pane();
        Scene scene = new Scene(root, WIDTH * UNIT_SIZE, HEIGHT * UNIT_SIZE);
        Canvas canvas = new Canvas(WIDTH * UNIT_SIZE, HEIGHT * UNIT_SIZE);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        root.getChildren().add(canvas);
        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case LEFT:
                    if (direction != 'R') nextDirection = 'L';
                    break;
                case RIGHT:
                    if (direction != 'L') nextDirection = 'R';
                    break;
                case UP:
                    if (direction != 'D') nextDirection = 'U';
                    break;
                case DOWN:
                    if (direction != 'U') nextDirection = 'D';
                    break;
            }
        });

        newSnake();
        newApple();
        running = true;

        KeyFrame keyFrame = new KeyFrame(Duration.millis(DELAY), event -> {
            if (running) {
                move();
                checkCollision();
                draw(gc);
            }
        });

        Timeline timeline = new Timeline(keyFrame);
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

        primaryStage.setScene(scene);
        primaryStage.setTitle("Snake Game");
        primaryStage.show();
    }

    private void newSnake() {
        for (int i = 0; i < bodyParts; ++i) {
            snake.addFirst(new Coord(i * UNIT_SIZE, 0));
        }
    }

    public void draw(GraphicsContext gc) {
        if (running) {
            // Clear the canvas
            gc.clearRect(0, 0, WIDTH * UNIT_SIZE, HEIGHT * UNIT_SIZE);

            // Draw apple
            gc.setFill(Color.RED);
            gc.fillOval(apple.x(), apple.y(), UNIT_SIZE, UNIT_SIZE);

            // Draw snake
            for (int i = 0; i < bodyParts; i++) {
                if (i == 0) {
                    gc.setFill(Color.GREEN.darker());
                } else {
                    if (i % 2 == 0) {
                        gc.setFill(Color.GREEN);
                    } else {
                        gc.setFill(Color.GREEN.brighter());
                    }
                }
                gc.fillRect(snake.get(i).x(), snake.get(i).y(), UNIT_SIZE, UNIT_SIZE);
            }

            // Display the score
            gc.setFill(Color.ORANGE);
            gc.fillText("Score: " + applesEaten, 10, 30);
        } else {
            gc.setFill(Color.RED);
            gc.fillText("Game Over", WIDTH * UNIT_SIZE / 2 - 50, HEIGHT * UNIT_SIZE / 2);
            gc.setFill(Color.PINK);
            gc.fillText("Your Score: " + applesEaten, WIDTH * UNIT_SIZE / 2 - 40, HEIGHT * UNIT_SIZE / 2 + 20);
        }
    }

    public void newApple() {
        if (applesEaten == WIDTH * HEIGHT - bodyParts) {
            running = false;
        }

        Coord tempApple;
        while(true) {
            int appleX = (int) (Math.random() * WIDTH) * UNIT_SIZE;
            int appleY = (int) (Math.random() * HEIGHT) * UNIT_SIZE;
            tempApple = new Coord(appleX, appleY);
            boolean flag = true;
            for (int i = 0; i < bodyParts; ++i) {
                if (snake.get(i).equals(tempApple)) {
                    flag = false;
                    break;
                }
            }
            if (flag) {
                break;
            }
        }
        apple = tempApple;
    }

    public void move() {
        // Сохраняем координаты головы
        Coord head = snake.getFirst();
        int newX = head.x() / UNIT_SIZE;
        int newY = head.y() / UNIT_SIZE;

        // Обновляем позицию головы
        direction = nextDirection;
        switch (direction) {
            case 'U' -> newY -= 1;
            case 'D' -> newY += 1;
            case 'L' -> newX -= 1;
            case 'R' -> newX += 1;
        }

        // Зацикливание через стены
        if (newX >= WIDTH) newX = 0;
        if (newX < 0) newX = WIDTH - 1;
        if (newY >= HEIGHT) newY = 0;
        if (newY < 0) newY = HEIGHT - 1;

        snake.addFirst(new Coord(newX * UNIT_SIZE, newY * UNIT_SIZE));
        if (!checkApple()) snake.removeLast();
    }

    public boolean checkApple() {
        if (snake.getFirst().equals(apple)) {
            bodyParts++;
            applesEaten++;
            newApple();
            return true;
        }
        return false;
    }

    public void checkCollision() {
        // Check self-collision
        for (int i = bodyParts - 1; i > 0; i--) {
            if (snake.getFirst().equals(snake.get(i))) {
                running = false;
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
