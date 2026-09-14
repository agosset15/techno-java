package ru.university.spreadsheet.ui;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import ru.university.spreadsheet.model.Role;
import ru.university.spreadsheet.model.User;
import ru.university.spreadsheet.service.AuthService;
import ru.university.spreadsheet.service.ServiceException;

/**
 * Окно входа и регистрации пользователя.
 * <p>
 * Нажатие Enter в поле пароля равносильно нажатию кнопки «Войти». Ошибки выводятся
 * в строке сообщений под полями ввода. После успешного входа вызывается обработчик
 * {@code onSuccess}, а окно входа закрывается.
 */
public class LoginView {

    private static final Logger LOG = Logger.getLogger(LoginView.class.getName());
    private static final double FIELD_WIDTH = 240;

    private final Stage stage;
    private final AuthService authService;
    private final Consumer<User> onSuccess;

    private final TextField loginField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Label messageLabel = new Label();

    /**
     * Создаёт окно входа.
     *
     * @param stage       окно, в котором отображается форма
     * @param authService сервис аутентификации
     * @param onSuccess   обработчик успешного входа, получающий вошедшего пользователя
     */
    public LoginView(Stage stage, AuthService authService, Consumer<User> onSuccess) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.onSuccess = Objects.requireNonNull(onSuccess, "onSuccess");
    }

    /**
     * Отображает форму входа.
     */
    public void show() {
        Label title = new Label("Электронная таблица");
        title.getStyleClass().add("login-title");

        loginField.setId("loginField");
        loginField.setPromptText("латинские буквы, цифры, _");
        loginField.setPrefWidth(FIELD_WIDTH);
        loginField.setOnAction(event -> passwordField.requestFocus());

        passwordField.setId("passwordField");
        passwordField.setPromptText("не менее " + AuthService.MIN_PASSWORD_LENGTH + " символов");
        passwordField.setOnAction(event -> onLogin());

        Button loginButton = new Button("Войти");
        loginButton.setId("loginButton");
        loginButton.setOnAction(event -> onLogin());
        Button registerButton = new Button("Зарегистрироваться");
        registerButton.setId("registerButton");
        registerButton.setOnAction(event -> onRegister());
        HBox buttons = new HBox(10, registerButton, loginButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        messageLabel.setId("messageLabel");
        messageLabel.getStyleClass().add("message-label");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(FIELD_WIDTH + 80);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(24));
        grid.add(title, 0, 0, 2, 1);
        grid.add(new Label("Логин:"), 0, 1);
        grid.add(loginField, 1, 1);
        grid.add(new Label("Пароль:"), 0, 2);
        grid.add(passwordField, 1, 2);
        grid.add(buttons, 0, 3, 2, 1);
        grid.add(messageLabel, 0, 4, 2, 1);

        Scene scene = new Scene(grid);
        UiSupport.applyStylesheet(scene);
        stage.setScene(scene);
        stage.setTitle("Вход — Электронная таблица");
        stage.setResizable(false);
        stage.show();
        loginField.requestFocus();
    }

    private void onLogin() {
        try {
            User user = authService.login(loginField.getText(), passwordField.getText());
            passwordField.clear();
            showMessage("", false);
            onSuccess.accept(user);
            stage.close();
        } catch (ServiceException e) {
            passwordField.clear();
            passwordField.requestFocus();
            showMessage(e.getMessage(), true);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Ошибка входа", e);
            showMessage("Техническая ошибка: " + e.getMessage(), true);
        }
    }

    private void onRegister() {
        try {
            User user = authService.register(loginField.getText(), passwordField.getText());
            passwordField.clear();
            passwordField.requestFocus();
            String role = user.getRole() == Role.ADMIN ? " с правами администратора" : "";
            showMessage("Пользователь «" + user.getLogin() + "» зарегистрирован" + role + ". Выполните вход.", false);
        } catch (ServiceException e) {
            showMessage(e.getMessage(), true);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Ошибка регистрации", e);
            showMessage("Техническая ошибка: " + e.getMessage(), true);
        }
    }

    private void showMessage(String text, boolean error) {
        messageLabel.setText(text);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
        stage.sizeToScene();
    }
}
