package ru.university.spreadsheet.ui;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import ru.university.spreadsheet.model.CellRange;
import ru.university.spreadsheet.model.CellStyle;
import ru.university.spreadsheet.model.Document;
import ru.university.spreadsheet.service.StyleService;

/**
 * Панель оформления ячеек: выбор цвета фона и текста, полужирное начертание,
 * сброс стиля, копирование и вставка стиля.
 * <p>
 * Стиль применяется к текущему выделению главного окна. Если документ не открыт
 * или выделение отсутствует, команды игнорируются.
 */
public class StyleToolbar {

    private final SpreadsheetController controller;
    private final StyleService styleService;
    private final ColorPicker background = new ColorPicker(CellStyle.DEFAULT.bg());
    private final ColorPicker foreground = new ColorPicker(CellStyle.DEFAULT.fg());
    private final ToggleButton bold = new ToggleButton("Ж");
    private final ToolBar toolbar;
    private CellStyle copiedStyle;
    private boolean syncing;

    /**
     * Создаёт панель стилей.
     *
     * @param controller   контроллер главного окна, хранящий документ и выделение
     * @param styleService сервис оформления ячеек
     */
    public StyleToolbar(SpreadsheetController controller, StyleService styleService) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.styleService = Objects.requireNonNull(styleService, "styleService");

        background.setId("bgColorPicker");
        background.setTooltip(new Tooltip("Цвет фона"));
        background.setOnAction(event -> onBgColorChange(background.getValue()));

        foreground.setId("fgColorPicker");
        foreground.setTooltip(new Tooltip("Цвет текста"));
        foreground.setOnAction(event -> onFgColorChange(foreground.getValue()));

        bold.setId("boldToggle");
        bold.setTooltip(new Tooltip("Полужирное начертание"));
        bold.getStyleClass().add("bold-toggle");
        bold.setOnAction(event -> onBoldToggle());

        Button reset = new Button("Сбросить стиль");
        reset.setId("resetStyleButton");
        reset.setOnAction(event -> onResetStyle());

        toolbar = new ToolBar(new Label("Фон:"), background, new Label("Текст:"), foreground,
                bold, new Separator(), reset);
        toolbar.getStyleClass().add("style-toolbar");
    }

    /**
     * Возвращает панель для размещения в окне.
     *
     * @return панель инструментов JavaFX
     */
    public ToolBar getNode() {
        return toolbar;
    }

    /**
     * Применяет выбранный цвет фона к выделению.
     *
     * @param color цвет фона
     */
    public void onBgColorChange(Color color) {
        apply((doc, range) -> styleService.applyBackground(doc, range, color));
    }

    /**
     * Применяет выбранный цвет текста к выделению.
     *
     * @param color цвет текста
     */
    public void onFgColorChange(Color color) {
        apply((doc, range) -> styleService.applyForeground(doc, range, color));
    }

    /**
     * Включает или выключает полужирное начертание для выделения.
     */
    public void onBoldToggle() {
        boolean selected = bold.isSelected();
        apply((doc, range) -> styleService.applyBold(doc, range, selected));
    }

    /**
     * Возвращает выделенным ячейкам стиль по умолчанию.
     */
    public void onResetStyle() {
        apply(styleService::resetStyle);
        showStyle(CellStyle.DEFAULT);
    }

    /**
     * Запоминает стиль активной ячейки для последующей вставки.
     */
    public void onCopyStyle() {
        controller.getActiveCell()
                .flatMap(ref -> controller.getCurrentDoc().map(doc -> doc.getCell(ref).getStyle()))
                .ifPresent(style -> {
                    copiedStyle = style;
                    showStyle(style);
                });
    }

    /**
     * Применяет ранее скопированный стиль ко всему выделению.
     */
    public void onPasteStyle() {
        if (copiedStyle == null) {
            controller.showMessage("Сначала скопируйте стиль ячейки");
            return;
        }
        CellStyle style = copiedStyle;
        apply((doc, range) -> styleService.applyStyle(doc, range, style));
    }

    /**
     * Показывает на панели стиль активной ячейки, не применяя его.
     *
     * @param style стиль активной ячейки
     */
    public void showStyle(CellStyle style) {
        syncing = true;
        try {
            background.setValue(style.bg());
            foreground.setValue(style.fg());
            bold.setSelected(style.bold());
        } finally {
            syncing = false;
        }
    }

    private void apply(BiConsumer<Document, CellRange> action) {
        if (syncing) {
            return;
        }
        Optional<Document> doc = controller.getCurrentDoc();
        Optional<CellRange> range = controller.getSelection();
        if (doc.isEmpty() || range.isEmpty()) {
            return;
        }
        action.accept(doc.get(), range.get());
        controller.onStyleApplied();
    }
}
