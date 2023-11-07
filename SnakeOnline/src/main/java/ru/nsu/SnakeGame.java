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

// CLIENT
public class SnakeGame extends Application {
    public record Coord(int x, int y) {}
    private GameContext gameContext; // server need to send me
    private Direction direction = Direction.RIGHT;
    private static final int UNIT_SIZE = 20;
    private static final int DELAY = 75;

    public void start(Stage primaryStage) {
        Pane root = new Pane();
        Scene scene = new Scene(root, gameContext.getAreaWidth() * UNIT_SIZE, gameContext.getAreaHeight() * UNIT_SIZE);
        Canvas canvas = new Canvas(gameContext.getAreaWidth() * UNIT_SIZE, gameContext.getAreaHeight() * UNIT_SIZE);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        root.getChildren().add(canvas);
        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case LEFT:
                    direction = Direction.LEFT;
                    break;
                case RIGHT:
                    direction = Direction.RIGHT;
                    break;
                case UP:
                    direction = Direction.UP;
                    break;
                case DOWN:
                    direction = Direction.DOWN;
                    break;
            }
        });

        KeyFrame keyFrame = new KeyFrame(Duration.millis(DELAY), event -> draw(gc, gameContext));

        Timeline timeline = new Timeline(keyFrame);
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

        primaryStage.setScene(scene);
        primaryStage.setTitle("Snake Game");
        primaryStage.show();
    }

    public void draw(GraphicsContext gc, GameContext gameContext) {
        if (gameContext.isGameStatus()) {
            // Clear the canvas
            gc.clearRect(0, 0, gameContext.getAreaWidth() * UNIT_SIZE, gameContext.getAreaHeight() * UNIT_SIZE);

            // Draw apple
            gc.setFill(Color.RED);
            for (var apple : gameContext.getApples()) {
                gc.fillOval(apple.x(), apple.y(), UNIT_SIZE, UNIT_SIZE);
            }

            for (var snake : gameContext.getSnakes().values()) {
                // Draw snake
                for (int i = 0; i < snake.getBody().size(); i++) {
                    if (i == 0) {
                        gc.setFill(snake.getColor().darker());
                    } else {
                        if (i % 2 == 0) {
                            gc.setFill(snake.getColor());
                        } else {
                            gc.setFill(snake.getColor().brighter());
                        }
                    }
                    gc.fillRect(snake.getBody().get(i).x(), snake.getBody().get(i).y(), UNIT_SIZE, UNIT_SIZE);
                }
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
