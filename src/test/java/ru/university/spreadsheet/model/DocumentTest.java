package ru.university.spreadsheet.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Document: табличный документ")
class DocumentTest {

    private static final CellRef A1 = CellRef.parse("A1");
    private static final CellRef B2 = CellRef.parse("B2");

    @Test
    @DisplayName("новый документ не помечен изменённым")
    void newDocumentIsClean() {
        Document doc = new Document("Смета", 1);

        assertThat(doc.getId()).isZero();
        assertThat(doc.isModified()).isFalse();
        assertThat(doc.getUpdatedAt()).isEqualTo(doc.getCreatedAt());
    }

    @Test
    @DisplayName("getCell создаёт пустую ячейку один раз и не меняет флаг modified")
    void getCellCreatesOnce() {
        Document doc = new Document("Смета", 1);

        Cell first = doc.getCell(A1);

        assertThat(doc.getCell(A1)).isSameAs(first);
        assertThat(first.isEmpty()).isTrue();
        assertThat(doc.isModified()).isFalse();
        assertThat(doc.findCell(B2)).isEmpty();
    }

    @Test
    @DisplayName("setCell и removeCell помечают документ изменённым, markSaved снимает флаг")
    void modificationFlag() {
        Document doc = new Document("Смета", 1);

        doc.setCell(A1, new Cell(A1, "1"));
        assertThat(doc.isModified()).isTrue();

        doc.markSaved();
        assertThat(doc.isModified()).isFalse();

        doc.removeCell(A1);
        assertThat(doc.isModified()).isTrue();
        assertThat(doc.findCell(A1)).isEmpty();
    }

    @Test
    @DisplayName("setCell отклоняет ячейку с несовпадающим адресом")
    void setCellRejectsMismatch() {
        Document doc = new Document("Смета", 1);

        assertThatThrownBy(() -> doc.setCell(A1, new Cell(B2, "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getNonEmptyCells пропускает пустые ячейки и упорядочивает по позиции")
    void nonEmptyCells() {
        Document doc = new Document("Смета", 1);
        doc.setCell(B2, new Cell(B2, "2"));
        doc.getCell(CellRef.parse("C3"));
        doc.setCell(A1, new Cell(A1, "1"));

        assertThat(doc.getNonEmptyCells())
                .extracting(cell -> cell.getRef().toA1())
                .containsExactly("A1", "B2");
    }
}
