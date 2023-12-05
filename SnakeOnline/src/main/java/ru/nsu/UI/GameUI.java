package ru.nsu.UI;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import ru.nsu.Controller;
import ru.nsu.SnakeGame.GameField;
import ru.nsu.SnakeGame.GameLogic;
import ru.nsu.SnakeGame.Snake;
import ru.nsu.SnakeNet;
import ru.nsu.SnakesProto.*;
import ru.nsu.patterns.Observer;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

public class GameUI extends Application implements Observer {
    private static double CELL_SIZE;
    private final long threshold = 3000;

    private BorderPane root;
    private GridPane gameGrid;

    private TableView<PlayerInfo> leaderboardTable;
    private TableView<ServerInfo> curGameInfo;
    private TableView<ServerInfo> serverList = new TableView<>();
    private final Map<String, Timer> serverTimers = new HashMap<>();

    private GameField gameField = null;

    private volatile boolean gameRunning = false;

    private Button createGameButton;
    private Button exitGameButton;
    private final Controller controller = new Controller(this);

    @Override
    public void start(Stage stage) {
        // Игровое поле
        gameGrid = new GridPane();
        gameGrid.setPadding(new Insets(10));
        gameGrid.setAlignment(Pos.CENTER);

        root = new BorderPane();
        root.setPrefSize(1000, 700);

        root.setLeft(gameGrid);
        VBox rightPanel = createRightPanel();
        root.setRight(rightPanel);
        updateButtonsState();

        Scene scene = new Scene(root);
        scene.setOnKeyPressed(event -> handleKeyPress(event.getCode()));
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(event -> {
            gameExit();
            controller.close();

            for (Timer timer : serverTimers.values()) {
                timer.cancel();
            }
            Platform.exit();
        });

        controller.start();
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
        TableColumn<PlayerInfo, String> placeColumn = new TableColumn<>("Place");
        TableColumn<PlayerInfo, String> nameColumn = new TableColumn<>("Player Name");
        TableColumn<PlayerInfo, String> scoreColumn = new TableColumn<>("Score");
        placeColumn.setCellValueFactory(new PropertyValueFactory<>("place"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("playerName"));
        scoreColumn.setCellValueFactory(new PropertyValueFactory<>("score"));
        leaderboardTable.getColumns().addAll(placeColumn, nameColumn, scoreColumn);
        VBox leaderBox = new VBox(lBoard, leaderboardTable);

        // Информация о текущей игре
        Label cGame = new Label("Current game");
        curGameInfo = new TableView<>();
        TableColumn<ServerInfo, String> gameNameColumn = new TableColumn<>("Game name");
        TableColumn<ServerInfo, String> areaSizeColumn = new TableColumn<>("Area size");
        TableColumn<ServerInfo, String> foodColumn = new TableColumn<>("Food");
        gameNameColumn.setCellValueFactory(new PropertyValueFactory<>("serverName"));
        areaSizeColumn.setCellValueFactory(new PropertyValueFactory<>("areaSize"));
        foodColumn.setCellValueFactory(new PropertyValueFactory<>("foodCoefficientB"));
        curGameInfo.getColumns().addAll(gameNameColumn, areaSizeColumn, foodColumn);
        VBox curGameBox = new VBox(cGame, curGameInfo);

        HBox hBox1 = new HBox(leaderBox, curGameBox);
        hBox1.setSpacing(10);
        vbox.getChildren().add(hBox1);

        createGameButton = new Button("Create Game");
        createGameButton.setPrefSize(200, 100);
        createGameButton.setOnAction(event -> openCreateGameForm());
        exitGameButton = new Button("Exit Game");
        exitGameButton.setPrefSize(200, 100);
        exitGameButton.setOnAction(event -> gameExit());

        HBox buttonBox = new HBox(createGameButton, exitGameButton);
        buttonBox.setSpacing(10);
        vbox.getChildren().add(buttonBox);

        serverList = new TableView<>();
        TableColumn<ServerInfo, String> serverLeader = new TableColumn<>("Game name");
        TableColumn<ServerInfo, String> serverOnline = new TableColumn<>("Online");
        TableColumn<ServerInfo, String> serverAreaSize = new TableColumn<>("AreaSize");
        TableColumn<ServerInfo, String> serverFood = new TableColumn<>("Food");
        serverLeader.setCellValueFactory(new PropertyValueFactory<>("serverName"));
        serverOnline.setCellValueFactory(new PropertyValueFactory<>("online"));
        serverAreaSize.setCellValueFactory(new PropertyValueFactory<>("areaSize"));
        serverFood.setCellValueFactory(new PropertyValueFactory<>("foodCoefficientB"));
        serverList.getColumns().addAll(serverLeader, serverOnline, serverAreaSize, serverFood);

        serverList.setRowFactory(tv -> {
            TableRow<ServerInfo> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    ServerInfo clickedRow = row.getItem();
                    if (!gameRunning) { // Проверка, что пользователь не находится в игре
                        showJoinGameDialog(clickedRow);
                    }
                }
            });
            return row;
        });

        vbox.getChildren().add(serverList);
        return vbox;
    }

    private void showJoinGameDialog(ServerInfo serverInfo) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Join Game");
        dialog.setHeaderText("Enter Your Name to Join: " + serverInfo.serverNameProperty().get());
        dialog.setContentText("Name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            generateGameField(serverInfo);

            try {
                controller.startClient(name, serverInfo);

                gameRunning = true;
                updateButtonsState(); // Обновление состояния кнопок
                updateCurGameInfo(serverInfo);
            } catch (IOException e) {
                System.err.println("Start client error");
                e.printStackTrace();
            }
        });
    }

    private void generateGameField(ServerInfo serverInfo) {
        String[] numbers = serverInfo.areaSizeProperty().get().split("[^0-9]+");
        int width = Integer.parseInt(numbers[0]);
        int height = Integer.parseInt(numbers[1]);
        adjustCellSize(width, height);

        gameField = new GameField(serverInfo);
    }

    private void adjustCellSize(int fieldWidth, int fieldHeight) {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();

        // Ширина и высота, доступные для игрового поля
        double availableWidth = screenBounds.getWidth() - 900;
        double availableHeight = screenBounds.getHeight() - 500;

        double cellWidth = availableWidth / fieldWidth;
        double cellHeight = availableHeight / fieldHeight;

        CELL_SIZE = Math.min(cellWidth, cellHeight);
    }

    private void gameExit() {
        gameRunning = false;

        leaderboardTable.getItems().clear();
        curGameInfo.getItems().clear();
        if (gameField != null) clearGameGrid();

        controller.stopServer();

        updateButtonsState();
    }

    private void openCreateGameForm() {
        Stage createGameStage = new Stage();
        createGameStage.setTitle("Create Game");

        // Создаем компоненты для формы создания игры
        Label playerNameLabel = new Label("Your name:");
        TextField playerNameTextField = new TextField();

        Label nameLabel = new Label("Game Name:");
        TextField nameTextField = new TextField();

        Label sizeLabel = new Label("Field Size:");
        Label widthLabel = new Label("Width (10 - 100):");
        TextField widthTextField = new TextField();
        Label heightLabel = new Label("Height (10 - 100):");
        TextField heightTextField = new TextField();

        // Добавляем компоненты для формулы ax + b
        Label formulaLabel = new Label("Food Formula(x - player count): ax + b");
        Label coefficientALabel = new Label("Coefficient a:");
        TextField coefficientATextField = new TextField();

        Label coefficientBLabel = new Label("Coefficient b:");
        TextField coefficientBTextField = new TextField();

        Label gameSpeedLabel = new Label("Game Speed (100 - 5000):");
        TextField gameSpeedTextField = new TextField();

        Button confirmButton = new Button("Confirm");
        confirmButton.setOnAction(event -> {
            boolean dataIsValid = true;
            // Получаем введенные значения
            String playerName = "playerName", gameName = "gameName";
            int fieldWidth = 0, fieldHeight = 0,
                foodCoefficientA = 0, foodCoefficientB = 0,
                gameSpeed = 0;
            try {
                playerName = playerNameTextField.getText();
                gameName = nameTextField.getText();
                fieldWidth = Integer.parseInt(widthTextField.getText());
                if (fieldWidth < 10 || fieldWidth > 100) throw new NumberFormatException("width");
                fieldHeight = Integer.parseInt(heightTextField.getText());
                if (fieldHeight < 10 || fieldHeight > 100) throw new NumberFormatException("height");
                foodCoefficientA = Integer.parseInt(coefficientATextField.getText());
                foodCoefficientB = Integer.parseInt(coefficientBTextField.getText());
                if (foodCoefficientA + foodCoefficientB > fieldWidth * fieldHeight) throw new NumberFormatException("food");
                gameSpeed = Integer.parseInt(gameSpeedTextField.getText());
                if (gameSpeed < 100 || gameSpeed > 5000) throw new NumberFormatException("speed");
            } catch (NumberFormatException e) {
                switch (e.getMessage()) {
                    case "width" -> widthTextField.clear();
                    case "height" -> heightTextField.clear();
                    case "coefficients" -> {
                        coefficientATextField.clear();
                        coefficientBTextField.clear();
                    }
                    case "food" -> gameSpeedTextField.clear();
                }

                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Ошибка");
                alert.setHeaderText(null);
                alert.setContentText("Введите правильные данные:\n" + e.getMessage());

                alert.showAndWait();
                dataIsValid = false;
            }

            if (dataIsValid) {
                // Создаём игру
                try {
                    ServerInfo serverInfo = new ServerInfo(gameName,
                        0,
                        widthTextField.getText() + "x" + heightTextField.getText(),
                        foodCoefficientA,
                        foodCoefficientB,
                        gameSpeed,
                        Controller.getAddress("Wi-Fi"),
                        21212);

                    generateGameField(serverInfo);

                    controller.startServer(playerName, serverInfo);

                    gameRunning = true;
                    updateButtonsState(); // Обновление состояния кнопок
                    updateCurGameInfo(serverInfo);
                } catch (IOException e) {
                    System.err.println("Server create error...");
                    throw new RuntimeException(e);
                }
                // Закрываем форму создания игры
                createGameStage.close();
            }
        });

        // Создаем и настраиваем layout для формы
        VBox layout = new VBox();
        layout.setSpacing(10);
        layout.setPadding(new Insets(10));
        layout.getChildren().addAll(playerNameLabel, playerNameTextField, nameLabel, nameTextField, sizeLabel, widthLabel,
                widthTextField, heightLabel, heightTextField, formulaLabel, coefficientALabel, coefficientATextField,
                coefficientBLabel, coefficientBTextField, gameSpeedLabel, gameSpeedTextField, confirmButton);

        Scene scene = new Scene(layout);
        createGameStage.setScene(scene);
        createGameStage.show();
    }

    private void handleKeyPress(KeyCode code) {
        if (gameRunning) {
            try {
                controller.sendSteerMsg(code);
            } catch (IOException ex) {
                System.err.println("Steer send error!");
                ex.printStackTrace();
            }
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
                } else if ((counter + 1) % 4 < 2) {
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

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void update(Object message, InetAddress address, int port) {
        if (message instanceof GameMessage.StateMsg) {
            GameMessage.StateMsg stateMsg = (GameMessage.StateMsg) message;
            GameLogic.editGameFieldFromState(gameField, stateMsg);
            render();

            GamePlayers gamePlayers = stateMsg.getState().getPlayers();
            updateLeaderboardTable(gamePlayers);
        } else if (message instanceof GameMessage.AnnouncementMsg) {
            GameMessage.AnnouncementMsg announcementMsg = (GameMessage.AnnouncementMsg) message;
            updateServerList(announcementMsg, address, port);
        }
    }
    private void updateLeaderboardTable(GamePlayers gamePlayers) {
        leaderboardTable.getItems().clear();

        for (GamePlayer player : gamePlayers.getPlayersList()) {
            PlayerInfo playerInfo = new PlayerInfo(
                    getPlace(gamePlayers, player),
                    player.getName(),
                    player.getScore()
            );
            leaderboardTable.getItems().add(playerInfo);
        }
        leaderboardTable.refresh();
    }
    private String getPlace(GamePlayers gamePlayers, GamePlayer currentPlayer) {
        int currentPlayerScore = currentPlayer.getScore();
        int place = 1;

        for (GamePlayer player : gamePlayers.getPlayersList()) {
            if (player.getScore() > currentPlayerScore) {
                place++;
            }
        }

        return Integer.toString(place);
    }

    private void updateServerList(GameMessage.AnnouncementMsg announcementMsg, InetAddress address, int port) {
        for (GameAnnouncement gameAnnouncement : announcementMsg.getGamesList()) {
            String gameName = gameAnnouncement.getGameName();
            boolean exists = false;

            // Находим IP и порт мастера
            String masterIp = null;
            int masterPort = -1;
            for (GamePlayer player : gameAnnouncement.getPlayers().getPlayersList()) {
                if (player.getRole() == NodeRole.MASTER) {
                    masterIp = player.getIpAddress();
                    masterPort = player.getPort();
                    break;
                }
            }

            if (masterIp == null || masterPort == -1) {
                // Если мастер не найден, используем адрес и порт отправителя
                masterIp = address.getHostAddress();
                masterPort = port;
            }

            for (ServerInfo server : serverList.getItems()) {
                if (server.serverNameProperty().get().equals(gameName)) {
                    exists = true;
                    updateServerInfo(server, gameAnnouncement, masterIp, masterPort); // Обновляем информацию о сервере
                    resetTimerForServer(gameName); // Сбрасываем таймер
                    break;
                }
            }

            if (!exists) {
                ServerInfo newServer = new ServerInfo(
                        gameName,
                        gameAnnouncement.getPlayers().getPlayersCount(),
                        String.format("%dx%d", gameAnnouncement.getConfig().getWidth(), gameAnnouncement.getConfig().getHeight()),
                        0,
                        gameAnnouncement.getConfig().getFoodStatic(),
                        gameAnnouncement.getConfig().getStateDelayMs(),
                        masterIp,
                        masterPort
                );
                serverList.getItems().add(newServer);
                startTimerForServer(gameName); // Запускаем таймер для нового сервера
            }
        }
    }

    private void updateServerInfo(ServerInfo server, GameAnnouncement gameAnnouncement, String masterIp, int masterPort) {
        Platform.runLater(() -> {
            server.serverNameProperty().set(gameAnnouncement.getGameName());
            server.onlineProperty().set((gameAnnouncement.getPlayers().getPlayersCount()));
            server.areaSizeProperty().set(String.format("%dx%d", gameAnnouncement.getConfig().getWidth(), gameAnnouncement.getConfig().getHeight()));
            server.foodCoefficientAProperty().set(0);
            server.foodCoefficientBProperty().set(gameAnnouncement.getConfig().getFoodStatic());
            server.serverIPProperty().set(masterIp);
            server.serverPortProperty().set(masterPort);
        });
    }


    private void resetTimerForServer(String gameName) {
        Timer existingTimer = serverTimers.get(gameName);
        if (existingTimer != null) {
            existingTimer.cancel();
        }
        startTimerForServer(gameName);
    }

    private void startTimerForServer(String gameName) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    serverList.getItems().removeIf(server -> server.serverNameProperty().get().equals(gameName));
                });
            }
        }, threshold);
        serverTimers.put(gameName, timer);
    }

    private void updateCurGameInfo(ServerInfo serverInfo) {
        Platform.runLater(() -> {
            curGameInfo.getItems().clear();
            curGameInfo.getItems().add(serverInfo);
        });
    }

    private void updateButtonsState() {
        createGameButton.setDisable(gameRunning);
        exitGameButton.setDisable(!gameRunning);
    }
}
