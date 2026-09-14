package ru.university.spreadsheet.ui;

import java.net.URL;

import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Window;

/**
 * Вспомогательные методы пользовательского интерфейса: подключение таблицы стилей
 * и стандартные диалоги сообщений.
 */
final class UiSupport {

    private static final String STYLESHEET = "/styles.css";

    private UiSupport() {
    }

    static void applyStylesheet(Scene scene) {
        URL url = UiSupport.class.getResource(STYLESHEET);
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        }
    }

    static void showError(Window owner, String message) {
        show(owner, Alert.AlertType.ERROR, "Ошибка", message);
    }

    static void showInfo(Window owner, String header, String message) {
        show(owner, Alert.AlertType.INFORMATION, header, message);
    }

    private static void show(Window owner, Alert.AlertType type, String header, String message) {
        Alert alert = new Alert(type, message);
        alert.initOwner(owner);
        alert.setHeaderText(header);
        alert.showAndWait();
    }
}
