package ru.university.spreadsheet.model;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cell: ячейка документа")
class CellTest {

    private static final CellRef A1 = CellRef.parse("A1");

    @Test
    @DisplayName("isFormula определяется по символу '=' в начале")
    void isFormula() {
        assertThat(new Cell(A1, "=1+2").isFormula()).isTrue();
        assertThat(new Cell(A1, "1+2").isFormula()).isFalse();
        assertThat(new Cell(A1).isFormula()).isFalse();
    }

    @Test
    @DisplayName("getDisplayValue: ошибка важнее значения, значение важнее исходного текста")
    void displayPriority() {
        Cell cell = new Cell(A1, "=1+2");
        assertThat(cell.getDisplayValue()).isEqualTo("=1+2");

        cell.setCachedValue(3.0);
        assertThat(cell.getDisplayValue()).isEqualTo("3");

        cell.setError("#DIV/0!");
        assertThat(cell.getDisplayValue()).isEqualTo("#DIV/0!");
        assertThat(cell.getCachedValue()).isEmpty();
    }

    @Test
    @DisplayName("дробные значения выводятся без научной нотации и лишних нулей")
    void formatsNumbers() {
        Cell cell = new Cell(A1, "=x");

        cell.setCachedValue(2.5);
        assertThat(cell.getDisplayValue()).isEqualTo("2.5");

        cell.setCachedValue(0.00001);
        assertThat(cell.getDisplayValue()).isEqualTo("0.00001");
    }

    @Test
    @DisplayName("setRaw сбрасывает кэшированное значение и ошибку")
    void setRawResetsState() {
        Cell cell = new Cell(A1, "=1/0");
        cell.setError("#DIV/0!");

        cell.setRaw("текст");

        assertThat(cell.getError()).isEmpty();
        assertThat(cell.getCachedValue()).isEmpty();
        assertThat(cell.getDisplayValue()).isEqualTo("текст");
    }

    @Test
    @DisplayName("пустой считается ячейка без текста и со стилем по умолчанию")
    void isEmpty() {
        assertThat(new Cell(A1).isEmpty()).isTrue();
        assertThat(new Cell(A1, null).isEmpty()).isTrue();
        assertThat(new Cell(A1, "x").isEmpty()).isFalse();
        assertThat(new Cell(A1, "", new CellStyle(Color.YELLOW, Color.BLACK, false)).isEmpty()).isFalse();
    }

    @Test
    @DisplayName("логическое значение выводится как TRUE или FALSE")
    void formatsBoolean() {
        Cell cell = new Cell(A1, "=1<2");

        cell.setCachedValue(Boolean.TRUE);
        assertThat(cell.getDisplayValue()).isEqualTo("TRUE");

        cell.setCachedValue(Boolean.FALSE);
        assertThat(cell.getDisplayValue()).isEqualTo("FALSE");
    }
}
