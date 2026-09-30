package ru.university.spreadsheet.service;

import java.util.Objects;
import java.util.function.UnaryOperator;

import javafx.scene.paint.Color;
import ru.university.spreadsheet.model.Cell;
import ru.university.spreadsheet.model.CellRange;
import ru.university.spreadsheet.model.CellRef;
import ru.university.spreadsheet.model.CellStyle;
import ru.university.spreadsheet.model.Document;

/**
 * Оформление ячеек: применение стиля к выделенному диапазону.
 * <p>
 * Отсутствующие ячейки диапазона создаются, документ помечается изменённым.
 * Ячейка без текста, но с нестандартным стилем считается непустой и сохраняется
 * в базе данных.
 */
public class StyleService {

    /**
     * Применяет стиль ко всем ячейкам диапазона.
     *
     * @param doc   документ
     * @param range диапазон ячеек
     * @param style применяемый стиль
     * @throws NullPointerException если документ, диапазон или стиль равны null
     */
    public void applyStyle(Document doc, CellRange range, CellStyle style) {
        Objects.requireNonNull(style, "style");
        update(doc, range, current -> style);
    }

    /**
     * Меняет цвет фона ячеек диапазона, сохраняя цвет текста и начертание.
     *
     * @param doc   документ
     * @param range диапазон ячеек
     * @param color новый цвет фона
     * @throws NullPointerException если документ, диапазон или цвет равны null
     */
    public void applyBackground(Document doc, CellRange range, Color color) {
        Objects.requireNonNull(color, "color");
        update(doc, range, current -> current.withBg(color));
    }

    /**
     * Меняет цвет текста ячеек диапазона, сохраняя цвет фона и начертание.
     *
     * @param doc   документ
     * @param range диапазон ячеек
     * @param color новый цвет текста
     * @throws NullPointerException если документ, диапазон или цвет равны null
     */
    public void applyForeground(Document doc, CellRange range, Color color) {
        Objects.requireNonNull(color, "color");
        update(doc, range, current -> current.withFg(color));
    }

    /**
     * Включает или выключает полужирное начертание, сохраняя цвета.
     *
     * @param doc   документ
     * @param range диапазон ячеек
     * @param bold  признак полужирного начертания
     * @throws NullPointerException если документ или диапазон равны null
     */
    public void applyBold(Document doc, CellRange range, boolean bold) {
        update(doc, range, current -> current.withBold(bold));
    }

    /**
     * Возвращает ячейкам диапазона стиль по умолчанию.
     *
     * @param doc   документ
     * @param range диапазон ячеек
     * @throws NullPointerException если документ или диапазон равны null
     */
    public void resetStyle(Document doc, CellRange range) {
        applyStyle(doc, range, CellStyle.DEFAULT);
    }

    private void update(Document doc, CellRange range, UnaryOperator<CellStyle> change) {
        Objects.requireNonNull(doc, "doc");
        Objects.requireNonNull(range, "range");
        for (CellRef ref : range) {
            Cell cell = doc.getCell(ref);
            cell.setStyle(change.apply(cell.getStyle()));
        }
        doc.setModified(true);
    }
}
