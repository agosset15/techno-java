package ru.university.spreadsheet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.university.spreadsheet.dao.DataAccessException;
import ru.university.spreadsheet.dao.DatabaseTestSupport;
import ru.university.spreadsheet.model.CellRef;
import ru.university.spreadsheet.model.CellStyle;
import ru.university.spreadsheet.model.Document;
import ru.university.spreadsheet.model.User;

@DisplayName("DocumentService: создание, открытие и сохранение документов")
class DocumentServiceTest extends DatabaseTestSupport {

    private static final String PASSWORD = "secret123";
    private static final CellRef A1 = CellRef.parse("A1");
    private static final CellRef B2 = CellRef.parse("B2");

    private AuthService auth;
    private DocumentService service;
    private User admin;
    private User user;

    @BeforeEach
    void setUpService() {
        auth = new AuthService(userDao);
        service = new DocumentService(documentDao, cellDao, database, auth);
        admin = auth.register("admin", PASSWORD);
        user = auth.register("user", PASSWORD);
        auth.login("user", PASSWORD);
    }

    @Test
    @DisplayName("create сохраняет документ с текущим пользователем в качестве владельца")
    void create() {
        Document doc = service.create("  Смета  ");

        assertThat(doc.getId()).isPositive();
        assertThat(doc.getName()).isEqualTo("Смета");
        assertThat(doc.getOwnerId()).isEqualTo(user.getId());
        assertThat(doc.isModified()).isFalse();
        assertThat(documentDao.findById(doc.getId())).isPresent();
    }

    @Test
    @DisplayName("create отклоняет пустое имя и работу без входа")
    void createValidation() {
        assertThatThrownBy(() -> service.create("   ")).isInstanceOf(ServiceException.class);

        auth.logout();
        assertThatThrownBy(() -> service.create("Смета")).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("setCellValue записывает текст и помечает документ, повтор значения не помечает")
    void setCellValue() {
        Document doc = service.create("Смета");

        service.setCellValue(doc, A1, "42");
        assertThat(doc.getCell(A1).getRaw()).isEqualTo("42");
        assertThat(doc.isModified()).isTrue();

        doc.markSaved();
        service.setCellValue(doc, A1, "42");
        assertThat(doc.isModified()).isFalse();
    }

    @Test
    @DisplayName("сохранённые значения и стили восстанавливаются при открытии")
    void saveAndOpen() {
        Document doc = service.create("Смета");
        LocalDateTime createdUpdatedAt = doc.getUpdatedAt();
        service.setCellValue(doc, A1, "42");
        service.setCellValue(doc, B2, "Текст");
        doc.getCell(B2).setStyle(new CellStyle(Color.YELLOW, Color.BLUE, true));

        service.save(doc);

        assertThat(doc.isModified()).isFalse();
        assertThat(doc.getUpdatedAt()).isAfterOrEqualTo(createdUpdatedAt);
        Document reopened = service.open(doc.getId());
        assertThat(reopened.isModified()).isFalse();
        assertThat(reopened.getCell(A1).getRaw()).isEqualTo("42");
        assertThat(reopened.getCell(B2).getRaw()).isEqualTo("Текст");
        assertThat(reopened.getCell(B2).getStyle()).isEqualTo(new CellStyle(Color.YELLOW, Color.BLUE, true));
    }

    @Test
    @DisplayName("очищенная ячейка не сохраняется")
    void clearedCellIsRemoved() {
        Document doc = service.create("Смета");
        service.setCellValue(doc, A1, "1");
        service.save(doc);

        service.setCellValue(doc, A1, "");
        service.save(doc);

        assertThat(cellDao.loadCells(doc.getId())).isEmpty();
    }

    @Test
    @DisplayName("документ без изменений не записывается")
    void unmodifiedIsNotWritten() {
        Document doc = service.create("Смета");
        documentDao.delete(doc.getId());

        service.save(doc);

        assertThat(documentDao.findById(doc.getId())).isEmpty();
    }

    @Test
    @DisplayName("ошибка записи ячеек откатывает обновление метаданных и сохраняет флаг изменений")
    void saveRollsBack() throws SQLException {
        Document doc = service.create("Смета");
        LocalDateTime updatedAt = doc.getUpdatedAt();
        try (Statement statement = database.getConnection().createStatement()) {
            statement.execute("CREATE TRIGGER fail_cells BEFORE INSERT ON cells "
                    + "BEGIN SELECT RAISE(ABORT, 'сбой записи'); END");
        }
        doc.setName("Новое имя");
        service.setCellValue(doc, A1, "1");

        assertThatThrownBy(() -> service.save(doc)).isInstanceOf(DataAccessException.class);

        assertThat(doc.isModified()).isTrue();
        assertThat(doc.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(documentDao.findById(doc.getId()).orElseThrow().getName()).isEqualTo("Смета");
        assertThat(database.getConnection().getAutoCommit()).isTrue();
    }

    @Test
    @DisplayName("пользователь не может открыть чужой документ, администратор может")
    void accessControl() {
        auth.login("admin", PASSWORD);
        Document adminDoc = service.create("Документ администратора");
        auth.login("user", PASSWORD);

        assertThatThrownBy(() -> service.open(adminDoc.getId())).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.listByOwner(admin.getId())).isInstanceOf(AuthException.class);

        Document userDoc = service.create("Документ пользователя");
        auth.login("admin", PASSWORD);
        assertThat(service.open(userDoc.getId()).getName()).isEqualTo("Документ пользователя");
        assertThat(service.listByOwner(user.getId())).extracting(Document::getId).containsExactly(userDoc.getId());
    }

    @Test
    @DisplayName("открытие несуществующего документа отклоняется")
    void openMissing() {
        assertThatThrownBy(() -> service.open(999)).isInstanceOf(ServiceException.class);
    }
}
