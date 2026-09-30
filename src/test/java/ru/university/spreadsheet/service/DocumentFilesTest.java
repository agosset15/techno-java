package ru.university.spreadsheet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.university.spreadsheet.dao.DatabaseTestSupport;
import ru.university.spreadsheet.model.CellRef;
import ru.university.spreadsheet.model.CellStyle;
import ru.university.spreadsheet.model.Document;
import ru.university.spreadsheet.model.User;

@DisplayName("DocumentService: список документов, удаление и «Сохранить как»")
class DocumentFilesTest extends DatabaseTestSupport {

    private static final String PASSWORD = "secret123";
    private static final CellStyle HIGHLIGHT = new CellStyle(Color.YELLOW, Color.BLUE, true);

    private AuthService auth;
    private DocumentService service;
    private StyleService styles;
    private User admin;
    private User user;

    @BeforeEach
    void setUpService() {
        auth = new AuthService(userDao);
        service = new DocumentService(documentDao, cellDao, database, auth);
        styles = new StyleService();
        admin = auth.register("admin", PASSWORD);
        user = auth.register("user", PASSWORD);
        auth.login("user", PASSWORD);
    }

    @Test
    @DisplayName("пользователь видит только свои документы, администратор — все")
    void listAvailable() {
        Document mine = service.create("Мой документ");
        auth.login("admin", PASSWORD);
        Document adminDoc = service.create("Документ администратора");

        assertThat(service.listAvailable()).extracting(Document::getId)
                .containsExactlyInAnyOrder(mine.getId(), adminDoc.getId());

        auth.login("user", PASSWORD);
        assertThat(service.listAvailable()).extracting(Document::getId).containsExactly(mine.getId());
    }

    @Test
    @DisplayName("delete удаляет документ вместе с ячейками")
    void delete() {
        Document doc = service.create("Смета");
        service.setCellValue(doc, CellRef.parse("A1"), "42");
        service.save(doc);

        service.delete(doc.getId());

        assertThat(documentDao.findById(doc.getId())).isEmpty();
        assertThat(cellDao.loadCells(doc.getId())).isEmpty();
    }

    @Test
    @DisplayName("нельзя удалить чужой или несуществующий документ")
    void deleteRejected() {
        auth.login("admin", PASSWORD);
        Document adminDoc = service.create("Документ администратора");
        auth.login("user", PASSWORD);

        assertThatThrownBy(() -> service.delete(adminDoc.getId())).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.delete(999)).isInstanceOf(ServiceException.class);
        assertThat(documentDao.findById(adminDoc.getId())).isPresent();
    }

    @Test
    @DisplayName("администратор может удалить чужой документ")
    void adminDeletesForeignDocument() {
        Document mine = service.create("Мой документ");
        auth.login("admin", PASSWORD);

        service.delete(mine.getId());

        assertThat(documentDao.findById(mine.getId())).isEmpty();
    }

    @Test
    @DisplayName("saveAs создаёт копию с теми же значениями, формулами и стилями")
    void saveAs() {
        Document original = service.create("Смета");
        service.setCellValue(original, CellRef.parse("A1"), "5");
        service.setCellValue(original, CellRef.parse("A2"), "7");
        service.setCellValue(original, CellRef.parse("B1"), "=SUM(A1:A2)");
        styles.applyStyle(original, new ru.university.spreadsheet.model.CellRange(
                CellRef.parse("A1"), CellRef.parse("A2")), HIGHLIGHT);
        service.save(original);

        Document copy = service.saveAs(original, "  Смета (копия)  ");

        assertThat(copy.getId()).isNotEqualTo(original.getId());
        assertThat(copy.getName()).isEqualTo("Смета (копия)");
        assertThat(copy.getOwnerId()).isEqualTo(user.getId());
        assertThat(copy.isModified()).isFalse();

        Document reopened = service.open(copy.getId());
        assertThat(reopened.getCell(CellRef.parse("B1")).getRaw()).isEqualTo("=SUM(A1:A2)");
        assertThat(reopened.getCell(CellRef.parse("B1")).getDisplayValue()).isEqualTo("12");
        assertThat(reopened.getCell(CellRef.parse("A1")).getStyle()).isEqualTo(HIGHLIGHT);
    }

    @Test
    @DisplayName("saveAs не меняет исходный документ")
    void saveAsKeepsOriginal() {
        Document original = service.create("Смета");
        service.setCellValue(original, CellRef.parse("A1"), "1");
        service.save(original);

        service.saveAs(original, "Копия");

        Document reopened = service.open(original.getId());
        assertThat(reopened.getName()).isEqualTo("Смета");
        assertThat(reopened.getCell(CellRef.parse("A1")).getRaw()).isEqualTo("1");
        assertThat(service.listAvailable()).hasSize(2);
    }

    @Test
    @DisplayName("saveAs отклоняет пустое имя")
    void saveAsValidation() {
        Document original = service.create("Смета");

        assertThatThrownBy(() -> service.saveAs(original, "  ")).isInstanceOf(ServiceException.class);
        assertThat(admin.getId()).isPositive();
    }
}
