package ru.nsu.SnakeGame;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import ru.nsu.SnakesProto.*;

import java.util.ArrayList;
import java.util.Arrays;

public class GameUI extends Application {
    private static final int CELL_SIZE = 15;

    private BorderPane root;
    private GridPane gameGrid;
    private TableView<String> leaderboardTable;
    private TableView<String> curGameInfo;
    private TableView<String> serverList;
    private int GameDelay = 80;
    private static GameField gameField = null;
    private Thread gameThread;
    private boolean gameRunning = false;

    private Snake testSnake = new Snake(new ArrayList<>(Arrays.asList(
            GameState.Coord.newBuilder().setX(2).setY(0).build(),
            GameState.Coord.newBuilder().setX(1).setY(0).build(),
            GameState.Coord.newBuilder().setX(0).setY(0).build()
    )));

    @Override
    public void start(Stage stage) {
        // Игровое поле
        gameGrid = new GridPane();
        gameGrid.setPadding(new Insets(10));
        gameGrid.setAlignment(Pos.CENTER);

        root = new BorderPane();
        root.setPrefSize(800, 600);

        root.setLeft(gameGrid);
        VBox rightPanel = createRightPanel();
        root.setRight(rightPanel);

        Scene scene = new Scene(root);
        scene.setOnKeyPressed(event -> handleKeyPress(event.getCode()));
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(event -> {
            gameRunning = false;
            Platform.exit();
        });
        gameRunning = true;
        runGameLoop();
    }

    private void clearGameGrid() {
        gameGrid.getChildren().clear();

        for (int i = 0; i < gameField.getWidth(); i++) {
            for (int j = 0; j < gameField.getHeight(); j++) {
                Rectangle cell = createTile(i, j, Color.LIGHTGRAY);
                gameGrid.add(cell, i, j);
            }
        }
    }

    private VBox createRightPanel() {
        VBox vbox = new VBox();
        vbox.setSpacing(10);
        vbox.setPadding(new Insets(10));

        // Таблица лидеров
        Label lBoard = new Label("Leaderboard");
        leaderboardTable = new TableView<>();
        TableColumn<String, String> index = new TableColumn<>("Place");
        TableColumn<String, String> nameColumn = new TableColumn<>("Player Name");
        TableColumn<String, String> scoreColumn = new TableColumn<>("Score");
        leaderboardTable.getColumns().addAll(index, nameColumn, scoreColumn);
        VBox leaderBox = new VBox(lBoard, leaderboardTable);

        // Информация о текущей игре
        Label cGame = new Label("Current game");
        curGameInfo = new TableView<>();
        TableColumn<String, String> leader = new TableColumn<>("Leader");
        TableColumn<String, String> areaSize = new TableColumn<>("Area size");
        TableColumn<String, String> food = new TableColumn<>("Food");
        curGameInfo.getColumns().addAll(leader, areaSize, food);
        VBox curGameBox = new VBox(cGame, curGameInfo);

        HBox hBox1 = new HBox(leaderBox, curGameBox);
        hBox1.setSpacing(10);
        vbox.getChildren().add(hBox1);

        Button createGameButton = new Button("Create Game");
        createGameButton.setPrefSize(200, 100);
        Button exitGameButton = new Button("Exit Game");
        exitGameButton.setPrefSize(200, 100);

        HBox buttonBox = new HBox(createGameButton, exitGameButton);
        buttonBox.setSpacing(10);
        vbox.getChildren().add(buttonBox);

        serverList = new TableView<>();
        TableColumn<String, String> serverLeader = new TableColumn<>("Leader");
        TableColumn<String, String> serverOnline = new TableColumn<>("Online");
        TableColumn<String, String> serverAreaSize = new TableColumn<>("AreaSize");
        TableColumn<String, String> serverFood = new TableColumn<>("Food");
        serverList.getColumns().addAll(serverLeader, serverOnline, serverAreaSize, serverFood);

        vbox.getChildren().add(serverList);

        return vbox;
    }

    private void runGameLoop() {
        gameThread = new Thread(() -> {
            while (gameRunning) {
                render();
                try {
                    Thread.sleep(GameDelay); // Регулировка частоты обновлений
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        gameThread.start();
    }

    private void handleKeyPress(KeyCode code) { // test
        System.err.println(code);
        switch (code) {
            case A, LEFT -> testSnake.setNextDirection(Direction.LEFT);
            case S, DOWN -> testSnake.setNextDirection(Direction.DOWN);
            case D, RIGHT -> testSnake.setNextDirection(Direction.RIGHT);
            case W, UP -> testSnake.setNextDirection(Direction.UP);
        }
    }

    private void render() {
        if (gameField == null) return;
        // Очищаем игровое поле
        Platform.runLater(this::clearGameGrid);

        // Отрисовываем змей
        for (Snake snake : gameField.getSnakes()) {
            int counter = 0;
            for (GameState.Coord p : snake.getBody()) {
                Color curColor;
                if (counter == 0) {
                    curColor = snake.getColor().darker();
                } else if (counter % 6 < 3) {
                    curColor = snake.getColor();
                } else {
                    curColor = snake.getColor().brighter();
                }
                Rectangle rect = createTile(p.getX(), p.getY(), curColor);
                Platform.runLater(() -> gameGrid.add(rect, p.getX(), p.getY()));
                counter++;
            }
        }

        // Отрисовываем еду
        for (GameState.Coord food : gameField.getFoods()) {
            Rectangle rect = createTile(food.getX(), food.getY(), Color.RED);
            Platform.runLater(() -> gameGrid.add(rect, food.getX(), food.getY()));
        }
    }

    private Rectangle createTile(int x, int y, Color color) {
        Rectangle rect = new Rectangle(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
        rect.setFill(color);
        return rect;
    }

    public static void setGameField(GameField field) {
        gameField = field;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
