package ru.nsu;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import okhttp3.*;
import ru.nsu.geocode.GeoData;
import ru.nsu.opentrip.FeatureData;
import ru.nsu.opentrip.Properties;
import ru.nsu.opentripinfo.FeatureInfoData;
import ru.nsu.openweather.WeatherData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LocationApp extends Application {
    private OkHttpClient client;
    private APIWorker apiWorker;
    private Button searchButton;
    private ListView<String> resultList;
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Location Search App");

        client = new OkHttpClient();
        apiWorker = new APIWorker(client);

        VBox vbox = createUI();

        Scene scene = new Scene(vbox, 400, 400);
        primaryStage.setScene(scene);

        primaryStage.setOnCloseRequest(event -> {
            try {
                // Вызов close() при закрытии окна приложения
                if (client != null) {
                    client.dispatcher().executorService().shutdown();
                    client.connectionPool().evictAll();
                    if (client.cache() != null) {
                        client.cache().close();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        primaryStage.show();
    }

    private VBox createUI() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        Label label = new Label("Введите название местоположения:");
        TextField locationInput = new TextField();
        searchButton = new Button("Поиск");
        resultList = new ListView<>();

        resultList.setPrefHeight(200);

        searchButton.setOnAction(e -> handleSearchButton(locationInput));

        vbox.getChildren().addAll(label, locationInput, searchButton, resultList);

        return vbox;
    }

    private void handleSearchButton(TextField locationInput) {
        // Очистка и обработка запроса
        String inputText = locationInput.getText();
        Platform.runLater(() -> resultList.getItems().clear());
        searchButton.setDisable(true);
        resultList.setOnMouseClicked(null);

        Callback locationsCallback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseString = response.body().string();
                        List<Location> locations = GeoData.parseJSON(responseString);

                        Platform.runLater(() -> {
                            resultList.getItems().clear();
                            if (locations.isEmpty()) {
                                resultList.getItems().add("Нет результатов");
                            } else {
                                for (Location location : locations) {
                                    resultList.getItems().add(location.getName());
                                }
                            }
                            searchButton.setDisable(false);

                            // Обработка щелчка на элементе resultList
                            resultList.setOnMouseClicked(event -> handleResultListClick(locations));
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    // Обработка неуспешного ответа
                    Platform.runLater(() -> {
                        resultList.getItems().clear();
                        resultList.getItems().add("Ошибка: " + response.code());
                        searchButton.setDisable(false);
                    });
                }
            }
        };

        apiWorker.getLocationsByAddress(inputText, locationsCallback);
    }

    private void handleResultListClick(List<Location> locations) {
        resultList.setOnMouseClicked(null); // Отключаем обработчик, чтобы избежать множественных кликов
        searchButton.setDisable(true);

        int selectedIndex = resultList.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= locations.size()) {
            searchButton.setDisable(false);
            return;
        }

        Location selectedLocation = locations.get(selectedIndex);

        Callback weatherCallback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseString = response.body().string();
                        String weather = WeatherData.parseJSON(responseString);

                        Platform.runLater(() -> {
                            resultList.getItems().clear();
                            resultList.getItems().add("Погода: " + weather);
                        });

                        handlePlaces(selectedLocation);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    // Обработка неуспешного ответа
                    Platform.runLater(() -> {
                        resultList.getItems().clear();
                        resultList.getItems().add("Ошибка при получении погоды: " + response.code());
                    });

                    handlePlaces(selectedLocation);
                }
            }
        };

        apiWorker.getWeatherByCoordinates(selectedLocation.getLat(), selectedLocation.getLon(), weatherCallback);
    }

    private void handlePlaces(Location location) {
        Callback placesCallback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    resultList.getItems().add("Ошибка при получении интересных мест");
                    searchButton.setDisable(false);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseString = response.body().string();
                        List<Properties> places = FeatureData.parseJSON(responseString);

                        int countPlaces = 5;
                        places = places.subList(0, countPlaces); //Ограничиваем список мест до 5
                        ArrayList<CompletableFuture<String>> futures = new ArrayList<>(countPlaces);

                        Platform.runLater( () -> resultList.getItems().add("Интересные места:") );
                        for (Properties place : places) {
                            CompletableFuture<String> future = new CompletableFuture<>();
                            if (place.getName() != null && !place.getName().isEmpty()) {
                                handlePlaceInfo(place, future);
                            }
                            futures.add(future);
                        }
                        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                        allOf.join();

                        for (CompletableFuture<String> future : futures) {
                            future.thenAccept(placeInfo -> Platform.runLater(() -> resultList.getItems().add(placeInfo)));
                        }

                        searchButton.setDisable(false);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    // Обработка неуспешного ответа
                    Platform.runLater(() -> {
                        resultList.getItems().add("Ошибка при получении интересных мест: " + response.code());
                        searchButton.setDisable(false);
                    });
                }
            }
        };

        apiWorker.getInterestingPlacesByCoordinates(
                location.getLon() - 0.01, location.getLat() - 0.01,
                location.getLon() + 0.01, location.getLat() + 0.01,
                placesCallback
        );
    }

    private void handlePlaceInfo(Properties place, CompletableFuture<String> future) {
        Callback placeCallback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                System.err.println("[FAIL] handlePlaceInfo");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseString = response.body().string();
                        String placeInfo = FeatureInfoData.parseJSON(responseString);
                        future.complete(placeInfo);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.err.println("[RESPONSE_ERROR] Failed with code: " + response.code());
                }
            }
        };

        apiWorker.getInfoAboutPlace(place.getXid(), placeCallback);
    }
}