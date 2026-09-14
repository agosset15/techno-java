package ru.university.spreadsheet.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.university.spreadsheet.model.Cell;
import ru.university.spreadsheet.model.CellRef;
import ru.university.spreadsheet.model.CellStyle;
import ru.university.spreadsheet.model.Document;
import ru.university.spreadsheet.model.Role;
import ru.university.spreadsheet.model.User;

@DisplayName("CellDao")
class CellDaoTest extends DatabaseTestSupport {

    private static final CellStyle YELLOW_BLUE_BOLD = new CellStyle(Color.YELLOW, Color.BLUE, true);
    private static final CellStyle CUSTOM = new CellStyle(Color.web("#1A2B3C"), Color.web("#FEDCBA"), false);

    private Document doc;

    @BeforeEach
    void createDocument() {
        User owner = saveUser("owner", Role.USER);
        doc = documentDao.save(new Document("Смета", owner.getId()));
    }

    @Test
    @DisplayName("пользователь, документ и 3 ячейки читаются обратно с теми же значениями и цветами")
    void roundTripThreeCells() {
        List<Cell> cells = List.of(
                new Cell(CellRef.parse("A1"), "42", YELLOW_BLUE_BOLD),
                new Cell(CellRef.parse("B2"), "Текст"),
                new Cell(CellRef.parse("AA10"), "=A1*2", CUSTOM));

        cellDao.replaceCells(doc.getId(), cells);
        Map<CellRef, Cell> loaded = cellDao.loadCells(doc.getId());

        assertThat(loaded).hasSize(3);
        for (Cell expected : cells) {
            Cell actual = loaded.get(expected.getRef());
            assertThat(actual).as("ячейка %s", expected.getRef()).isNotNull();
            assertThat(actual.getRaw()).isEqualTo(expected.getRaw());
            assertThat(actual.getStyle()).isEqualTo(expected.getStyle());
            assertThat(CellStyle.toHex(actual.getStyle().bg())).isEqualTo(CellStyle.toHex(expected.getStyle().bg()));
            assertThat(CellStyle.toHex(actual.getStyle().fg())).isEqualTo(CellStyle.toHex(expected.getStyle().fg()));
            assertThat(actual.getStyle().bold()).isEqualTo(expected.getStyle().bold());
        }
    }

    @Test
    @DisplayName("ячейки загружаются в порядке строк и столбцов")
    void loadsInPositionOrder() {
        cellDao.replaceCells(doc.getId(), List.of(
                new Cell(CellRef.parse("B2"), "4"),
                new Cell(CellRef.parse("A2"), "3"),
                new Cell(CellRef.parse("B1"), "2"),
                new Cell(CellRef.parse("A1"), "1")));

        assertThat(cellDao.loadCells(doc.getId()).keySet())
                .extracting(CellRef::toA1)
                .containsExactly("A1", "B1", "A2", "B2");
    }

    @Test
    @DisplayName("пустые ячейки не сохраняются, ячейка только со стилем сохраняется")
    void skipsEmptyCells() {
        cellDao.replaceCells(doc.getId(), List.of(
                new Cell(CellRef.parse("A1")),
                new Cell(CellRef.parse("A2"), ""),
                new Cell(CellRef.parse("A3"), "", YELLOW_BLUE_BOLD)));

        assertThat(cellDao.loadCells(doc.getId()).keySet()).containsExactly(CellRef.parse("A3"));
    }

    @Test
    @DisplayName("replaceCells удаляет прежние ячейки документа")
    void replacesPreviousCells() {
        cellDao.replaceCells(doc.getId(), List.of(new Cell(CellRef.parse("A1"), "old")));

        cellDao.replaceCells(doc.getId(), List.of(new Cell(CellRef.parse("B1"), "new")));

        Map<CellRef, Cell> loaded = cellDao.loadCells(doc.getId());
        assertThat(loaded.keySet()).containsExactly(CellRef.parse("B1"));
        assertThat(loaded.get(CellRef.parse("B1")).getRaw()).isEqualTo("new");
    }

    @Test
    @DisplayName("replaceCells не затрагивает ячейки других документов")
    void keepsOtherDocuments() {
        Document other = documentDao.save(new Document("Другой", doc.getOwnerId()));
        cellDao.replaceCells(other.getId(), List.of(new Cell(CellRef.parse("C3"), "чужая")));

        cellDao.replaceCells(doc.getId(), List.of(new Cell(CellRef.parse("A1"), "своя")));

        assertThat(cellDao.loadCells(other.getId())).containsOnlyKeys(CellRef.parse("C3"));
    }

    @Test
    @DisplayName("при ошибке вставки транзакция откатывается и прежние ячейки сохраняются")
    void rollsBackOnFailure() throws SQLException {
        cellDao.replaceCells(doc.getId(), List.of(new Cell(CellRef.parse("A1"), "исходное")));
        List<Cell> duplicates = List.of(
                new Cell(CellRef.parse("B1"), "1"),
                new Cell(CellRef.parse("B1"), "2"));

        assertThatThrownBy(() -> cellDao.replaceCells(doc.getId(), duplicates))
                .isInstanceOf(DataAccessException.class);

        Map<CellRef, Cell> loaded = cellDao.loadCells(doc.getId());
        assertThat(loaded.keySet()).containsExactly(CellRef.parse("A1"));
        assertThat(loaded.get(CellRef.parse("A1")).getRaw()).isEqualTo("исходное");
        assertThat(database.getConnection().getAutoCommit()).isTrue();
    }

    @Test
    @DisplayName("внутри внешней транзакции подтверждение и откат выполняет вызывающий код")
    void participatesInOuterTransaction() throws SQLException {
        cellDao.replaceCells(doc.getId(), List.of(new Cell(CellRef.parse("A1"), "до")));
        Connection connection = database.getConnection();
        connection.setAutoCommit(false);
        try {
            cellDao.replaceCells(doc.getId(), List.of(new Cell(CellRef.parse("Z9"), "после")));
            connection.rollback();
        } finally {
            connection.setAutoCommit(true);
        }

        assertThat(cellDao.loadCells(doc.getId()).keySet()).containsExactly(CellRef.parse("A1"));
    }

    @Test
    @DisplayName("удаление документа каскадно удаляет его ячейки")
    void cascadeOnDocumentDelete() {
        cellDao.replaceCells(doc.getId(), List.of(new Cell(CellRef.parse("A1"), "1")));

        documentDao.delete(doc.getId());

        assertThat(cellDao.loadCells(doc.getId())).isEmpty();
    }

    @Test
    @DisplayName("loadCells для документа без ячеек возвращает пустой словарь")
    void loadEmpty() {
        assertThat(cellDao.loadCells(doc.getId())).isEmpty();
    }
}
