package ru.university.spreadsheet.ui;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ru.university.spreadsheet.model.Cell;
import ru.university.spreadsheet.model.CellRange;
import ru.university.spreadsheet.model.CellRef;
import ru.university.spreadsheet.model.CellStyle;
import ru.university.spreadsheet.model.Document;
import ru.university.spreadsheet.service.AuthService;
import ru.university.spreadsheet.service.DocumentService;
import ru.university.spreadsheet.service.StyleService;

/**
 * Главное окно приложения: меню, строка формул, таблица и строка состояния.
 * <p>
 * Отвечает только за построение и обновление элементов интерфейса; действия пользователя
 * передаются {@link SpreadsheetController}.
 */
public class MainView {

    /** Число строк таблицы. */
    public static final int DEFAULT_ROWS = 100;

    /** Число столбцов таблицы (A–Z). */
    public static final int DEFAULT_COLS = 26;

    /**
     * Решение пользователя о несохранённых изменениях.
     */
    public enum SaveChoice {
        /** Сохранить документ. */
        SAVE,
        /** Закрыть документ без сохранения. */
        DISCARD,
        /** Отменить закрытие. */
        CANCEL
    }

    private static final String APP_TITLE = "Электронная таблица";
    private static final double COLUMN_WIDTH = 90;
    private static final double ROW_HEADER_WIDTH = 48;
    private static final double WINDOW_WIDTH = 1100;
    private static final double WINDOW_HEIGHT = 700;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final Stage stage;
    private final Runnable onLogout;
    private final SpreadsheetController controller;

    private final TableView<ObservableList<Cell>> table = new TableView<>();
    private final ToolBar toolbar = new ToolBar();
    private final List<TableColumn<ObservableList<Cell>, Cell>> dataColumns = new ArrayList<>();
    private final Label addressLabel = new Label();
    private final TextField formulaField = new TextField();
    private final Label statusAddress = new Label();
    private final Label statusDocument = new Label();
    private final Label statusModified = new Label();
    private final Label statusUser = new Label();
    private final List<MenuItem> documentItems = new ArrayList<>();
    private final StyleToolbar styleToolbar;

    /**
     * Создаёт главное окно.
     *
     * @param stage           окно JavaFX
     * @param documentService сервис документов
     * @param authService     сервис аутентификации
     * @param styleService    сервис оформления ячеек
     * @param onLogout        действие при выходе из учётной записи
     */
    public MainView(Stage stage, DocumentService documentService, AuthService authService,
                    StyleService styleService, Runnable onLogout) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.onLogout = Objects.requireNonNull(onLogout, "onLogout");
        this.controller = new SpreadsheetController(this, documentService, authService);
        this.styleToolbar = new StyleToolbar(controller, styleService);

