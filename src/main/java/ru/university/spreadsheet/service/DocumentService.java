package ru.university.spreadsheet.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import ru.university.spreadsheet.dao.CellDao;
import ru.university.spreadsheet.dao.DataAccessException;
import ru.university.spreadsheet.dao.Database;
import ru.university.spreadsheet.dao.DocumentDao;
import ru.university.spreadsheet.formula.BinOpNode;
import ru.university.spreadsheet.formula.DependencyGraph;
import ru.university.spreadsheet.formula.Evaluator;
import ru.university.spreadsheet.formula.FormulaException;
import ru.university.spreadsheet.formula.FuncNode;
import ru.university.spreadsheet.formula.Lexer;
import ru.university.spreadsheet.formula.Node;
import ru.university.spreadsheet.formula.Parser;
import ru.university.spreadsheet.formula.RangeNode;
import ru.university.spreadsheet.formula.RefNode;
import ru.university.spreadsheet.formula.UnaryNode;
import ru.university.spreadsheet.model.Cell;
import ru.university.spreadsheet.model.CellRef;
import ru.university.spreadsheet.model.Document;
import ru.university.spreadsheet.model.Role;
import ru.university.spreadsheet.model.User;

/**
 * Работа с табличными документами: создание, открытие, сохранение, изменение ячеек
 * и пересчёт формул.
 * <p>
 * Пользователь работает только со своими документами, администратору доступны все.
 * Сервис хранит граф зависимостей открытого документа, поэтому одновременно
 * обслуживает один документ: при создании и открытии граф строится заново.
 */
public class DocumentService {

    private static final long MAX_RANGE_CELLS = 100_000;

    private static final Logger LOG = Logger.getLogger(DocumentService.class.getName());

