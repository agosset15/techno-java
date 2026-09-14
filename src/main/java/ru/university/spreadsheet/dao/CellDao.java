package ru.university.spreadsheet.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.scene.paint.Color;
import ru.university.spreadsheet.model.Cell;
import ru.university.spreadsheet.model.CellRef;
import ru.university.spreadsheet.model.CellStyle;

/**
 * Доступ к таблице {@code cells} — содержимому и стилям ячеек документов.
 * <p>
 * Сохраняются только непустые ячейки (с текстом или стилем, отличным от стиля
 * по умолчанию). Цвета сериализуются в виде {@code #RRGGBB}.
 */
public class CellDao {

    private static final String SQL_LOAD =
            "SELECT row_idx, col_idx, raw, bg, fg, bold FROM cells WHERE doc_id = ? ORDER BY row_idx, col_idx";
    private static final String SQL_DELETE_BY_DOC =
            "DELETE FROM cells WHERE doc_id = ?";
    private static final String SQL_INSERT =
            "INSERT INTO cells (doc_id, row_idx, col_idx, raw, bg, fg, bold) VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final Logger LOG = Logger.getLogger(CellDao.class.getName());

    private final Database database;

    /**
     * Создаёт DAO, работающий с общим экземпляром {@link Database}.
     */
    public CellDao() {
        this(Database.getInstance());
    }

    /**
     * Создаёт DAO, работающий с указанной базой данных.
     *
     * @param database база данных
     */
    public CellDao(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /**
     * Загружает ячейки документа.
     *
     * @param docId идентификатор документа
     * @return словарь «адрес → ячейка», упорядоченный по строкам и столбцам; пустой, если ячеек нет
     * @throws DataAccessException при ошибке базы данных или некорректных данных в записи
     */
    public Map<CellRef, Cell> loadCells(int docId) {
        try (PreparedStatement statement = database.getConnection().prepareStatement(SQL_LOAD)) {
            statement.setInt(1, docId);
            try (ResultSet rs = statement.executeQuery()) {
                Map<CellRef, Cell> cells = new LinkedHashMap<>();
                while (rs.next()) {
                    Cell cell = mapRow(rs);
                    cells.put(cell.getRef(), cell);
                }
                return cells;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка загрузки ячеек документа id=" + docId, e);
        }
    }

    /**
     * Заменяет все ячейки документа переданными: удаляет прежние строки и пакетно
     * вставляет непустые ячейки.
     * <p>
     * Если соединение находится в режиме автоподтверждения, метод сам открывает транзакцию,
     * подтверждает её при успехе и откатывает при ошибке. Если транзакция уже открыта
     * вызывающим кодом, метод выполняется в её рамках, а подтверждение и откат остаются
     * за вызывающим кодом.
     *
     * @param docId идентификатор документа
     * @param cells ячейки документа; пустые ячейки пропускаются
     * @throws DataAccessException при ошибке базы данных; изменения при этом откатываются
     */
    public void replaceCells(int docId, Collection<Cell> cells) {
        Objects.requireNonNull(cells, "cells");
        Connection connection = database.getConnection();
        boolean ownTransaction = beginIfNeeded(connection);
        try {
            deleteAll(connection, docId);
            insertAll(connection, docId, cells);
            if (ownTransaction) {
                connection.commit();
            }
        } catch (SQLException e) {
            DataAccessException failure =
                    new DataAccessException("Ошибка сохранения ячеек документа id=" + docId, e);
            if (ownTransaction) {
                rollback(connection, failure);
            }
            throw failure;
        } catch (RuntimeException e) {
            if (ownTransaction) {
                rollback(connection, e);
            }
            throw e;
        } finally {
            if (ownTransaction) {
                restoreAutoCommit(connection);
            }
        }
    }

    private static void deleteAll(Connection connection, int docId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_DELETE_BY_DOC)) {
            statement.setInt(1, docId);
            statement.executeUpdate();
        }
    }

    private static void insertAll(Connection connection, int docId, Collection<Cell> cells) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_INSERT)) {
            int batchSize = 0;
            for (Cell cell : cells) {
                if (cell.isEmpty()) {
                    continue;
                }
                CellStyle style = cell.getStyle();
                statement.setInt(1, docId);
                statement.setInt(2, cell.getRef().row());
                statement.setInt(3, cell.getRef().col());
                statement.setString(4, cell.getRaw());
                statement.setString(5, CellStyle.toHex(style.bg()));
                statement.setString(6, CellStyle.toHex(style.fg()));
                statement.setInt(7, style.bold() ? 1 : 0);
                statement.addBatch();
                batchSize++;
            }
            if (batchSize > 0) {
                statement.executeBatch();
            }
        }
    }

    private static Cell mapRow(ResultSet rs) throws SQLException {
        CellRef ref = new CellRef(rs.getInt("row_idx"), rs.getInt("col_idx"));
        try {
            CellStyle style = new CellStyle(
                    Color.web(rs.getString("bg")),
                    Color.web(rs.getString("fg")),
                    rs.getInt("bold") != 0);
            return new Cell(ref, rs.getString("raw"), style);
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("Некорректный стиль ячейки " + ref.toA1() + " в базе данных", e);
        }
    }

    private static boolean beginIfNeeded(Connection connection) {
        try {
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new DataAccessException("Не удалось начать транзакцию", e);
        }
    }

    private static void rollback(Connection connection, RuntimeException failure) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            failure.addSuppressed(e);
        }
    }

    private static void restoreAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Не удалось восстановить режим автоподтверждения", e);
        }
    }
}
