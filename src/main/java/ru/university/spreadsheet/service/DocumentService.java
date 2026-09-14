package ru.university.spreadsheet.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import ru.university.spreadsheet.dao.CellDao;
import ru.university.spreadsheet.dao.DataAccessException;
import ru.university.spreadsheet.dao.Database;
import ru.university.spreadsheet.dao.DocumentDao;
import ru.university.spreadsheet.model.Cell;
import ru.university.spreadsheet.model.CellRef;
import ru.university.spreadsheet.model.Document;
import ru.university.spreadsheet.model.Role;
import ru.university.spreadsheet.model.User;

/**
 * Работа с табличными документами: создание, открытие, сохранение и изменение ячеек.
 * <p>
 * Пользователь работает только со своими документами; администратору доступны все.
 */
public class DocumentService {

    private static final Logger LOG = Logger.getLogger(DocumentService.class.getName());

    private final DocumentDao documentDao;
    private final CellDao cellDao;
    private final Database database;
    private final AuthService authService;

    /**
     * Создаёт сервис, работающий с базой данных по умолчанию.
     *
     * @param authService сервис аутентификации, хранящий текущего пользователя
     */
    public DocumentService(AuthService authService) {
        this(new DocumentDao(), new CellDao(), Database.getInstance(), authService);
    }

    /**
     * Создаёт сервис с указанными компонентами доступа к данным.
     *
     * @param documentDao DAO документов
     * @param cellDao     DAO ячеек
     * @param database    база данных, используемая для управления транзакциями
     * @param authService сервис аутентификации, хранящий текущего пользователя
     */
    public DocumentService(DocumentDao documentDao, CellDao cellDao, Database database, AuthService authService) {
        this.documentDao = Objects.requireNonNull(documentDao, "documentDao");
        this.cellDao = Objects.requireNonNull(cellDao, "cellDao");
        this.database = Objects.requireNonNull(database, "database");
        this.authService = Objects.requireNonNull(authService, "authService");
    }

    /**
     * Создаёт пустой документ, владельцем которого становится текущий пользователь,
     * и сохраняет его метаданные в базе данных.
     *
     * @param name имя документа; пробелы по краям отбрасываются
     * @return сохранённый документ без несохранённых изменений
     * @throws AuthException    если вход не выполнен
     * @throws ServiceException если имя пустое
     */
    public Document create(String name) {
        User owner = requireUser();
        if (name == null || name.isBlank()) {
            throw new ServiceException("Имя документа не может быть пустым");
        }
        Document doc = documentDao.save(new Document(name.strip(), owner.getId()));
        LOG.info(() -> "Создан документ " + doc.getName() + " (id=" + doc.getId() + ")");
        return doc;
    }

    /**
     * Открывает документ: загружает метаданные и ячейки.
     *
     * @param id идентификатор документа
     * @return документ без несохранённых изменений
     * @throws AuthException    если вход не выполнен или документ принадлежит другому пользователю
     * @throws ServiceException если документ не найден
     */
    public Document open(int id) {
        User user = requireUser();
        Document doc = documentDao.findById(id)
                .orElseThrow(() -> new ServiceException("Документ не найден: id=" + id));
        requireAccess(user, doc.getOwnerId());
        cellDao.loadCells(id).forEach(doc::setCell);
        doc.markSaved();
        LOG.info(() -> "Открыт документ " + doc.getName() + " (id=" + id + ")");
        return doc;
    }

    /**
     * Возвращает документы указанного владельца, начиная с последних изменённых.
     *
     * @param ownerId идентификатор владельца
     * @return список документов; пустой, если документов нет
     * @throws AuthException если вход не выполнен или запрошены чужие документы
     *                       пользователем без роли администратора
     */
    public List<Document> listByOwner(int ownerId) {
        requireAccess(requireUser(), ownerId);
        return documentDao.findByOwner(ownerId);
    }

    /**
     * Сохраняет документ в одной транзакции: обновляет метаданные и дату изменения,
     * затем заменяет ячейки. При отсутствии изменений запись не выполняется.
     * При ошибке транзакция откатывается, а флаг изменений и дата изменения сохраняют
     * прежние значения.
     *
     * @param doc документ
     * @throws AuthException       если вход не выполнен или документ чужой
     * @throws DataAccessException при ошибке базы данных
     */
    public void save(Document doc) {
        Objects.requireNonNull(doc, "doc");
        requireAccess(requireUser(), doc.getOwnerId());
        if (!doc.isModified()) {
            return;
        }
        LocalDateTime previousUpdatedAt = doc.getUpdatedAt();
        Connection connection = database.getConnection();
        beginTransaction(connection);
        try {
            doc.setUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            documentDao.update(doc);
            cellDao.replaceCells(doc.getId(), doc.getNonEmptyCells());
            connection.commit();
            doc.markSaved();
            LOG.info(() -> "Сохранён документ " + doc.getName() + " (id=" + doc.getId() + ")");
        } catch (SQLException e) {
            DataAccessException failure = new DataAccessException("Не удалось сохранить документ", e);
            rollback(connection, failure);
            doc.setUpdatedAt(previousUpdatedAt);
            throw failure;
        } catch (RuntimeException e) {
            rollback(connection, e);
            doc.setUpdatedAt(previousUpdatedAt);
            throw e;
        } finally {
            restoreAutoCommit(connection);
        }
    }

    /**
     * Записывает введённое пользователем значение в ячейку и помечает документ изменённым.
     * Если значение не изменилось, документ не помечается.
     *
     * @param doc   документ
     * @param ref   адрес ячейки
     * @param value исходный текст; {@code null} трактуется как пустая строка
     */
    public void setCellValue(Document doc, CellRef ref, String value) {
        Objects.requireNonNull(doc, "doc");
        Objects.requireNonNull(ref, "ref");
        String raw = value == null ? "" : value;
        Cell cell = doc.getCell(ref);
        if (cell.getRaw().equals(raw)) {
            return;
        }
        cell.setRaw(raw);
        doc.setModified(true);
    }

    private User requireUser() {
        return authService.getCurrentUser()
                .orElseThrow(() -> new AuthException("Необходимо выполнить вход"));
    }

    private static void requireAccess(User user, int ownerId) {
        if (user.getId() != ownerId && user.getRole() != Role.ADMIN) {
            throw new AuthException("Документ принадлежит другому пользователю");
        }
    }

    private static void beginTransaction(Connection connection) {
        try {
            connection.setAutoCommit(false);
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
