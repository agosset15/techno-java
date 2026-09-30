package ru.university.spreadsheet.formula;

import java.util.Objects;

import ru.university.spreadsheet.model.CellRef;

/**
 * Узел-ссылка на одну ячейку.
 */
public final class RefNode extends Node {

    private final CellRef ref;

    /**
     * Создаёт узел-ссылку.
     *
     * @param ref адрес ячейки
     * @throws NullPointerException если адрес равен null
     */
    public RefNode(CellRef ref) {
        this.ref = Objects.requireNonNull(ref, "ref");
    }

    /**
     * Возвращает адрес ячейки.
     *
     * @return адрес
     */
    public CellRef getRef() {
        return ref;
    }

    @Override
    public Object accept(Evaluator evaluator) {
        return evaluator.visitRef(this);
    }

    /**
     * Возвращает адрес в нотации A1.
     *
     * @return адрес ячейки
     */
    @Override
    public String toString() {
        return ref.toA1();
    }
}
