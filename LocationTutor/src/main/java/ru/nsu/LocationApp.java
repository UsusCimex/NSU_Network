package ru.nsu;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ru.nsu.opentrip.Properties;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class LocationApp extends Application {
    private final APIWorker apiWorker = new APIWorker();
    private Button searchButton;
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Location Search App");

        VBox vbox = createUI();

        Scene scene = new Scene(vbox, 400, 400);
        primaryStage.setScene(scene);

        primaryStage.show();
    }

    private VBox createUI() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        Label label = new Label("Введите название местоположения:");
        TextField locationInput = new TextField();
        searchButton = new Button("Поиск");

        ListView<String> resultList = new ListView<>();
        resultList.setPrefHeight(200);

        searchButton.setOnAction(e -> handleSearchButton(locationInput, resultList));

        vbox.getChildren().addAll(label, locationInput, searchButton, resultList);

        return vbox;
    }

    private void handleSearchButton(TextField locationInput, ListView<String> resultList) {
        // Очистка и обработка запроса
        String inputText = locationInput.getText();
        Platform.runLater(() -> resultList.getItems().clear());
        searchButton.setDisable(true);
        resultList.setOnMouseClicked(null);

        CompletableFuture.supplyAsync(() -> apiWorker.getLocationsByAddress(inputText))
                .thenAccept(locations -> {
                    Platform.runLater(() -> {
                                if (locations.isEmpty()) {
                                    resultList.getItems().add("Нет результатов");
                                } else {
                                    for (Location location : locations) {
                                        resultList.getItems().add(location.getName());
                                    }
                                }
                            });
                    searchButton.setDisable(false);

                    // Обработка щелчка на элементе resultList
                    resultList.setOnMouseClicked(event -> handleResultListClick(locations, resultList));
                });
    }

    private void handleResultListClick(List<Location> locations, ListView<String> resultList) {
        resultList.setOnMouseClicked(null);
        searchButton.setDisable(true);
        int selectedIndex = resultList.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= locations.size()) {
            return;
        }

        Location selectedLocation = locations.get(selectedIndex);

        CompletableFuture<String> weatherFuture = CompletableFuture.supplyAsync(() ->
                apiWorker.getWeatherByCoordinates(selectedLocation.getLat(), selectedLocation.getLon())
        );

        CompletableFuture<List<Properties>> placesFuture = CompletableFuture.supplyAsync(() ->
                apiWorker.getInterestingPlacesByCoordinates(
                        selectedLocation.getLon() - 0.01, selectedLocation.getLat() - 0.01,
                        selectedLocation.getLon() + 0.01, selectedLocation.getLat() + 0.01
                )
        );

        CompletableFuture.allOf(weatherFuture, placesFuture)
                .thenAcceptAsync(ignored -> handleWeatherAndPlaces(weatherFuture.join(), placesFuture.join(), resultList));
    }

    private void handleWeatherAndPlaces(String weather, List<Properties> places, ListView<String> resultList) {
        Platform.runLater(() -> {
            resultList.getItems().clear();
            resultList.getItems().add("Погода: " + weather);
        });

        if (!places.isEmpty()) {
            Platform.runLater(() -> resultList.getItems().add("Интересные места:"));
            ExecutorService executor = Executors.newFixedThreadPool(5);

            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (Properties place : places) {
                if (place.getName() != null && !place.getName().isEmpty()) {
                    CompletableFuture<String> placeInfoFuture = CompletableFuture.supplyAsync(() -> {
                        String placeInfo = "- " + place.getName();
                        try {
                            Thread.sleep(1000); // Задержка между запросами, 1 сек
                            String info = apiWorker.getInfoAboutPlace(place.getXid());
                            if (info != null) {
                                placeInfo += "\n-- " + info;
                            }
                            return placeInfo;
                        } catch (Exception e) {
                            e.printStackTrace();
                            return null;
                        }
                    }, executor);
                    placeInfoFuture.thenAccept(placeInfo -> {
                        if (placeInfo != null && !placeInfo.isEmpty()) {
                            Platform.runLater(() -> resultList.getItems().add(placeInfo));
                        }
                    });
                    futures.add(placeInfoFuture);
                }
            }

            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            allOf.thenRun(() -> {
                // Все CompletableFuture завершились
                executor.shutdown();
                searchButton.setDisable(false);
            });
        } else {
            Platform.runLater(() -> resultList.getItems().add("Нет интересных мест"));
        }
    }
}