    private final DocumentDao documentDao;
    private final CellDao cellDao;
    private final Database database;
    private final AuthService authService;
    private DependencyGraph graph = new DependencyGraph();

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
        graph = new DependencyGraph();
        LOG.info(() -> "Создан документ " + doc.getName() + " (id=" + doc.getId() + ")");
        return doc;
    }

    /**
     * Открывает документ: загружает метаданные и ячейки, строит граф зависимостей
     * и пересчитывает формулы.
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
        recalculateAll(doc);
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
     * Записывает введённое пользователем значение в ячейку и пересчитывает формулы.
     * <p>
     * Порядок работы: сохранение текста, перестроение зависимостей ячейки, проверка
     * графа на цикл, вычисление значения и пересчёт зависимых ячеек в топологическом
     * порядке. При цикле в ячейку записывается код {@value FormulaException#CYCLE};
     * зависимые ячейки при этом тоже пересчитываются, чтобы код цикла увидели все его
     * участники. Если значение не изменилось, ничего не происходит.
     *
     * @param doc   документ
     * @param ref   адрес ячейки
     * @param value исходный текст; null трактуется как пустая строка
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
        graph.removeDependencies(ref);
        if (cell.isFormula()) {
            registerDependencies(cell);
            recalculate(doc, ref);
        }
        for (CellRef dependent : graph.topologicalOrder(ref)) {
            recalculate(doc, dependent);
        }
        doc.setModified(true);
    }

    /**
     * Полностью перестраивает граф зависимостей и пересчитывает все формулы документа.
     * Вызывается после загрузки документа из базы данных.
     *
     * @param doc документ
     */
    public void recalculateAll(Document doc) {
        Objects.requireNonNull(doc, "doc");
        graph = new DependencyGraph();
        List<Cell> formulas = doc.getNonEmptyCells().stream().filter(Cell::isFormula).toList();
        formulas.forEach(this::registerDependencies);
        Set<CellRef> recalculated = new LinkedHashSet<>();
        for (CellRef ref : graph.topologicalOrder()) {
            recalculate(doc, ref);
            recalculated.add(ref);
        }
        for (Cell cell : formulas) {
            if (!recalculated.contains(cell.getRef())) {
                recalculate(doc, cell.getRef());
            }
        }
    }

    /**
     * Разбирает формулу ячейки и регистрирует её зависимости в графе.
     * Ошибка разбора записывается в ячейку как код ошибки.
     *
     * @param cell ячейка с формулой
     */
    private void registerDependencies(Cell cell) {
        CellRef ref = cell.getRef();
        graph.removeDependencies(ref);
        try {
            Set<CellRef> precedents = new LinkedHashSet<>();
            collectRefs(parse(cell.getRaw()), precedents);
            for (CellRef precedent : precedents) {
                graph.addDependency(ref, precedent);
            }
        } catch (FormulaException e) {
            cell.setError(e.getCode());
        }
    }

    /**
     * Пересчитывает одну ячейку с формулой: проверяет цикл, вычисляет значение
     * и сохраняет его в кэше либо записывает код ошибки.
     *
     * @param doc документ
     * @param ref адрес ячейки
     */
    private void recalculate(Document doc, CellRef ref) {
        Cell cell = doc.getCell(ref);
        if (!cell.isFormula()) {
            return;
        }
        if (graph.hasCycle(ref)) {
            cell.setError(FormulaException.CYCLE);
            return;
        }
        try {
            cell.setCachedValue(new Evaluator(doc).eval(parse(cell.getRaw())));
        } catch (FormulaException e) {
            cell.setError(e.getCode());
        }
    }

    private static Node parse(String formula) {
        return new Parser(new Lexer(formula).tokenize()).parse();
    }

    /**
     * Собирает адреса всех ячеек, на которые ссылается формула. Диапазон раскрывается
     * в отдельные ячейки.
     *
     * @param node узел синтаксического дерева
     * @param refs множество, в которое добавляются найденные адреса
     */
    private static void collectRefs(Node node, Set<CellRef> refs) {
        if (node instanceof RefNode reference) {
            refs.add(reference.getRef());
        } else if (node instanceof RangeNode range) {
            if (range.getRange().size() > MAX_RANGE_CELLS) {
                throw new FormulaException(FormulaException.ERROR, "слишком большой диапазон " + range.getRange());
            }
            range.getRange().forEach(refs::add);
        } else if (node instanceof BinOpNode binary) {
            collectRefs(binary.getLeft(), refs);
            collectRefs(binary.getRight(), refs);
        } else if (node instanceof UnaryNode unary) {
            collectRefs(unary.getOperand(), refs);
        } else if (node instanceof FuncNode function) {
            function.getArgs().forEach(argument -> collectRefs(argument, refs));
        }
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

    /**
     * Возвращает документы, доступные текущему пользователю: свои — обычному
     * пользователю, все — администратору.
     *
     * @return список документов, начиная с последних изменённых
     * @throws AuthException если вход не выполнен
     */
    public List<Document> listAvailable() {
        User user = requireUser();
        return user.getRole() == Role.ADMIN ? documentDao.findAll() : documentDao.findByOwner(user.getId());
    }

    /**
     * Удаляет документ вместе с его ячейками.
     *
     * @param id идентификатор документа
     * @throws AuthException    если вход не выполнен или документ принадлежит другому пользователю
     * @throws ServiceException если документ не найден
     */
    public void delete(int id) {
        User user = requireUser();
        Document doc = documentDao.findById(id)
                .orElseThrow(() -> new ServiceException("Документ не найден: id=" + id));
        requireAccess(user, doc.getOwnerId());
        documentDao.delete(id);
        LOG.info(() -> "Удалён документ " + doc.getName() + " (id=" + id + ")");
    }

    /**
     * Сохраняет содержимое документа под новым именем.
     * <p>
     * Создаётся новый документ с текущим пользователем в качестве владельца, в него
     * копируются непустые ячейки исходного документа, после чего копия сохраняется.
     * Исходный документ не изменяется; граф зависимостей перестраивается для копии,
     * которая становится текущей для сервиса.
     *
     * @param doc  исходный документ
     * @param name имя нового документа
     * @return сохранённая копия документа
     * @throws AuthException    если вход не выполнен
     * @throws ServiceException если имя пустое
     */
    public Document saveAs(Document doc, String name) {
        Objects.requireNonNull(doc, "doc");
        Document copy = create(name);
        for (Cell cell : doc.getNonEmptyCells()) {
            copy.setCell(cell.getRef(), new Cell(cell.getRef(), cell.getRaw(), cell.getStyle()));
        }
        recalculateAll(copy);
        copy.setModified(true);
        save(copy);
        return copy;
    }
}
