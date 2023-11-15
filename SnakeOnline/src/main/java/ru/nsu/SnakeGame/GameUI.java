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
import ru.nsu.SnakeClient;
import ru.nsu.SnakeServer;
import ru.nsu.SnakesProto.*;
import ru.nsu.patterns.Observer;

import java.io.IOException;

public class GameUI extends Application implements Observer {
    private static final int CELL_SIZE = 15;

    private BorderPane root;
    private GridPane gameGrid;

    private TableView<String> leaderboardTable;
    private TableView<String> curGameInfo;
    private TableView<String> serverList;

    private static GameField gameField = null;

    private SnakeServer server = null;
    private boolean serverWorking = false;
    private SnakeClient client = null;
    private boolean clientWorking = false;

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
            serverWorking = false;
            clientWorking = false;
            Platform.exit();
        });
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
        createGameButton.setOnAction(event -> openCreateGameForm());
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

    private void openCreateGameForm() {
        Stage createGameStage = new Stage();
        createGameStage.setTitle("Create Game");

        // Создаем компоненты для формы создания игры
        Label nameLabel = new Label("Game Name:");
        TextField nameTextField = new TextField();

        Label sizeLabel = new Label("Field Size:");
        Label widthLabel = new Label("Width:");
        TextField widthTextField = new TextField();
        Label heightLabel = new Label("Height:");
        TextField heightTextField = new TextField();

        // Добавляем компоненты для формулы ax + b
        Label formulaLabel = new Label("Food Formula(x - player count): ax + b");
        Label coefficientALabel = new Label("Coefficient a:");
        TextField coefficientATextField = new TextField();

        Label coefficientBLabel = new Label("Coefficient b:");
        TextField coefficientBTextField = new TextField();

        Button confirmButton = new Button("Confirm");
        confirmButton.setOnAction(event -> {
            // Получаем введенные значения
            String gameName = nameTextField.getText();
            int fieldWidth = Integer.parseInt(widthTextField.getText());
            int fieldHeight = Integer.parseInt(heightTextField.getText());
            int foodCoefficientA = Integer.parseInt(coefficientATextField.getText());
            int foodCoefficientB = Integer.parseInt(coefficientBTextField.getText());

            // Создаем новую игру с полученными параметрами
            try {
                startServer(gameName, fieldWidth, fieldHeight, foodCoefficientA, foodCoefficientB);
            } catch (IOException e) {
                System.err.println("Server error");
                throw new RuntimeException(e);
            }

            // Закрываем форму создания игры
            createGameStage.close();
        });

        // Создаем и настраиваем layout для формы
        VBox layout = new VBox();
        layout.setSpacing(10);
        layout.setPadding(new Insets(10));
        layout.getChildren().addAll(nameLabel, nameTextField, sizeLabel, widthLabel, widthTextField, heightLabel, heightTextField,
                                    formulaLabel, coefficientALabel, coefficientATextField, coefficientBLabel, coefficientBTextField, confirmButton);

        Scene scene = new Scene(layout);
        createGameStage.setScene(scene);
        createGameStage.show();
    }

    private void startServer(String gameName, int fieldWidth, int fieldHeight, int foodCoefficientA, int foodCoefficientB) throws IOException {
        gameField = new GameField(fieldWidth, fieldHeight, foodCoefficientA, foodCoefficientB);
        // Создаем и запускаем сервер
        server = new SnakeServer(gameName, 21212, gameField);
        new Thread(() -> server.start()).start();
    }

    private void handleKeyPress(KeyCode code) {
        // if player tap LEFT, UP, RIGHT, DOWN -> move snake!
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

    @Override
    public void update(Object o) {
        if (o instanceof GameField) {
            gameField = (GameField) o;
            render();
        }
    }
}
