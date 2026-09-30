package ru.university.spreadsheet.service;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.university.spreadsheet.dao.DatabaseTestSupport;
import ru.university.spreadsheet.model.CellRange;
import ru.university.spreadsheet.model.CellRef;
import ru.university.spreadsheet.model.CellStyle;
import ru.university.spreadsheet.model.Document;

@DisplayName("StyleService: оформление ячеек")
class StyleServiceTest extends DatabaseTestSupport {

    private static final String PASSWORD = "secret123";
    private static final CellStyle HIGHLIGHT = new CellStyle(Color.YELLOW, Color.BLUE, true);

    private AuthService auth;
    private DocumentService documents;
    private StyleService service;
    private Document doc;

    @BeforeEach
    void setUpService() {
        auth = new AuthService(userDao);
        documents = new DocumentService(documentDao, cellDao, database, auth);
        service = new StyleService();
        doc = new Document("Тест", 1);
    }

    private static CellRange range(String from, String to) {
        return new CellRange(CellRef.parse(from), CellRef.parse(to));
    }

    private CellStyle styleOf(String a1) {
        return doc.getCell(CellRef.parse(a1)).getStyle();
    }

    @Test
    @DisplayName("applyStyle применяет стиль ко всем ячейкам диапазона, создавая отсутствующие")
    void applyStyleToRange() {
        service.applyStyle(doc, range("A1", "C5"), HIGHLIGHT);

        assertThat(doc.getNonEmptyCells()).hasSize(15);
        assertThat(styleOf("A1")).isEqualTo(HIGHLIGHT);
        assertThat(styleOf("B3")).isEqualTo(HIGHLIGHT);
        assertThat(styleOf("C5")).isEqualTo(HIGHLIGHT);
        assertThat(styleOf("D5")).isEqualTo(CellStyle.DEFAULT);
    }

    @Test
    @DisplayName("применение стиля помечает документ изменённым")
    void marksModified() {
        doc.markSaved();

        service.applyStyle(doc, CellRange.of(CellRef.parse("A1")), HIGHLIGHT);

        assertThat(doc.isModified()).isTrue();
    }

    @Test
    @DisplayName("изменение цвета фона сохраняет цвет текста и начертание")
    void applyBackgroundKeepsOtherAttributes() {
        service.applyStyle(doc, CellRange.of(CellRef.parse("A1")), HIGHLIGHT);

        service.applyBackground(doc, CellRange.of(CellRef.parse("A1")), Color.RED);

        assertThat(styleOf("A1")).isEqualTo(new CellStyle(Color.RED, Color.BLUE, true));
    }

    @Test
    @DisplayName("изменение цвета текста и начертания сохраняет остальные свойства")
    void applyForegroundAndBold() {
        service.applyForeground(doc, range("A1", "A2"), Color.GREEN);
        service.applyBold(doc, range("A1", "A2"), true);

        assertThat(styleOf("A1")).isEqualTo(new CellStyle(Color.WHITE, Color.GREEN, true));
        assertThat(styleOf("A2")).isEqualTo(new CellStyle(Color.WHITE, Color.GREEN, true));

        service.applyBold(doc, range("A1", "A2"), false);

        assertThat(styleOf("A1").bold()).isFalse();
    }

    @Test
    @DisplayName("resetStyle возвращает стиль по умолчанию")
    void resetStyle() {
        service.applyStyle(doc, range("A1", "B2"), HIGHLIGHT);

        service.resetStyle(doc, range("A1", "B2"));

        assertThat(styleOf("A1").isDefault()).isTrue();
        assertThat(styleOf("B2").isDefault()).isTrue();
    }

    @Test
    @DisplayName("стили диапазона сохраняются и восстанавливаются после открытия документа")
    void stylesSurviveSaveAndOpen() {
        auth.register("user", PASSWORD);
        auth.login("user", PASSWORD);
        Document saved = documents.create("Смета");
        documents.setCellValue(saved, CellRef.parse("A1"), "42");
        service.applyStyle(saved, range("A1", "C5"), HIGHLIGHT);

        documents.save(saved);
        Document reopened = documents.open(saved.getId());

        assertThat(reopened.getNonEmptyCells()).hasSize(15);
        assertThat(reopened.getCell(CellRef.parse("A1")).getStyle()).isEqualTo(HIGHLIGHT);
        assertThat(reopened.getCell(CellRef.parse("C5")).getStyle()).isEqualTo(HIGHLIGHT);
        assertThat(reopened.getCell(CellRef.parse("A1")).getRaw()).isEqualTo("42");
        assertThat(reopened.getCell(CellRef.parse("D6")).getStyle()).isEqualTo(CellStyle.DEFAULT);
    }
}
