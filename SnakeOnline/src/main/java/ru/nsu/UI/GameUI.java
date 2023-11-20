package ru.nsu.UI;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
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
    private static final int CELL_SIZE = 15;
    private long threshold = 3000;
    private long announcementDelayMS = 1000;
    private long stateDelayMS = 1000;

    private BorderPane root;
    private GridPane gameGrid;

    private TableView<PlayerInfo> leaderboardTable;

    private TableView<ServerInfo> curGameInfo;

    private TableView<ServerInfo> serverList = new TableView<>();
    private Map<String, Timer> serverTimers = new HashMap<>();

    private GameField gameField = null;
    private boolean running = false;

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
            gameExit();
            clientWorking = false;
            Platform.exit();
        });

        running = true;
        Thread serverListListener = new Thread(() -> {
            MulticastSocket multicastSocket = null;
            try {
                multicastSocket = new MulticastSocket(SnakeServer.GAME_MULTICAST_PORT);
                multicastSocket.joinGroup(InetAddress.getByName(SnakeServer.MULTICAST_ADDRESS));
                while (running) {
                    try {
                        byte[] buf = new byte[256];
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        multicastSocket.receive(packet);

                        byte[] trimmedData = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());

                        GameMessage message = GameMessage.parseFrom(trimmedData);
                        System.err.println("Get Multicast message: " + message.getTypeCase());
                        if (message.getTypeCase() != GameMessage.TypeCase.ANNOUNCEMENT) continue;
                        update(message.getAnnouncement());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
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

        Button createGameButton = new Button("Create Game");
        createGameButton.setPrefSize(200, 100);
        createGameButton.setOnAction(event -> openCreateGameForm());
        Button exitGameButton = new Button("Exit Game");
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

        vbox.getChildren().add(serverList);

        return vbox;
    }

    private void gameExit() {
        serverWorking = false;
        if (server != null) {
            server.stopGameLoop();
            server = null;
        }

        clientWorking = false;
        client = null;
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
            try {
                createServer(gameName, 21212);
                createClient(playerName, SnakeServer.MULTICAST_ADDRESS, SnakeServer.CLIENT_MULTICAST_PORT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

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

    private void createServer(String gameName, int port) throws IOException {
        server = new SnakeServer(gameName, port, gameField);
        System.err.println("Server started: localhost:" + port);
        serverWorking = true;

        Thread serverListener = new Thread(() -> {
            while (serverWorking) {
                try {
                    System.err.println("[Server] Listen...");
                    server.receiveMessage();
                } catch (IOException e) {
                    System.err.println("Receive message error!");
                    gameExit();
                    throw new RuntimeException(e);
                }
            }
        });

        Thread announcementThread = new Thread(() -> {
            while (serverWorking) {
                try {
                    server.sendAnnouncement(InetAddress.getByName(SnakeServer.MULTICAST_ADDRESS), SnakeServer.GAME_MULTICAST_PORT);
                    Thread.sleep(announcementDelayMS);
                } catch (InterruptedException | IOException e) {
                    System.err.println("Announcement send error!");
                    gameExit();
                    throw new RuntimeException(e);
                }
            }
        });

        Thread stateThread = new Thread(() -> {
            while (serverWorking) {
                try {
                    server.sendState(InetAddress.getByName(SnakeServer.MULTICAST_ADDRESS), SnakeServer.CLIENT_MULTICAST_PORT);
                    Thread.sleep(stateDelayMS);
                } catch (InterruptedException | IOException e) {
                    System.err.println("State send error!");
                    gameExit();
                    throw new RuntimeException(e);
                }
            }
        });

        serverListener.start();
        announcementThread.start();
        stateThread.start();
    }

    private void createClient(String name, String address, int port) throws IOException {
        client = new SnakeClient(address, port, this);
        client.sendJoinRequest(name);
        System.err.println("[Client] started: " + address + ":" + port);

        clientWorking = true;
        Thread clientThread = new Thread(() -> {
            while (clientWorking) {
                try {
                    System.err.println("[Client] Listen...");
                    client.receiveMessage();
                } catch (IOException ex) {
                    clientWorking = false;
                    ex.printStackTrace();
                }
            }
        });
        clientThread.start();
    }

    private void handleKeyPress(KeyCode code) {
        if (clientWorking) {
            try {
                switch (code) {
                    case A, LEFT  -> client.sendSteer(Direction.LEFT);
                    case S, DOWN  -> client.sendSteer(Direction.DOWN);
                    case D, RIGHT -> client.sendSteer(Direction.RIGHT);
                    case W, UP    -> client.sendSteer(Direction.UP);
                }
            } catch (IOException ex) {
                System.err.println("Steer send error!");
                clientWorking = false;
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
                gameField.getSnakes().clear();
                gameField.addSnake(Snake.parseSnake(snake));
            }
            render();

            GamePlayers gamePlayers = stateMsg.getState().getPlayers();
            updateLeaderboardTable(gamePlayers);
        } else if (o instanceof GameMessage.AnnouncementMsg) {
            GameMessage.AnnouncementMsg announcementMsg = (GameMessage.AnnouncementMsg) o;
            updateServerList(announcementMsg);
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

    private void updateServerList(GameMessage.AnnouncementMsg announcementMsg) {
        for (GameAnnouncement gameAnnouncement : announcementMsg.getGamesList()) {
            String gameName = gameAnnouncement.getGameName();

            // Check if the game is already in the list
            boolean exists = false;
            for (ServerInfo server : serverList.getItems()) {
                if (server.serverNameProperty().get().equals(gameName)) {
                    exists = true;
                    resetTimerForServer(gameName);
                    break;
                }
            }

            if (!exists) {
                serverList.getItems().add(new ServerInfo(gameName,
                        gameAnnouncement.getPlayers().getPlayersCount(),
                        String.format("%dx%d", gameAnnouncement.getConfig().getWidth(),gameAnnouncement.getConfig().getHeight()),
                        gameAnnouncement.getConfig().getFoodStatic()));
                startTimerForServer(gameName);
            }
        }
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
        }, 3000);
        serverTimers.put(gameName, timer);
    }
}
