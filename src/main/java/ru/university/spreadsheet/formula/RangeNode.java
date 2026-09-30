package ru.university.spreadsheet.formula;

import java.util.Objects;

import ru.university.spreadsheet.model.CellRange;

/**
 * Узел-диапазон ячеек. Допустим только как аргумент функции: вне функции
 * вычислитель возвращает ошибку {@value FormulaException#ERROR}.
 */
public final class RangeNode extends Node {

    private final CellRange range;

    /**
     * Создаёт узел-диапазон.
     *
     * @param range диапазон ячеек
     * @throws NullPointerException если диапазон равен null
     */
    public RangeNode(CellRange range) {
        this.range = Objects.requireNonNull(range, "range");
    }

    /**
     * Возвращает диапазон.
     *
     * @return диапазон ячеек
     */
    public CellRange getRange() {
        return range;
    }

    @Override
    public Object accept(Evaluator evaluator) {
        return evaluator.visitRange(this);
    }

    /**
     * Возвращает диапазон в нотации A1.
     *
     * @return строка вида A1:C5
     */
    @Override
    public String toString() {
        return range.toString();
    }
}
