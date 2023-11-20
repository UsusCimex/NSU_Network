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
import ru.nsu.SnakeClient;
import ru.nsu.SnakeGame.GameField;
import ru.nsu.SnakeGame.Snake;
import ru.nsu.SnakeServer;
import ru.nsu.SnakesProto.*;
import ru.nsu.patterns.Observer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.*;

public class GameUI extends Application implements Observer {
    private static double CELL_SIZE;
    private String serverIP = "localhost";
    private long threshold = 3000;

    private BorderPane root;
    private GridPane gameGrid;

    private TableView<PlayerInfo> leaderboardTable;

    private TableView<ServerInfo> curGameInfo;

    private TableView<ServerInfo> serverList = new TableView<>();
    private Map<String, Timer> serverTimers = new HashMap<>();

    private GameField gameField = null;
    private boolean running = false;
    private boolean receiveAnnouncementCicle = false;

    MulticastSocket announcementMulticastSocket;

    private SnakeServer server = null;
    private SnakeClient client = null;

    private Button createGameButton;
    private Button exitGameButton;

    @Override
    public void start(Stage stage) {
        // Игровое поле
        gameGrid = new GridPane();
        gameGrid.setPadding(new Insets(10));
        gameGrid.setAlignment(Pos.CENTER);

        root = new BorderPane();
        root.setPrefSize(1400, 1000);

        root.setLeft(gameGrid);
        VBox rightPanel = createRightPanel();
        root.setRight(rightPanel);

        Scene scene = new Scene(root);
        scene.setOnKeyPressed(event -> handleKeyPress(event.getCode()));
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(event -> {
            gameExit();
            Platform.exit();
        });

        // Создадим поток, который будет получать ANNOUNCEMENT сообщения
        receiveAnnouncementCicle = true;
        Thread serverListListener = new Thread(() -> {
            try {
                announcementMulticastSocket = new MulticastSocket(SnakeServer.GAME_MULTICAST_PORT);
                announcementMulticastSocket.joinGroup(InetAddress.getByName(SnakeServer.MULTICAST_ADDRESS));
                while (receiveAnnouncementCicle) {
                    try {
                        byte[] buf = new byte[256];
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        announcementMulticastSocket.receive(packet);

                        byte[] trimmedData = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());

                        GameMessage message = GameMessage.parseFrom(trimmedData);
                        System.err.println("[UI] Get Multicast message: " + message.getTypeCase());
                        if (message.getTypeCase() != GameMessage.TypeCase.ANNOUNCEMENT) continue;

                        updateServerList(message.getAnnouncement(), packet.getAddress(), packet.getPort());
                    } catch (IOException e) {
                        System.err.println("[GameUI] Announcement receive error!");
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        serverListListener.setDaemon(true);
        serverListListener.start();
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
        foodColumn.setCellValueFactory(new PropertyValueFactory<>("food"));
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
        serverFood.setCellValueFactory(new PropertyValueFactory<>("food"));
        serverList.getColumns().addAll(serverLeader, serverOnline, serverAreaSize, serverFood);

        serverList.setRowFactory(tv -> {
            TableRow<ServerInfo> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    ServerInfo clickedRow = row.getItem();
                    if (!running) { // Проверка, что пользователь не находится в игре
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
        result.ifPresent(name -> joinGame(name, serverInfo));
    }

    private void joinGame(String playerName, ServerInfo serverInfo) {
        try {
            if (client != null && running) {
                client.stop();
            }

            String[] numbers = serverInfo.areaSizeProperty().get().split("[^0-9]+");
            int width = Integer.parseInt(numbers[0]);
            int height = Integer.parseInt(numbers[1]);
            adjustCellSize(width, height);

            client = new SnakeClient(serverInfo.serverIPProperty().get(), serverInfo.serverPortProperty().get(), this);
            client.start(playerName);
            running = true;
            updateButtonsState(); // Обновление состояния кнопок
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void adjustCellSize(int fieldWidth, int fieldHeight) {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();

        // Ширина и высота, доступные для игрового поля
        double availableWidth = screenBounds.getWidth() - 300;
        double availableHeight = screenBounds.getHeight() - 100;

        double cellWidth = availableWidth / fieldWidth;
        double cellHeight = availableHeight / fieldHeight;

        CELL_SIZE = Math.min(cellWidth, cellHeight);
    }

    private void gameExit() {
        running = false;
        receiveAnnouncementCicle = false;
        if (announcementMulticastSocket != null) announcementMulticastSocket.close();

        leaderboardTable.getItems().clear();
        curGameInfo.getItems().clear();
        if (gameField != null) clearGameGrid();

        if (server != null) {
            server.stop();
            server = null;
        }
        if (client != null) {
            client.stop();
            client = null;
        }
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
            String playerName = playerNameTextField.getText();
            String gameName = nameTextField.getText();
            int fieldWidth = Integer.parseInt(widthTextField.getText());
            int fieldHeight = Integer.parseInt(heightTextField.getText());
            int foodCoefficientA = Integer.parseInt(coefficientATextField.getText());
            int foodCoefficientB = Integer.parseInt(coefficientBTextField.getText());

            // Создаём игру
            gameField = new GameField(fieldWidth, fieldHeight, foodCoefficientA, foodCoefficientB);
            adjustCellSize(fieldWidth, fieldHeight);
            startServer(gameName, gameField);
            startClient(playerName);

            // Закрываем форму создания игры
            createGameStage.close();
        });

        // Создаем и настраиваем layout для формы
        VBox layout = new VBox();
        layout.setSpacing(10);
        layout.setPadding(new Insets(10));
        layout.getChildren().addAll(playerNameLabel, playerNameTextField, nameLabel, nameTextField, sizeLabel, widthLabel, widthTextField, heightLabel, heightTextField,
                                    formulaLabel, coefficientALabel, coefficientATextField, coefficientBLabel, coefficientBTextField, confirmButton);

        Scene scene = new Scene(layout);
        createGameStage.setScene(scene);
        createGameStage.show();
    }

    private void startServer(String gameName, GameField gameField) {
        try {
            server = new SnakeServer(gameName, 21212, gameField, serverIP);
            server.start();
        } catch (IOException e) {
            System.err.println("Start server exception!");
            throw new RuntimeException(e);
        }
    }

    private void startClient(String playerName) {
        try {
            client = new SnakeClient(serverIP, 21212, this);
            client.start(playerName); // Start the client's network operations
        } catch (IOException e) {
            System.err.println("Start client exception!");
            throw new RuntimeException(e);
        }
    }

    private void handleKeyPress(KeyCode code) {
        if (client != null) {
            try {
                switch (code) {
                    case A, LEFT  -> client.sendSteer(Direction.LEFT);
                    case S, DOWN  -> client.sendSteer(Direction.DOWN);
                    case D, RIGHT -> client.sendSteer(Direction.RIGHT);
                    case W, UP    -> client.sendSteer(Direction.UP);
                }
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

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void update(Object o) {
        if (o instanceof GameMessage.StateMsg) {
            GameMessage.StateMsg stateMsg = (GameMessage.StateMsg) o;
            gameField.setFoods(stateMsg.getState().getFoodsList());
            for (GameState.Snake snake : stateMsg.getState().getSnakesList()) {
                gameField.updateSnake(Snake.parseSnake(snake));
            }
            render();

            GamePlayers gamePlayers = stateMsg.getState().getPlayers();
            updateLeaderboardTable(gamePlayers);
        }
    }
    private void updateLeaderboardTable(GamePlayers gamePlayers) {
        leaderboardTable.getItems().clear();

        // Iterate through the players in the gamePlayers message
        for (GamePlayer player : gamePlayers.getPlayersList()) {
            int playerId = player.getId();

            // Update the PlayerInfo object with the player's information
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

            for (ServerInfo server : serverList.getItems()) {
                if (server.serverNameProperty().get().equals(gameName)) {
                    exists = true;
                    updateServerInfo(server, gameAnnouncement); // Обновляем информацию о сервере
                    resetTimerForServer(gameName); // Сбрасываем таймер
                    break;
                }
            }

            if (!exists) {
                ServerInfo newServer = new ServerInfo(
                        gameName,
                        gameAnnouncement.getPlayers().getPlayersCount(),
                        String.format("%dx%d", gameAnnouncement.getConfig().getWidth(), gameAnnouncement.getConfig().getHeight()),
                        gameAnnouncement.getConfig().getFoodStatic(),
                        address.getHostAddress(),
                        port
                );
                serverList.getItems().add(newServer);
                startTimerForServer(gameName); // Запускаем таймер для нового сервера
            }
        }
    }

    private void updateServerInfo(ServerInfo server, GameAnnouncement gameAnnouncement) {
        Platform.runLater(() -> {
            server.serverNameProperty().set(gameAnnouncement.getGameName());
            server.onlineProperty().set((gameAnnouncement.getPlayers().getPlayersCount()));
            server.areaSizeProperty().set(String.format("%dx%d", gameAnnouncement.getConfig().getWidth(), gameAnnouncement.getConfig().getHeight()));
            server.foodProperty().set(gameAnnouncement.getConfig().getFoodStatic());
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

    private void updateButtonsState() {
        createGameButton.setDisable(running);
        exitGameButton.setDisable(!running);
    }
}
