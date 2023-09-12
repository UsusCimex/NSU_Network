package ru.nsu;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ru.nsu.opentrip.Properties;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class LocationApp extends Application {

    private APIWorker apiWorker = new APIWorker();

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

        AtomicReference<List<Location>> locations = new AtomicReference<>();
        searchButton.setOnAction(e -> {
            String inputText = locationInput.getText();
            locationInput.clear();
            locations.set(apiWorker.getLocationsByAddress(inputText));

            resultList.getItems().clear();

            if (locations.get().isEmpty()) {
                resultList.getItems().add("Нет результатов");
            } else {
                for (Location location : locations.get()) {
                    resultList.getItems().add(location.getName());
                }
            }
        });

        resultList.setOnMouseClicked(event -> {
            if (locations.get().isEmpty()) {
                return;
            }
            if (resultList.getSelectionModel().getSelectedIndex() >= locations.get().size()) {
                return;
            }
            Location selectedLocation = locations.get().get(resultList.getSelectionModel().getSelectedIndex());
            locations.set(new ArrayList<>());
            String weather = apiWorker.getWeatherByCoordinates(selectedLocation.getLat(), selectedLocation.getLon());
            List<Properties> places = apiWorker.getInterestingPlacesByCoordinates(selectedLocation.getLon() - 0.01, selectedLocation.getLat() - 0.01, selectedLocation.getLon() + 0.01, selectedLocation.getLat() + 0.01);

            resultList.getItems().clear();
            resultList.getItems().add("Погода: " + weather);

            if (!places.isEmpty()) {
                resultList.getItems().add("Интересные места:");
                for (Properties place : places) {
                    resultList.getItems().add("- " + place.getName());
                    String info = apiWorker.getInfoAboutPlace(place.getXid());
                    if (info != null) {
                        resultList.getItems().add("-- " + info);
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
