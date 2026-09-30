package ru.university.spreadsheet;

import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import ru.university.spreadsheet.dao.Database;
import ru.university.spreadsheet.service.AuthService;
import ru.university.spreadsheet.service.DocumentService;
import ru.university.spreadsheet.service.StyleService;
import ru.university.spreadsheet.ui.LoginView;
import ru.university.spreadsheet.ui.MainView;

/**
 * Точка входа JavaFX-приложения «Электронная таблица».
 * <p>
 * При запуске выполняет миграцию схемы базы данных, создаёт сервисы и показывает окно
 * входа. После успешного входа открывается главное окно; выход из учётной записи
 * возвращает к окну входа. При завершении приложения соединение с базой данных закрывается.
 */
public class App extends Application {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    private Stage loginStage;
    private AuthService authService;
    private DocumentService documentService;
    private StyleService styleService;

    /**
     * Запускает приложение: миграция БД и показ окна входа.
     *
     * @param stage основное окно, используемое для формы входа
     */
    @Override
    public void start(Stage stage) {
        try {
            Database.getInstance().migrate();
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Не удалось подготовить базу данных", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage());
            alert.setHeaderText("Не удалось подготовить базу данных");
            alert.showAndWait();
            Platform.exit();
            return;
        }
        loginStage = stage;
        authService = new AuthService();
        documentService = new DocumentService(authService);
        styleService = new StyleService();
        stage.setOnCloseRequest(event -> Database.getInstance().close());
        showLogin();
    }

    /**
     * Закрывает соединение с базой данных при завершении приложения.
     */
    @Override
    public void stop() {
        Database.getInstance().close();
    }

    /**
     * Запускает JavaFX-приложение.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        launch(args);
    }

    private void showLogin() {
        new LoginView(loginStage, authService, user -> showMain()).show();
    }

    private void showMain() {
        Stage mainStage = new Stage();
        MainView mainView = new MainView(mainStage, documentService, authService, styleService, () -> {
            authService.logout();
            showLogin();
            mainStage.close();
        });
        mainView.show();
    }
}
