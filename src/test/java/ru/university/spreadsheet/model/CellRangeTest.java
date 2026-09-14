package ru.university.spreadsheet.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CellRange: прямоугольный диапазон ячеек")
class CellRangeTest {

    private static CellRange range(String from, String to) {
        return new CellRange(CellRef.parse(from), CellRef.parse(to));
    }

    private static List<String> addresses(CellRange range) {
        List<String> result = new ArrayList<>();
        range.forEach(ref -> result.add(ref.toA1()));
        return result;
    }

    @Test
    @DisplayName("конструктор нормализует углы: from — верхний левый, to — нижний правый")
    void normalizesCorners() {
        CellRange reversed = range("C3", "A1");
        CellRange antiDiagonal = range("A3", "C1");

        assertThat(reversed.from()).isEqualTo(CellRef.parse("A1"));
        assertThat(reversed.to()).isEqualTo(CellRef.parse("C3"));
        assertThat(antiDiagonal).isEqualTo(range("A1", "C3"));
    }

    @Test
    @DisplayName("итерация идёт построчно слева направо")
    void iteratesRowByRow() {
        assertThat(addresses(range("B2", "A1"))).containsExactly("A1", "B1", "A2", "B2");
    }

    @Test
    @DisplayName("size, rowCount и colCount соответствуют размерам диапазона")
    void size() {
        CellRange range = range("A1", "C5");

        assertThat(range.size()).isEqualTo(15);
        assertThat(range.rowCount()).isEqualTo(5);
        assertThat(range.colCount()).isEqualTo(3);
        assertThat(addresses(range)).hasSize(15);
    }

    @Test
    @DisplayName("диапазон из одной ячейки имеет размер 1")
    void singleCell() {
        CellRange single = CellRange.of(CellRef.parse("D4"));

        assertThat(single.size()).isEqualTo(1);
        assertThat(addresses(single)).containsExactly("D4");
    }

    @Test
    @DisplayName("contains учитывает границы диапазона включительно")
    void contains() {
        CellRange range = range("B2", "D4");

        assertThat(range.contains(CellRef.parse("B2"))).isTrue();
        assertThat(range.contains(CellRef.parse("D4"))).isTrue();
        assertThat(range.contains(CellRef.parse("C3"))).isTrue();
        assertThat(range.contains(CellRef.parse("A2"))).isFalse();
        assertThat(range.contains(CellRef.parse("E4"))).isFalse();
        assertThat(range.contains(CellRef.parse("B5"))).isFalse();
    }

    @Test
    @DisplayName("исчерпанный итератор бросает NoSuchElementException")
    void exhaustedIterator() {
        Iterator<CellRef> iterator = CellRange.of(CellRef.parse("A1")).iterator();
        iterator.next();

        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("toString возвращает диапазон в нотации A1")
    void formatsAsA1() {
        assertThat(range("C5", "A1")).hasToString("A1:C5");
    }
}
