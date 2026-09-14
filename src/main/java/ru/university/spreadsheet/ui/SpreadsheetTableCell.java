package ru.university.spreadsheet.ui;

import java.util.function.BiConsumer;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import ru.university.spreadsheet.model.Cell;
import ru.university.spreadsheet.model.CellRef;

/**
 * Ячейка таблицы на экране: отображает значение ячейки документа с её стилем
 * и поддерживает редактирование на месте.
 * <p>
 * Ввод подтверждается клавишей Enter (с переходом на строку ниже) или потерей фокуса,
 * отменяется клавишей Esc. Подтверждённый текст передаётся обработчику {@code onCommit};
 * сама ячейка документ не изменяет.
 */
final class SpreadsheetTableCell extends TableCell<ObservableList<Cell>, Cell> {

    /** Ключ свойства таблицы с символом, с которого начат ввод в ячейку. */
    static final String INITIAL_INPUT_KEY = "spreadsheet.initialInput";

    private static final String ERROR_TEXT_CSS = " -fx-text-fill: #C62828;";

    private final BiConsumer<CellRef, String> onCommit;
    private TextField editor;
    private CellRef editingRef;
    private String originalRaw;
    private boolean discardEdit;

    SpreadsheetTableCell(BiConsumer<CellRef, String> onCommit) {
        this.onCommit = onCommit;
        getStyleClass().add("spreadsheet-cell");
    }

    @Override
    protected void updateItem(Cell cell, boolean empty) {
        super.updateItem(cell, empty);
        if (empty || cell == null) {
            setText(null);
            setGraphic(null);
            setStyle("");
        } else if (isEditing() && editor != null) {
            setText(null);
            setGraphic(editor);
        } else {
            render(cell);
        }
    }

    @Override
    public void startEdit() {
        Cell cell = getItem();
        if (cell == null) {
            return;
        }
        super.startEdit();
        if (!isEditing()) {
            return;
        }
        Object initialInput = getTableView().getProperties().remove(INITIAL_INPUT_KEY);
        editingRef = cell.getRef();
        originalRaw = cell.getRaw();
        discardEdit = false;
        if (editor == null) {
            editor = createEditor();
        }
        editor.setText(initialInput instanceof String typed ? typed : originalRaw);
        setText(null);
        setGraphic(editor);
        editor.requestFocus();
        editor.end();
    }

    @Override
    public void cancelEdit() {
        if (!isEditing()) {
            return;
        }
        String text = editor.getText();
        CellRef ref = editingRef;
        String before = originalRaw;
        boolean discard = discardEdit;

        super.cancelEdit();
        editingRef = null;
        discardEdit = false;
        setGraphic(null);
        if (getItem() != null) {
            render(getItem());
        }

        if (!discard && ref != null && text != null && !text.equals(before)) {
            Platform.runLater(() -> onCommit.accept(ref, text));
        }
    }

    private TextField createEditor() {
        TextField field = new TextField();
        field.getStyleClass().add("cell-editor");
        field.setOnAction(event -> {
            finishEditingAndMoveDown();
            event.consume();
        });
        field.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                discardEdit = true;
                cancelEdit();
                getTableView().requestFocus();
                event.consume();
            }
        });
        field.focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (!focused && isEditing()) {
                cancelEdit();
            }
        });
        return field;
    }

    private void finishEditingAndMoveDown() {
        int row = getIndex();
        TableColumn<ObservableList<Cell>, Cell> column = getTableColumn();
        TableView<ObservableList<Cell>> table = getTableView();
        cancelEdit();
        if (row + 1 < table.getItems().size()) {
            table.getSelectionModel().clearAndSelect(row + 1, column);
        }
        table.requestFocus();
    }

    private void render(Cell cell) {
        setText(cell.getDisplayValue());
        setGraphic(null);
        String css = cell.getStyle().toCss();
        setStyle(cell.hasError() ? css + ERROR_TEXT_CSS : css);
    }
}
