module ru.nsu {
    requires javafx.controls;
    requires javafx.fxml;


    opens ru.nsu to javafx.fxml;
    exports ru.nsu;
}