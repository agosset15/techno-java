package ru.university.spreadsheet.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CellRef: адрес ячейки в нотации A1")
class CellRefTest {

    @ParameterizedTest(name = "{0} -> row={1}, col={2}")
    @CsvSource({
            "A1, 0, 0",
            "Z1, 0, 25",
            "AA1, 0, 26",
            "AZ10, 9, 51",
            "BA2, 1, 52",
            "BC27, 26, 54",
            "ZZ100, 99, 701",
            "AAA1, 0, 702"
    })
    @DisplayName("parse и toA1 взаимно обратны, включая двух- и трёхбуквенные столбцы")
    void parseAndFormat(String a1, int row, int col) {
        CellRef ref = new CellRef(row, col);

        assertThat(CellRef.parse(a1)).isEqualTo(ref);
        assertThat(ref.toA1()).isEqualTo(a1);
    }

    @Test
    @DisplayName("parse не зависит от регистра, toA1 возвращает заглавные буквы")
    void caseInsensitive() {
        CellRef ref = CellRef.parse("bc27");

        assertThat(ref).isEqualTo(CellRef.parse("BC27"));
        assertThat(ref.toA1()).isEqualTo("BC27");
    }

    @Test
    @DisplayName("round-trip parse(toA1()) для всех адресов в диапазоне 0..300 × 0..800")
    void roundTripGrid() {
        for (int row = 0; row <= 300; row += 7) {
            for (int col = 0; col <= 800; col++) {
                CellRef ref = new CellRef(row, col);
                assertThat(CellRef.parse(ref.toA1())).isEqualTo(ref);
            }
        }
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"A", "1", "A0", "A01", "1A", "A-1", "A1B", " A1", "A1 ", "Ж1", "A1:B2",
            "A99999999999", "ZZZZZZZZ1"})
    @DisplayName("некорректный адрес вызывает IllegalArgumentException")
    void rejectsInvalid(String input) {
        assertThatThrownBy(() -> CellRef.parse(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("отрицательные координаты недопустимы")
    void rejectsNegative() {
        assertThatThrownBy(() -> new CellRef(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CellRef(0, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("columnName формирует буквенное обозначение столбца")
    void columnName() {
        assertThat(CellRef.columnName(0)).isEqualTo("A");
        assertThat(CellRef.columnName(25)).isEqualTo("Z");
        assertThat(CellRef.columnName(26)).isEqualTo("AA");
        assertThat(CellRef.columnName(701)).isEqualTo("ZZ");
    }
}
