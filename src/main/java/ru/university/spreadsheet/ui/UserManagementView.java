package ru.university.spreadsheet.ui;

import java.util.Objects;
import java.util.Optional;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import ru.university.spreadsheet.model.Role;
import ru.university.spreadsheet.model.User;
import ru.university.spreadsheet.service.ServiceException;
import ru.university.spreadsheet.service.UserManagementService;

/**
 * Окно управления учётными записями, доступное администратору.
 * <p>
 * Показывает список пользователей и позволяет добавить учётную запись, изменить роль
 * или удалить пользователя. Нарушения правил, например попытка удалить последнего
 * администратора, выводятся в окне с сообщением об ошибке.
 */
public class UserManagementView {

    private static final double WINDOW_WIDTH = 520;
    private static final double WINDOW_HEIGHT = 380;

    private final Window owner;
    private final UserManagementService service;
    private final TableView<User> table = new TableView<>();
    private final Stage stage = new Stage();

    /**
     * Создаёт окно управления пользователями.
     *
     * @param owner   окно-владелец
     * @param service сервис управления пользователями
     */
    public UserManagementView(Window owner, UserManagementService service) {
        this.owner = owner;
        this.service = Objects.requireNonNull(service, "service");
    }

    /**
     * Показывает окно и загружает список пользователей.
     */
    public void show() {
        TableColumn<User, String> loginColumn = new TableColumn<>("Логин");
        loginColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getLogin()));
        loginColumn.setPrefWidth(260);
        TableColumn<User, String> roleColumn = new TableColumn<>("Роль");
        roleColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getRole().name()));
        roleColumn.setPrefWidth(160);
        table.getColumns().add(loginColumn);
        table.getColumns().add(roleColumn);
        table.setId("usersTable");
        table.setPlaceholder(new Label("Список пуст"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button add = button("Добавить…", "addUserButton", this::onAdd);
        Button changeRole = button("Изменить роль…", "changeRoleButton", this::onChangeRole);
        Button delete = button("Удалить", "deleteUserButton", this::onDelete);
        Button close = button("Закрыть", "closeButton", stage::close);
        changeRole.disableProperty().bind(Bindings.isNull(table.getSelectionModel().selectedItemProperty()));
        delete.disableProperty().bind(Bindings.isNull(table.getSelectionModel().selectedItemProperty()));

        HBox buttons = new HBox(8, add, changeRole, delete, close);
        buttons.setPadding(new Insets(10, 0, 0, 0));
        BorderPane root = new BorderPane(table);
        root.setBottom(buttons);
        root.setPadding(new Insets(12));

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        UiSupport.applyStylesheet(scene);
        stage.setScene(scene);
        stage.setTitle("Управление пользователями");
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        reload();
        stage.show();
    }

    private void reload() {
        run(() -> table.setItems(FXCollections.observableArrayList(service.listUsers())));
    }

    private void onAdd() {
        newUserDialog().ifPresent(request -> run(() -> {
            service.createUser(request.login(), request.password(), request.role());
            reload();
        }));
    }

    private void onChangeRole() {
        User selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        ChoiceDialog<Role> dialog = new ChoiceDialog<>(selected.getRole(), Role.values());
        dialog.initOwner(stage);
        dialog.setTitle("Изменение роли");
        dialog.setHeaderText("Роль пользователя «" + selected.getLogin() + "»");
        dialog.setContentText("Роль:");
        dialog.getDialogPane().getButtonTypes().setAll(
                new ButtonType("ОК", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.showAndWait().ifPresent(role -> run(() -> {
            service.changeRole(selected.getId(), role);
            reload();
        }));
    }

    private void onDelete() {
        User selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        ButtonType yes = new ButtonType("Удалить", ButtonBar.ButtonData.YES);
        ButtonType no = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", yes, no);
        alert.initOwner(stage);
        alert.setTitle("Удаление пользователя");
        alert.setHeaderText("Удалить пользователя «" + selected.getLogin() + "»?");
        alert.setContentText("Вместе с учётной записью будут удалены её документы.");
        if (alert.showAndWait().filter(yes::equals).isPresent()) {
            run(() -> {
                service.deleteUser(selected.getId());
                reload();
            });
        }
    }

    private Optional<NewUser> newUserDialog() {
        Dialog<NewUser> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Новый пользователь");
        dialog.setHeaderText("Введите данные учётной записи");
        ButtonType okType = new ButtonType("Создать", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(okType,
                new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE));

        TextField login = new TextField();
        login.setId("newUserLogin");
        PasswordField password = new PasswordField();
        password.setId("newUserPassword");
        ChoiceBox<Role> role = new ChoiceBox<>(FXCollections.observableArrayList(Role.values()));
        role.setId("newUserRole");
        role.setValue(Role.USER);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Логин:"), 0, 0);
        grid.add(login, 1, 0);
        grid.add(new Label("Пароль:"), 0, 1);
        grid.add(password, 1, 1);
        grid.add(new Label("Роль:"), 0, 2);
        grid.add(role, 1, 2);
        dialog.getDialogPane().setContent(grid);

        Node okButton = dialog.getDialogPane().lookupButton(okType);
        okButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> login.getText().isBlank() || password.getText().isBlank(),
                login.textProperty(), password.textProperty()));
        dialog.setResultConverter(button -> button == okType
                ? new NewUser(login.getText(), password.getText(), role.getValue())
                : null);
        return dialog.showAndWait();
    }

    private void run(Runnable action) {
        try {
            action.run();
        } catch (ServiceException e) {
            UiSupport.showError(stage.isShowing() ? stage : owner, e.getMessage());
        } catch (RuntimeException e) {
            UiSupport.showError(stage.isShowing() ? stage : owner, "Техническая ошибка: " + e.getMessage());
        }
    }

    private static Button button(String text, String id, Runnable action) {
        Button button = new Button(text);
        button.setId(id);
        button.setOnAction(event -> action.run());
        return button;
    }

    /** Данные создаваемой учётной записи. */
    private record NewUser(String login, String password, Role role) {
    }
}
