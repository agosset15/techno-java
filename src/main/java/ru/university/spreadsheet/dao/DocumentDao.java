package ru.university.spreadsheet.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import ru.university.spreadsheet.model.Document;

/**
 * Доступ к таблице {@code documents} — метаданным табличных документов.
 * <p>
 * Ячейки документа читаются и записываются отдельно через {@link CellDao}.
 * Даты хранятся в формате ISO-8601 ({@code yyyy-MM-ddTHH:mm:ss}).
 */
public class DocumentDao {

    private static final String SQL_FIND_BY_ID =
            "SELECT id, name, owner_id, created_at, updated_at FROM documents WHERE id = ?";
    private static final String SQL_FIND_BY_OWNER =
            "SELECT id, name, owner_id, created_at, updated_at FROM documents "
                    + "WHERE owner_id = ? ORDER BY updated_at DESC, id DESC";
    private static final String SQL_FIND_ALL =
            "SELECT id, name, owner_id, created_at, updated_at FROM documents ORDER BY updated_at DESC, id DESC";
    private static final String SQL_INSERT =
            "INSERT INTO documents (name, owner_id, created_at, updated_at) VALUES (?, ?, ?, ?)";
    private static final String SQL_UPDATE =
            "UPDATE documents SET name = ?, updated_at = ? WHERE id = ?";
    private static final String SQL_DELETE =
            "DELETE FROM documents WHERE id = ?";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Database database;

    /**
     * Создаёт DAO, работающий с общим экземпляром {@link Database}.
     */
    public DocumentDao() {
        this(Database.getInstance());
    }

    /**
     * Создаёт DAO, работающий с указанной базой данных.
     *
     * @param database база данных
     */
    public DocumentDao(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /**
     * Ищет документ по идентификатору. Ячейки документа не загружаются.
     *
     * @param id идентификатор документа
     * @return документ или пустой {@link Optional}
     * @throws DataAccessException при ошибке базы данных
     */
    public Optional<Document> findById(int id) {
        try (PreparedStatement statement = connection().prepareStatement(SQL_FIND_BY_ID)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка поиска документа id=" + id, e);
        }
    }

    /**
     * Возвращает документы владельца, начиная с последних изменённых.
     *
     * @param ownerId идентификатор владельца
     * @return список документов; пустой, если документов нет
     * @throws DataAccessException при ошибке базы данных
     */
    public List<Document> findByOwner(int ownerId) {
        try (PreparedStatement statement = connection().prepareStatement(SQL_FIND_BY_OWNER)) {
            statement.setInt(1, ownerId);
            try (ResultSet rs = statement.executeQuery()) {
                List<Document> documents = new ArrayList<>();
                while (rs.next()) {
                    documents.add(mapRow(rs));
                }
                return documents;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка чтения документов владельца id=" + ownerId, e);
        }
    }

    /**
     * Сохраняет метаданные нового документа и присваивает ему идентификатор.
     *
     * @param doc документ с идентификатором {@code 0}
     * @return тот же документ с присвоенным идентификатором
     * @throws IllegalArgumentException если документ уже имеет идентификатор
     * @throws DataAccessException      при ошибке базы данных, например если владелец не существует
     */
    public Document save(Document doc) {
        Objects.requireNonNull(doc, "doc");
        if (doc.getId() != 0) {
            throw new IllegalArgumentException("Документ уже сохранён: id=" + doc.getId());
        }
        try (PreparedStatement statement =
                     connection().prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, doc.getName());
            statement.setInt(2, doc.getOwnerId());
            statement.setString(3, DATE_FORMAT.format(doc.getCreatedAt()));
            statement.setString(4, DATE_FORMAT.format(doc.getUpdatedAt()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("База данных не вернула идентификатор документа");
                }
                doc.setId(keys.getInt(1));
                return doc;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка сохранения документа " + doc.getName(), e);
        }
    }

    /**
     * Обновляет имя и дату изменения существующего документа.
     *
     * @param doc документ с ненулевым идентификатором
     * @throws DataAccessException если документ не найден или произошла ошибка базы данных
     */
    public void update(Document doc) {
        Objects.requireNonNull(doc, "doc");
        try (PreparedStatement statement = connection().prepareStatement(SQL_UPDATE)) {
            statement.setString(1, doc.getName());
            statement.setString(2, DATE_FORMAT.format(doc.getUpdatedAt()));
            statement.setInt(3, doc.getId());
            if (statement.executeUpdate() == 0) {
                throw new DataAccessException("Документ не найден: id=" + doc.getId());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка обновления документа id=" + doc.getId(), e);
        }
    }

    /**
     * Удаляет документ вместе с его ячейками (каскадно).
     * Удаление несуществующего документа не считается ошибкой.
     *
     * @param id идентификатор документа
     * @throws DataAccessException при ошибке базы данных
     */
    public void delete(int id) {
        try (PreparedStatement statement = connection().prepareStatement(SQL_DELETE)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка удаления документа id=" + id, e);
        }
    }

    private static Document mapRow(ResultSet rs) throws SQLException {
        try {
            return new Document(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getInt("owner_id"),
                    LocalDateTime.parse(rs.getString("created_at"), DATE_FORMAT),
                    LocalDateTime.parse(rs.getString("updated_at"), DATE_FORMAT));
        } catch (DateTimeParseException e) {
            throw new DataAccessException("Некорректная дата в записи документа", e);
        }
    }

    private Connection connection() {
        return database.getConnection();
    }

    /**
     * Возвращает все документы, начиная с последних изменённых. Используется
     * администратором, которому доступны документы всех пользователей.
     *
     * @return список документов; пустой, если документов нет
     * @throws DataAccessException при ошибке базы данных
     */
    public List<Document> findAll() {
        try (PreparedStatement statement = connection().prepareStatement(SQL_FIND_ALL);
             ResultSet rs = statement.executeQuery()) {
            List<Document> documents = new ArrayList<>();
            while (rs.next()) {
                documents.add(mapRow(rs));
            }
            return documents;
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка чтения списка документов", e);
        }
    }
}