        buildTable();
        buildFormulaBar();
        BorderPane root = new BorderPane();
        root.setTop(new VBox(buildMenu(), styleToolbar.getNode(), toolbar));
        root.setCenter(table);
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        UiSupport.applyStylesheet(scene);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            if (!controller.confirmCloseDocument()) {
                event.consume();
            }
        });
        updateStatus();
    }

    /**
     * Показывает окно без открытого документа.
     */
    public void show() {
        stage.show();
    }

    /**
     * Отображает документ в таблице и показывает окно, если оно ещё не показано.
     *
     * @param doc документ
     */
    public void show(Document doc) {
        ObservableList<ObservableList<Cell>> rows = FXCollections.observableArrayList();
        for (int row = 0; row < DEFAULT_ROWS; row++) {
            ObservableList<Cell> cells = FXCollections.observableArrayList();
            for (int col = 0; col < DEFAULT_COLS; col++) {
                cells.add(doc.getCell(new CellRef(row, col)));
            }
            rows.add(cells);
        }
        table.setItems(rows);
        table.setDisable(false);
        formulaField.setDisable(false);
        table.scrollTo(0);
        table.getSelectionModel().clearAndSelect(0, dataColumns.get(0));
        updateStatus();
        if (!stage.isShowing()) {
            stage.show();
        }
        table.requestFocus();
    }

    /**
     * Возвращает окно JavaFX главного окна.
     *
     * @return окно
     */
    public Stage getStage() {
        return stage;
    }

    /**
     * Перерисовывает все видимые ячейки таблицы.
     */
    public void refreshTable() {
        table.refresh();
    }

    /**
     * Показывает адрес и исходный текст ячейки в строке формул.
     *
     * @param ref адрес ячейки
     * @param raw исходный текст
     */
    public void showFormula(CellRef ref, String raw) {
        addressLabel.setText(ref.toA1());
        formulaField.setText(raw);
    }

    /**
     * Обновляет строку состояния, заголовок окна и доступность пунктов меню.
     */
    public void updateStatus() {
        Optional<Document> doc = controller.getCurrentDoc();
        statusAddress.setText("Ячейка: " + controller.getSelection().map(MainView::formatSelection).orElse("—"));
        statusDocument.setText(doc.map(d -> "Документ: " + d.getName()).orElse("Документ не открыт"));
        statusModified.setText(doc.map(d -> d.isModified() ? "● изменён" : "сохранён").orElse(""));
        statusUser.setText("Пользователь: " + controller.getCurrentUserLabel());
        documentItems.forEach(item -> item.setDisable(doc.isEmpty()));
        styleToolbar.getNode().setDisable(doc.isEmpty());
        stage.setTitle(doc.map(d -> d.getName() + (d.isModified() ? " *" : "") + " — ").orElse("") + APP_TITLE);
    }

    /**
     * Запрашивает имя документа. Кнопка подтверждения недоступна, пока имя пустое.
     *
     * @param title заголовок диалога
     * @return введённое имя без пробелов по краям или пустой {@link Optional} при отмене
     */
    public Optional<String> askDocumentName(String title) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(stage);
        dialog.setTitle(title);
        dialog.setHeaderText("Введите имя документа");
        dialog.setContentText("Имя:");
        ButtonType okType = new ButtonType("ОК", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(okType, cancelButtonType());
        Node okButton = dialog.getDialogPane().lookupButton(okType);
        okButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> dialog.getEditor().getText().isBlank(), dialog.getEditor().textProperty()));
        return dialog.showAndWait().map(String::strip).filter(name -> !name.isEmpty());
    }

    /**
     * Показывает список документов и возвращает выбранный.
     *
     * @param documents документы для выбора
     * @return выбранный документ или пустой {@link Optional} при отмене
     */
    public Optional<Document> chooseDocument(List<Document> documents) {
        Dialog<Document> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Открыть документ");
        dialog.setHeaderText("Выберите документ");
        ButtonType openType = new ButtonType("Открыть", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(openType, cancelButtonType());

        TableView<Document> list = new TableView<>(FXCollections.observableArrayList(documents));
        list.setPlaceholder(new Label("Документов нет"));
        list.setPrefSize(440, 300);
        list.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<Document, String> nameColumn = new TableColumn<>("Имя");
        nameColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getName()));
        nameColumn.setPrefWidth(280);
        TableColumn<Document, String> updatedColumn = new TableColumn<>("Изменён");
        updatedColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(DATE_FORMAT.format(data.getValue().getUpdatedAt())));
        updatedColumn.setPrefWidth(130);
        list.getColumns().add(nameColumn);
        list.getColumns().add(updatedColumn);
        list.setRowFactory(tableView -> {
            TableRow<Document> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    dialog.setResult(row.getItem());
                    dialog.close();
                }
            });
            return row;
        });
        if (!documents.isEmpty()) {
            list.getSelectionModel().selectFirst();
        }

        dialog.getDialogPane().lookupButton(openType).disableProperty()
                .bind(list.getSelectionModel().selectedItemProperty().isNull());
        dialog.getDialogPane().setContent(list);
        dialog.setResultConverter(button -> button == openType ? list.getSelectionModel().getSelectedItem() : null);
        return dialog.showAndWait();
    }

    /**
     * Спрашивает, сохранить ли изменения в документе.
     *
     * @param documentName имя документа
     * @return решение пользователя
     */
    public SaveChoice askSaveChanges(String documentName) {
        ButtonType save = new ButtonType("Сохранить", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Не сохранять", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", save, discard, cancel);
        alert.initOwner(stage);
        alert.setTitle("Несохранённые изменения");
        alert.setHeaderText("Сохранить изменения в документе «" + documentName + "»?");
        alert.setContentText("Если не сохранить документ, изменения будут потеряны.");
        Optional<ButtonType> answer = alert.showAndWait();
        if (answer.isPresent() && answer.get() == save) {
            return SaveChoice.SAVE;
        }
        if (answer.isPresent() && answer.get() == discard) {
            return SaveChoice.DISCARD;
        }
        return SaveChoice.CANCEL;
    }

    /**
     * Показывает сообщение об ошибке.
     *
     * @param message текст сообщения
     */
    /**
     * Показывает на панели стилей оформление активной ячейки, не применяя его.
     *
     * @param style стиль активной ячейки
     */
    public void updateStyleToolbar(CellStyle style) {
        styleToolbar.showStyle(style);
    }

    /**
     * Показывает информационное сообщение.
     *
     * @param message текст сообщения
     */
    public void showInfo(String message) {
        UiSupport.showInfo(stage, "Сообщение", message);
    }

    public void showError(String message) {
        UiSupport.showError(stage, message);
    }

    /**
     * Сообщает, что функция будет реализована на одном из следующих этапов разработки.
     *
     * @param feature название функции
     * @param stageNumber номер этапа, на котором функция появится
     */
    public void showNotImplemented(String feature, int stageNumber) {
        UiSupport.showInfo(stage, feature, "Функция будет реализована на этапе " + stageNumber + ".");
    }

    private MenuBar buildMenu() {
        MenuItem newItem = menuItem("Создать…", "Shortcut+N", controller::onNew);
        MenuItem openItem = menuItem("Открыть…", "Shortcut+O", controller::onOpen);
        MenuItem saveItem = menuItem("Сохранить", "Shortcut+S", controller::onSave);
        MenuItem saveAsItem = menuItem("Сохранить как…", "Shortcut+Shift+S",
                () -> showNotImplemented("Сохранить как", 7));
        MenuItem printItem = menuItem("Печать…", "Shortcut+P", controller::onPrint);
        MenuItem exitItem = menuItem("Выход", null, this::onExit);
        documentItems.addAll(List.of(saveItem, saveAsItem, printItem));
        Menu fileMenu = new Menu("Файл", null, newItem, openItem, new SeparatorMenuItem(),
                saveItem, saveAsItem, new SeparatorMenuItem(), printItem, new SeparatorMenuItem(), exitItem);

        MenuItem copyStyleItem = menuItem("Копировать стиль", "Shortcut+Shift+C", styleToolbar::onCopyStyle);
        MenuItem pasteStyleItem = menuItem("Вставить стиль", "Shortcut+Shift+V", styleToolbar::onPasteStyle);
        documentItems.addAll(List.of(copyStyleItem, pasteStyleItem));
        Menu editMenu = new Menu("Правка", null, copyStyleItem, pasteStyleItem);

        Menu adminMenu = new Menu("Администрирование", null,
                menuItem("Пользователи…", null, () -> showNotImplemented("Управление пользователями", 7)));
        adminMenu.setId("adminMenu");
        adminMenu.setDisable(!controller.isAdmin());

        Menu helpMenu = new Menu("Справка", null, menuItem("О программе", null, this::showAbout));
        return new MenuBar(fileMenu, editMenu, adminMenu, helpMenu);
    }

    private static ButtonType cancelButtonType() {
        return new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
    }

    private static MenuItem menuItem(String text, String accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(KeyCombination.keyCombination(accelerator));
        }
        item.setOnAction(event -> action.run());
        return item;
    }

    private void buildFormulaBar() {
        addressLabel.getStyleClass().add("cell-address");
        formulaField.setId("formulaField");
        formulaField.setPromptText("Значение или формула, начинающаяся с «=»");
        formulaField.setDisable(true);
        HBox.setHgrow(formulaField, Priority.ALWAYS);
        formulaField.setOnAction(event -> controller.getActiveCell().ifPresent(ref -> {
            controller.onCellEdit(ref, formulaField.getText());
            table.requestFocus();
        }));
        formulaField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                controller.getSelection().ifPresent(range -> controller.onSelectRange(range));
                table.requestFocus();
            }
        });
        HBox bar = new HBox(8, addressLabel, new Label("ƒx"), formulaField);
        bar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(bar, Priority.ALWAYS);
        bar.prefWidthProperty().bind(toolbar.widthProperty().subtract(20));
        toolbar.getStyleClass().add("formula-bar");
        toolbar.getItems().add(bar);
    }

    private HBox buildStatusBar() {
        HBox statusBar = new HBox(statusAddress, statusDocument, statusModified, statusUser);
        statusBar.getStyleClass().add("status-bar");
        statusModified.setId("statusModified");
        return statusBar;
    }

    private void buildTable() {
        table.setId("spreadsheetTable");
        table.setEditable(true);
        table.setDisable(true);
        table.setPlaceholder(new Label("Создайте или откройте документ в меню «Файл»"));
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<ObservableList<Cell>, Number> rowHeader = new TableColumn<>("");
        rowHeader.setCellValueFactory(
                data -> new ReadOnlyObjectWrapper<Number>(data.getValue().get(0).getRef().row() + 1));
        rowHeader.setCellFactory(column -> new TableCell<>() {
            {
                getStyleClass().add("row-header");
            }

            @Override
            protected void updateItem(Number number, boolean empty) {
                super.updateItem(number, empty);
                setText(empty || number == null ? null : number.toString());
            }
        });
        configureColumn(rowHeader, ROW_HEADER_WIDTH);
        rowHeader.setEditable(false);
        table.getColumns().add(rowHeader);

        for (int col = 0; col < DEFAULT_COLS; col++) {
            int colIndex = col;
            TableColumn<ObservableList<Cell>, Cell> column = new TableColumn<>(CellRef.columnName(col));
            column.setUserData(colIndex);
            column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().get(colIndex)));
            column.setCellFactory(tableColumn -> new SpreadsheetTableCell(controller::onCellEdit));
            configureColumn(column, COLUMN_WIDTH);
            dataColumns.add(column);
            table.getColumns().add(column);
        }

        table.getSelectionModel().getSelectedCells()
                .addListener((ListChangeListener<TablePosition>) change -> onTableSelectionChanged());
        table.addEventHandler(KeyEvent.KEY_TYPED, this::onTableKeyTyped);
        table.addEventHandler(KeyEvent.KEY_PRESSED, this::onTableKeyPressed);
    }

    private static void configureColumn(TableColumn<ObservableList<Cell>, ?> column, double width) {
        column.setSortable(false);
        column.setReorderable(false);
        column.setPrefWidth(width);
    }

    /**
     * Приводит выделенные ячейки таблицы к прямоугольному диапазону и передаёт его
     * контроллеру вместе с активной (сфокусированной) ячейкой.
     */
    private void onTableSelectionChanged() {
        ObservableList<TablePosition> positions = table.getSelectionModel().getSelectedCells();
        if (positions.isEmpty()) {
            return;
        }
        int minRow = Integer.MAX_VALUE;
        int minCol = Integer.MAX_VALUE;
        int maxRow = -1;
        int maxCol = -1;
        for (TablePosition<?, ?> position : positions) {
            if (position.getRow() < 0 || !(position.getTableColumn().getUserData() instanceof Integer col)) {
                continue;
            }
            minRow = Math.min(minRow, position.getRow());
            maxRow = Math.max(maxRow, position.getRow());
            minCol = Math.min(minCol, col);
            maxCol = Math.max(maxCol, col);
        }
        if (maxRow < 0) {
            // Выделены только номера строк: переносим выделение на первый столбец.
            int row = positions.get(0).getRow();
            if (row >= 0) {
                Platform.runLater(() -> table.getSelectionModel().clearAndSelect(row, dataColumns.get(0)));
            }
            return;
        }
        focusedRef().ifPresent(controller::onSelect);
        controller.onSelectRange(new CellRange(new CellRef(minRow, minCol), new CellRef(maxRow, maxCol)));
    }

    private Optional<CellRef> focusedRef() {
        TablePosition<ObservableList<Cell>, ?> focused = table.getFocusModel().getFocusedCell();
        if (focused == null || focused.getRow() < 0 || focused.getTableColumn() == null
                || !(focused.getTableColumn().getUserData() instanceof Integer col)) {
            return Optional.empty();
        }
        return Optional.of(new CellRef(focused.getRow(), col));
    }

    private void onTableKeyTyped(KeyEvent event) {
        String typed = event.getCharacter();
        if (table.getEditingCell() != null || typed.isEmpty() || Character.isISOControl(typed.charAt(0))
                || event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return;
        }
        controller.getActiveCell().ifPresent(ref -> {
            startEditing(ref, typed);
            event.consume();
        });
    }

    private void onTableKeyPressed(KeyEvent event) {
        if (table.getEditingCell() != null) {
            return;
        }
        Optional<CellRef> selected = controller.getActiveCell();
        if (selected.isEmpty()) {
            return;
        }
        if (event.getCode() == KeyCode.F2) {
            startEditing(selected.get(), null);
            event.consume();
        } else if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
            controller.onCellEdit(selected.get(), "");
            event.consume();
        }
    }

    private void startEditing(CellRef ref, String initialInput) {
        if (initialInput != null) {
            table.getProperties().put(SpreadsheetTableCell.INITIAL_INPUT_KEY, initialInput);
        }
        table.edit(ref.row(), dataColumns.get(ref.col()));
        table.getProperties().remove(SpreadsheetTableCell.INITIAL_INPUT_KEY);
    }

    private void onExit() {
        if (controller.confirmCloseDocument()) {
            onLogout.run();
        }
    }

    private void showAbout() {
        UiSupport.showInfo(stage, APP_TITLE + " 1.0.0",
                "Создание и редактирование табличных документов с формулами и стилями ячеек.\n"
                        + "Курсовая работа. Java, JavaFX, SQLite.");
    }

    private static String formatSelection(CellRange range) {
        return range.size() == 1 ? range.from().toA1() : range.toString();
    }
}
