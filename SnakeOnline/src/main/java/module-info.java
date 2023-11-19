module ru.nsu {
    requires javafx.controls;
    requires javafx.fxml;
    requires protobuf.java;

    opens ru.nsu to javafx.fxml;
    exports ru.nsu.SnakeGame;
    exports ru.nsu;
    exports ru.nsu.UI;
}