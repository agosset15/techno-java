package ru.university.spreadsheet.formula;

/**
 * Узел-число.
 */
public final class NumberNode extends Node {

    private static final double MAX_EXACT_INTEGER = 1e15;

    private final double value;

    /**
     * Создаёт узел-число.
     *
     * @param value значение
     */
    public NumberNode(double value) {
        this.value = value;
    }

    /**
     * Возвращает значение.
     *
     * @return число
     */
    public double getValue() {
        return value;
    }

    @Override
    public Object accept(Evaluator evaluator) {
        return evaluator.visitNumber(this);
    }

    /**
     * Возвращает число без дробной части, если оно целое.
     *
     * @return текст числа
     */
    @Override
    public String toString() {
        return value == Math.rint(value) && Math.abs(value) < MAX_EXACT_INTEGER
                ? Long.toString((long) value)
                : Double.toString(value);
    }
}
