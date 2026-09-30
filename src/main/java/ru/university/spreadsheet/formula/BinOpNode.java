package ru.university.spreadsheet.formula;

import java.util.Objects;

/**
 * Узел бинарной операции: арифметической или сравнения.
 */
public final class BinOpNode extends Node {

    private final String op;
    private final Node left;
    private final Node right;

    /**
     * Создаёт узел бинарной операции.
     *
     * @param op    знак операции
     * @param left  левый операнд
     * @param right правый операнд
     * @throws NullPointerException если операция или операнд равны null
     */
    public BinOpNode(String op, Node left, Node right) {
        this.op = Objects.requireNonNull(op, "op");
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
    }

    /**
     * Возвращает знак операции.
     *
     * @return операция
     */
    public String getOp() {
        return op;
    }

    /**
     * Возвращает левый операнд.
     *
     * @return узел левого операнда
     */
    public Node getLeft() {
        return left;
    }

    /**
     * Возвращает правый операнд.
     *
     * @return узел правого операнда
     */
    public Node getRight() {
        return right;
    }

    @Override
    public Object accept(Evaluator evaluator) {
        return evaluator.visitBinOp(this);
    }

    /**
     * Возвращает выражение в скобках.
     *
     * @return строка вида (2 + (2 * 2))
     */
    @Override
    public String toString() {
        return "(" + left + " " + op + " " + right + ")";
    }
}
