package ru.nsu;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ru.nsu.opentrip.Properties;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class LocationApp extends Application {
    private APIWorker apiWorker = new APIWorker();
    ExecutorService executor = Executors.newFixedThreadPool(3);

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

        AtomicReference<List<Location>> atomicLocations = new AtomicReference<>();
        searchButton.setOnAction(e -> {
            String inputText = locationInput.getText();
            locationInput.clear();
            atomicLocations.set(apiWorker.getLocationsByAddress(inputText));
            List<Location> locations = atomicLocations.get();

            resultList.getItems().clear();

            if (locations.isEmpty()) {
                resultList.getItems().add("Нет результатов");
            } else {
                for (Location location : locations) {
                    resultList.getItems().add(location.getName());
                }
            }
        });

        resultList.setOnMouseClicked(event -> {
            List<Location> locations = atomicLocations.get();
            if (locations.isEmpty()) {
                return;
            }
            if (resultList.getSelectionModel().getSelectedIndex() >= locations.size()) {
                return;
            }
            Location selectedLocation = locations.get(resultList.getSelectionModel().getSelectedIndex());
            String weather = apiWorker.getWeatherByCoordinates(selectedLocation.getLat(), selectedLocation.getLon());
            List<Properties> places = apiWorker.getInterestingPlacesByCoordinates(selectedLocation.getLon() - 0.01, selectedLocation.getLat() - 0.01, selectedLocation.getLon() + 0.01, selectedLocation.getLat() + 0.01);

            resultList.getItems().clear();
            resultList.getItems().add("Погода: " + weather);

            if (!places.isEmpty()) {
                resultList.getItems().add("Интересные места:");
                for (Properties place : places) {
                    if (place.getName() != null) {
                        resultList.getItems().add("- " + place.getName());
                        String info = apiWorker.getInfoAboutPlace(place.getXid());
                        if (info != null) {
                            resultList.getItems().add("-- " + info);
                        }
                    }
                }
            } else {
                resultList.getItems().add("Нет интересных мест");
            }
        });

        vbox.getChildren().addAll(label, locationInput, searchButton, resultList);

        Scene scene = new Scene(vbox, 400, 400);
        primaryStage.setScene(scene);

        primaryStage.show();
    }
}
