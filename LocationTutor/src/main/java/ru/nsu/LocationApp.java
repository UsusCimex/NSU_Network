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
import java.util.concurrent.ExecutionException;

public class LocationApp extends Application {
    private OkHttpClient client;
    private APIWorker apiWorker;
    private Button searchButton;
    private ListView<String> resultList;
    private static int MAX_PLACE_COUNT = 5;
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
        resultList.getItems().clear();

        ArrayList<CompletableFuture<String>> futures = new ArrayList<>();
        futures.add(new CompletableFuture<String>());

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

                        futures.get(0).complete("Погода: " + weather);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    futures.get(0).complete("Ошибка при получении погоды, код ошибки: " + response.code());
                }
            }
        };

        apiWorker.getWeatherByCoordinates(selectedLocation.getLat(), selectedLocation.getLon(), weatherCallback);

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

                        futures.add(new CompletableFuture<>());
                        futures.get(1).complete("Интересные места:");

                        int placeCount = 0;
                        for (Properties place : places) {
                            if (place.getName() != null && !place.getName().isEmpty()) {
                                CompletableFuture<String> future = new CompletableFuture<>();
                                handlePlaceInfo(place, future);
                                futures.add(future);
                                placeCount++;
                                if (placeCount >= MAX_PLACE_COUNT) break;
                            }
                        }

                        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                        allOf.thenAccept(voidResult -> {
                            for (CompletableFuture<String> future : futures) {
                                try {
                                    String text = future.get(); // ожидание завершения всех CompletableFuture
                                    Platform.runLater(() -> resultList.getItems().add(text));
                                } catch (InterruptedException | ExecutionException e) {
                                    System.err.println("CompletableFuture error!...");
                                    Platform.runLater(() -> {
                                        resultList.getItems().clear();
                                        resultList.getItems().add("Ошибка при получении мест...");
                                    });
                                }
                            }

                            searchButton.setDisable(false);
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    // Обработка неуспешного ответа
                    Platform.runLater(() -> {
                        resultList.getItems().add("Ошибка при получении интересных мест, код ошибки: " + response.code());
                        searchButton.setDisable(false);
                    });
                }
            }
        };

        apiWorker.getInterestingPlacesByCoordinates(
                selectedLocation.getLon() - 0.01, selectedLocation.getLat() - 0.01,
                selectedLocation.getLon() + 0.01, selectedLocation.getLat() + 0.01,
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