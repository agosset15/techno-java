package ru.university.spreadsheet.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.university.spreadsheet.dao.DatabaseTestSupport;
import ru.university.spreadsheet.formula.FormulaException;
import ru.university.spreadsheet.model.CellRef;
import ru.university.spreadsheet.model.Document;

@DisplayName("DocumentService: пересчёт формул и обнаружение циклов")
class DocumentRecalculationTest extends DatabaseTestSupport {

    private static final String PASSWORD = "secret123";

    private AuthService auth;
    private DocumentService service;
    private Document doc;

    @BeforeEach
    void setUpService() {
        auth = new AuthService(userDao);
        service = new DocumentService(documentDao, cellDao, database, auth);
        doc = new Document("Тест", 1);
    }

    private void set(String a1, String raw) {
        service.setCellValue(doc, CellRef.parse(a1), raw);
    }

    private String display(String a1) {
        return doc.getCell(CellRef.parse(a1)).getDisplayValue();
    }

    @Test
    @DisplayName("формула вычисляется при вводе")
    void evaluatesOnInput() {
        set("A1", "2");
        set("B1", "=A1*3");

        assertThat(display("B1")).isEqualTo("6");
        assertThat(doc.isModified()).isTrue();
    }

    @Test
    @DisplayName("каскадный пересчёт: изменение A1 обновляет B1 и C1")
    void cascadeRecalculation() {
        set("A1", "1");
        set("B1", "=A1*2");
        set("C1", "=B1+5");
        assertThat(display("C1")).isEqualTo("7");

        set("A1", "4");

        assertThat(display("B1")).isEqualTo("8");
        assertThat(display("C1")).isEqualTo("13");
    }

    @Test
    @DisplayName("изменение ячейки диапазона обновляет функцию")
    void rangeRecalculation() {
        set("A1", "1");
        set("A2", "2");
        set("A3", "3");
        set("B1", "=SUM(A1:A3)");
        assertThat(display("B1")).isEqualTo("6");

        set("A2", "20");

        assertThat(display("B1")).isEqualTo("24");
    }

    @Test
    @DisplayName("прямой цикл: A1 = B1, B1 = A1")
    void directCycle() {
        set("A1", "=B1");
        set("B1", "=A1");

        assertThat(display("A1")).isEqualTo(FormulaException.CYCLE);
        assertThat(display("B1")).isEqualTo(FormulaException.CYCLE);
    }

    @Test
    @DisplayName("самоссылка: A1 = A1 + 1")
    void selfReference() {
        set("A1", "=A1+1");

        assertThat(display("A1")).isEqualTo(FormulaException.CYCLE);
    }

    @Test
    @DisplayName("транзитивный цикл через три ячейки")
    void transitiveCycle() {
        set("A1", "=C1+1");
        set("B1", "=A1+1");
        set("C1", "=B1+1");

        assertThat(display("A1")).isEqualTo(FormulaException.CYCLE);
        assertThat(display("B1")).isEqualTo(FormulaException.CYCLE);
        assertThat(display("C1")).isEqualTo(FormulaException.CYCLE);
    }

    @Test
    @DisplayName("исправление формулы снимает код цикла")
    void cycleIsResolved() {
        set("A1", "=B1");
        set("B1", "=A1");

        set("B1", "3");

        assertThat(display("B1")).isEqualTo("3");
        assertThat(display("A1")).isEqualTo("3");
    }

    @Test
    @DisplayName("перезапись формулы литералом удаляет зависимость")
    void literalRemovesDependency() {
        set("A1", "1");
        set("B1", "=A1*2");
        assertThat(display("B1")).isEqualTo("2");

        set("B1", "10");
        set("A1", "7");

        assertThat(display("B1")).isEqualTo("10");
    }

    @Test
    @DisplayName("код ошибки распространяется на зависимые ячейки и снимается при исправлении")
    void errorPropagation() {
        set("A1", "=1/0");
        set("B1", "=A1+1");

        assertThat(display("A1")).isEqualTo(FormulaException.DIV_BY_ZERO);
        assertThat(display("B1")).isEqualTo(FormulaException.DIV_BY_ZERO);

        set("A1", "=10/2");

        assertThat(display("A1")).isEqualTo("5");
        assertThat(display("B1")).isEqualTo("6");
    }

    @Test
    @DisplayName("ошибочная формула сохраняет код ошибки в ячейке")
    void formulaErrors() {
        set("A1", "=FOO(1)");
        set("B1", "=2+");

        assertThat(display("A1")).isEqualTo(FormulaException.NAME);
        assertThat(display("B1")).isEqualTo(FormulaException.ERROR);
    }

    @Test
    @DisplayName("recalculateAll восстанавливает значения формул документа")
    void recalculateAll() {
        doc.getCell(CellRef.parse("A1")).setRaw("2");
        doc.getCell(CellRef.parse("B1")).setRaw("=A1*3");
        doc.getCell(CellRef.parse("C1")).setRaw("=B1+1");
        doc.getCell(CellRef.parse("D1")).setRaw("=2+2");

        service.recalculateAll(doc);

        assertThat(display("B1")).isEqualTo("6");
        assertThat(display("C1")).isEqualTo("7");
        assertThat(display("D1")).isEqualTo("4");
    }

    @Test
    @DisplayName("после сохранения и открытия формулы пересчитаны")
    void recalculatesAfterOpen() {
        auth.register("user", PASSWORD);
        auth.login("user", PASSWORD);
        Document saved = service.create("Смета");
        service.setCellValue(saved, CellRef.parse("A1"), "5");
        service.setCellValue(saved, CellRef.parse("A2"), "7");
        service.setCellValue(saved, CellRef.parse("B1"), "=SUM(A1:A2)*2");
        service.save(saved);

        Document reopened = service.open(saved.getId());

        assertThat(reopened.getCell(CellRef.parse("B1")).getRaw()).isEqualTo("=SUM(A1:A2)*2");
        assertThat(reopened.getCell(CellRef.parse("B1")).getDisplayValue()).isEqualTo("24");
        assertThat(reopened.isModified()).isFalse();
    }
}
