package ru.university.spreadsheet.ui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import ru.university.spreadsheet.model.CellRange;
import ru.university.spreadsheet.model.CellRef;
import ru.university.spreadsheet.model.Document;
import ru.university.spreadsheet.model.User;
import ru.university.spreadsheet.service.AuthService;
import ru.university.spreadsheet.service.DocumentService;
import ru.university.spreadsheet.service.PrintService;
import ru.university.spreadsheet.service.ServiceException;

/**
 * Контроллер главного окна: принимает действия пользователя от {@link MainView},
 * передаёт их сервисам и обновляет представление.
 * <p>
 * Хранит открытый документ, выделенный диапазон и активную ячейку выделения —
 * ту, содержимое которой показано в строке формул и которая редактируется с клавиатуры.
 * Бизнес-логики не содержит: изменения документа выполняются через {@link DocumentService}.
 */
public class SpreadsheetController {

    private static final Logger LOG = Logger.getLogger(SpreadsheetController.class.getName());

    private final MainView view;
    private final DocumentService documentService;
    private final AuthService authService;
    private final PrintService printService;
    private Document currentDoc;
    private CellRange selection;
    private CellRef activeCell;

    /**
     * Создаёт контроллер.
     *
     * @param view            главное окно
     * @param documentService сервис документов
     * @param authService     сервис аутентификации
     * @param printService    сервис печати
     */
    public SpreadsheetController(MainView view, DocumentService documentService, AuthService authService,
                                 PrintService printService) {
        this.view = Objects.requireNonNull(view, "view");
        this.documentService = Objects.requireNonNull(documentService, "documentService");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.printService = Objects.requireNonNull(printService, "printService");
    }

    /**
     * Возвращает открытый документ.
     *
     * @return документ или пустой {@link Optional}, если документ не открыт
     */
    public Optional<Document> getCurrentDoc() {
        return Optional.ofNullable(currentDoc);
    }

    /**
     * Возвращает текущее выделение.
     *
     * @return выделенный диапазон (одиночная ячейка — диапазон 1×1) или пустой {@link Optional}
     */
    public Optional<CellRange> getSelection() {
        return Optional.ofNullable(selection);
    }

    /**
     * Возвращает активную ячейку выделения.
     *
     * @return адрес активной ячейки или пустой {@link Optional}, если выделения нет
     */
    public Optional<CellRef> getActiveCell() {
        return Optional.ofNullable(activeCell);
    }

    /**
     * Возвращает подпись текущего пользователя для строки состояния.
     *
     * @return строка вида {@code admin (ADMIN)} или {@code —}, если вход не выполнен
     */
    public String getCurrentUserLabel() {
        return authService.getCurrentUser()
                .map(user -> user.getLogin() + " (" + user.getRole() + ")")
                .orElse("—");
    }

    /**
     * Проверяет, является ли текущий пользователь администратором.
     *
     * @return {@code true} для роли администратора
     */
    public boolean isAdmin() {
        return authService.isAdmin();
    }

    /**
     * Обрабатывает подтверждённый ввод в ячейку.
     *
     * @param ref   адрес ячейки
     * @param value введённый текст
     */
    public void onCellEdit(CellRef ref, String value) {
        if (currentDoc == null) {
            return;
        }
        runSafely("Не удалось изменить ячейку", () -> documentService.setCellValue(currentDoc, ref, value));
        view.refreshTable();
        if (ref.equals(activeCell)) {
            view.showFormula(ref, currentDoc.getCell(ref).getRaw());
        }
        view.updateStatus();
    }

    /**
     * Обрабатывает выбор одиночной ячейки: она становится и выделением, и активной ячейкой.
     *
     * @param ref адрес выбранной ячейки
     */
    public void onSelect(CellRef ref) {
        activeCell = Objects.requireNonNull(ref, "ref");
        onSelectRange(CellRange.of(ref));
    }

    /**
     * Обрабатывает выделение диапазона ячеек. Активная ячейка сохраняется, если входит
     * в новый диапазон, иначе ею становится верхний левый угол.
     *
     * @param range выделенный диапазон
     */
    public void onSelectRange(CellRange range) {
        if (currentDoc == null) {
            return;
        }
        selection = Objects.requireNonNull(range, "range");
        if (activeCell == null || !range.contains(activeCell)) {
            activeCell = range.from();
        }
        view.showFormula(activeCell, currentDoc.getCell(activeCell).getRaw());
        view.updateStyleToolbar(currentDoc.getCell(activeCell).getStyle());
        view.updateStatus();
    }

