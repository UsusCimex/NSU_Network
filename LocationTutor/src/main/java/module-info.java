module ru.nsu {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires okhttp3;

    opens ru.nsu to javafx.fxml;
    exports ru.nsu;
    exports ru.nsu.geocode;
    exports ru.nsu.openweather;
    exports ru.nsu.opentrip;
    exports ru.nsu.opentripinfo;
    opens ru.nsu.geocode to javafx.fxml;
}
