package ru.university.spreadsheet.formula;

import java.util.Objects;

/**
 * Узел унарной операции: смены знака или унарного плюса.
 */
public final class UnaryNode extends Node {

    private final String op;
    private final Node operand;

    /**
     * Создаёт узел унарной операции.
     *
     * @param op      знак операции
     * @param operand операнд
     * @throws NullPointerException если операция или операнд равны null
     */
    public UnaryNode(String op, Node operand) {
        this.op = Objects.requireNonNull(op, "op");
        this.operand = Objects.requireNonNull(operand, "operand");
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
     * Возвращает операнд.
     *
     * @return узел операнда
     */
    public Node getOperand() {
        return operand;
    }

    @Override
    public Object accept(Evaluator evaluator) {
        return evaluator.visitUnary(this);
    }

    /**
     * Возвращает выражение в скобках.
     *
     * @return строка вида (-3)
     */
    @Override
    public String toString() {
        return "(" + op + operand + ")";
    }
}
