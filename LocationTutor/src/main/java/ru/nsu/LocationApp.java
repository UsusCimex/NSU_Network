package ru.nsu;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ru.nsu.opentrip.Properties;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class LocationApp extends Application {
    private APIWorker apiWorker = new APIWorker();
    Semaphore semaphore = new Semaphore(1);
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Location Search App");

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        Label label = new Label("Введите название местоположения:");
        TextField locationInput = new TextField();
        Button searchButton = new Button("Поиск");

        ListView<String> resultList = new ListView<>();
        resultList.setPrefHeight(200);

        searchButton.setOnAction(e -> {
            String inputText = locationInput.getText();
            locationInput.clear();

            // Асинхронный вызов для получения локаций по адресу
            CompletableFuture<List<Location>> locationFuture = CompletableFuture.supplyAsync(() -> {
                return apiWorker.getLocationsByAddress(inputText);
            });

            locationFuture.thenAccept(locations -> {
                Platform.runLater(() -> {
                    resultList.getItems().clear();

                    if (locations.isEmpty()) {
                        resultList.getItems().add("Нет результатов");
                    } else {
                        for (Location location : locations) {
                            resultList.getItems().add(location.getName());
                        }
                    }
                });
            });

            // Обработчик щелчка на элементе resultList
            resultList.setOnMouseClicked(event -> {
                List<Location> locations = locationFuture.join();
                if (locations.isEmpty()) {
                    return;
                }
                int selectedIndex = resultList.getSelectionModel().getSelectedIndex();
                if (selectedIndex >= 0 && selectedIndex < locations.size()) {
                    Location selectedLocation = locations.get(selectedIndex);

                    // Асинхронный вызов для получения информации о выбранном месте
                    CompletableFuture<Void> infoFuture = CompletableFuture.runAsync(() -> {
                        String weather = apiWorker.getWeatherByCoordinates(selectedLocation.getLat(), selectedLocation.getLon());
                        List<Properties> places = apiWorker.getInterestingPlacesByCoordinates(
                                selectedLocation.getLon() - 0.01, selectedLocation.getLat() - 0.01,
                                selectedLocation.getLon() + 0.01, selectedLocation.getLat() + 0.01
                        );

                        Platform.runLater(() -> {
                            resultList.getItems().clear();
                            resultList.getItems().add("Погода: " + weather);
                            long delayMillis = 500; //Задержка между запросами, 0.5 сек

                            if (!places.isEmpty()) {
                                resultList.getItems().add("Интересные места:");
                                for (Properties place : places) {
                                    if (place.getName() != null) {
                                        // Асинхронный вызов для получения информации о месте
                                        CompletableFuture<Void> placeInfoFuture = CompletableFuture.runAsync(() -> {
                                            try {
                                                semaphore.acquire();
                                                String placeInfo = "- " + place.getName();

                                                try {
                                                    Thread.sleep(delayMillis);
                                                } catch (InterruptedException ex) {
                                                    ex.printStackTrace();
                                                }
                                                String info = apiWorker.getInfoAboutPlace(place.getXid());
                                                if (info != null) {
                                                    placeInfo += "\n-- " + info;
                                                }
                                                String finalPlaceInfo = placeInfo;
                                                Platform.runLater(() -> {
                                                    resultList.getItems().add(finalPlaceInfo); }
                                                );
                                            } catch (InterruptedException ex) {
                                                ex.printStackTrace();
                                            } finally {
                                                semaphore.release();
                                            }
                                        });
                                    }
                                }
                            } else {
                                resultList.getItems().add("Нет интересных мест");
                            }
                        });
                    });
                }
            });
        });

        vbox.getChildren().addAll(label, locationInput, searchButton, resultList);

        Scene scene = new Scene(vbox, 400, 400);
        primaryStage.setScene(scene);

        primaryStage.show();
    }
}
