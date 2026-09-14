package ru.university.spreadsheet.model;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Прямоугольный диапазон ячеек, заданный двумя углами включительно.
 * <p>
 * Конструктор нормализует углы: {@code from} всегда верхний левый, {@code to} — нижний правый,
 * поэтому диапазоны {@code C3:A1}, {@code A3:C1} и {@code A1:C3} равны. Одиночная ячейка
 * представляется диапазоном 1×1. Объект неизменяем.
 *
 * @param from верхний левый угол диапазона
 * @param to   нижний правый угол диапазона
 */
public record CellRange(CellRef from, CellRef to) implements Iterable<CellRef> {

    /**
     * Создаёт диапазон по двум произвольным противоположным углам.
     *
     * @param from первый угол
     * @param to   второй угол
     * @throws NullPointerException если один из углов равен {@code null}
     */
    public CellRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        CellRef topLeft = new CellRef(Math.min(from.row(), to.row()), Math.min(from.col(), to.col()));
        CellRef bottomRight = new CellRef(Math.max(from.row(), to.row()), Math.max(from.col(), to.col()));
        from = topLeft;
        to = bottomRight;
    }

    /**
     * Создаёт диапазон из одной ячейки.
     *
     * @param ref адрес ячейки
     * @return диапазон 1×1
     */
    public static CellRange of(CellRef ref) {
        return new CellRange(ref, ref);
    }

    /**
     * Проверяет, входит ли ячейка в диапазон.
     *
     * @param ref адрес ячейки
     * @return {@code true}, если ячейка лежит внутри диапазона или на его границе
     * @throws NullPointerException если адрес равен {@code null}
     */
    public boolean contains(CellRef ref) {
        Objects.requireNonNull(ref, "ref");
        return ref.row() >= from.row() && ref.row() <= to.row()
                && ref.col() >= from.col() && ref.col() <= to.col();
    }

    /**
     * Возвращает количество строк в диапазоне.
     *
     * @return число строк, не меньше 1
     */
    public int rowCount() {
        return to.row() - from.row() + 1;
    }

    /**
     * Возвращает количество столбцов в диапазоне.
     *
     * @return число столбцов, не меньше 1
     */
    public int colCount() {
        return to.col() - from.col() + 1;
    }

    /**
     * Возвращает количество ячеек в диапазоне.
     *
     * @return произведение числа строк на число столбцов
     */
    public long size() {
        return (long) rowCount() * colCount();
    }

    /**
     * Возвращает итератор, обходящий ячейки диапазона построчно: слева направо,
     * затем сверху вниз.
     *
     * @return итератор адресов ячеек
     */
    @Override
    public Iterator<CellRef> iterator() {
        return new Iterator<>() {
            private int row = from.row();
            private int col = from.col();
            private boolean finished;

            @Override
            public boolean hasNext() {
                return !finished;
            }

            @Override
            public CellRef next() {
                if (finished) {
                    throw new NoSuchElementException();
                }
                CellRef current = new CellRef(row, col);
                if (col < to.col()) {
                    col++;
                } else if (row < to.row()) {
                    col = from.col();
                    row++;
                } else {
                    finished = true;
                }
                return current;
            }
        };
    }

    /**
     * Возвращает диапазон в нотации A1.
     *
     * @return строка вида {@code "A1:C5"}
     */
    @Override
    public String toString() {
        return from.toA1() + ":" + to.toA1();
    }
}