    /**
     * Обновляет представление после изменения стиля ячеек.
     */
    public void onStyleApplied() {
        view.refreshTable();
        view.updateStatus();
    }

    /**
     * Показывает пользователю информационное сообщение.
     *
     * @param message текст сообщения
     */
    public void showMessage(String message) {
        view.showInfo(message);
    }

    /**
     * Сохраняет открытый документ.
     */
    public void onSave() {
        if (currentDoc == null) {
            return;
        }
        runSafely("Не удалось сохранить документ", () -> documentService.save(currentDoc));
        view.updateStatus();
    }

    /**
     * Печатает открытый документ: показывает диалог выбора принтера и отправляет
     * таблицу на печать.
     */
    public void onPrint() {
        if (currentDoc == null) {
            return;
        }
        try {
            if (printService.print(view.getPrintableNode())) {
                view.showInfo("Документ отправлен на печать");
            }
        } catch (RuntimeException e) {
            handleError("Не удалось напечатать документ", e);
        }
    }

    /**
     * Сохраняет открытый документ под новым именем и продолжает работу с копией.
     */
    public void onSaveAs() {
        if (currentDoc == null) {
            return;
        }
        Optional<String> name = view.askDocumentName("Сохранить как");
        if (name.isEmpty()) {
            return;
        }
        runSafely("Не удалось сохранить документ",
                () -> display(documentService.saveAs(currentDoc, name.get())));
    }

    /**
     * Создаёт новый документ: запрашивает имя и открывает документ в таблице.
     */
    public void onNew() {
        Optional<String> name = view.askDocumentName("Новый документ");
        if (name.isEmpty() || !confirmCloseDocument()) {
            return;
        }
        runSafely("Не удалось создать документ", () -> display(documentService.create(name.get())));
    }

    /**
     * Открывает документ, выбранный из списка документов текущего пользователя.
     */
    public void onOpen() {
        Optional<User> user = authService.getCurrentUser();
        if (user.isEmpty()) {
            return;
        }
        List<Document> documents;
        try {
            documents = documentService.listAvailable();
        } catch (RuntimeException e) {
            handleError("Не удалось загрузить список документов", e);
            return;
        }
        Optional<Document> chosen = view.chooseDocument(documents, this::deleteDocument);
        if (chosen.isEmpty() || !confirmCloseDocument()) {
            return;
        }
        runSafely("Не удалось открыть документ", () -> display(documentService.open(chosen.get().getId())));
    }

    /**
     * Спрашивает пользователя, что делать с несохранёнными изменениями перед закрытием
     * документа: сохранить, не сохранять или отменить закрытие.
     *
     * @return {@code true}, если документ можно закрыть
     */
    public boolean confirmCloseDocument() {
        if (currentDoc == null || !currentDoc.isModified()) {
            return true;
        }
        return switch (view.askSaveChanges(currentDoc.getName())) {
            case SAVE -> {
                onSave();
                yield !currentDoc.isModified();
            }
            case DISCARD -> true;
            case CANCEL -> false;
        };
    }

    private void display(Document doc) {
        currentDoc = doc;
        selection = null;
        activeCell = null;
        view.show(doc);
    }

    private void runSafely(String context, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            handleError(context, e);
        }
    }

    private void handleError(String context, RuntimeException e) {
        if (e instanceof ServiceException) {
            view.showError(e.getMessage());
        } else {
            LOG.log(Level.SEVERE, context, e);
            view.showError(context + ": " + e.getMessage());
        }
    }

    /**
     * Удаляет документ по запросу из диалога открытия. Если удалён открытый документ,
     * окно очищается.
     *
     * @param doc удаляемый документ
     * @return true, если документ удалён
     */
    private boolean deleteDocument(Document doc) {
        try {
            documentService.delete(doc.getId());
            if (currentDoc != null && currentDoc.getId() == doc.getId()) {
                currentDoc = null;
                selection = null;
                activeCell = null;
                view.showEmpty();
            }
            return true;
        } catch (RuntimeException e) {
            handleError("Не удалось удалить документ", e);
            return false;
        }
    }
}
