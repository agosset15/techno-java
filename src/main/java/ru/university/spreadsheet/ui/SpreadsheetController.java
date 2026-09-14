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
import ru.university.spreadsheet.service.ServiceException;

/**
 * Контроллер главного окна: принимает действия пользователя от {@link MainView},
 * передаёт их сервисам и обновляет представление.
 * <p>
 * Хранит открытый документ и текущее выделение. Бизнес-логики не содержит — все
 * изменения документа выполняются через {@link DocumentService}.
 */
public class SpreadsheetController {

    private static final Logger LOG = Logger.getLogger(SpreadsheetController.class.getName());

    private final MainView view;
    private final DocumentService documentService;
    private final AuthService authService;
    private Document currentDoc;
    private CellRange selection;

    /**
     * Создаёт контроллер.
     *
     * @param view            главное окно
     * @param documentService сервис документов
     * @param authService     сервис аутентификации
     */
    public SpreadsheetController(MainView view, DocumentService documentService, AuthService authService) {
        this.view = Objects.requireNonNull(view, "view");
        this.documentService = Objects.requireNonNull(documentService, "documentService");
        this.authService = Objects.requireNonNull(authService, "authService");
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
        if (selection != null && selection.from().equals(ref)) {
            view.showFormula(ref, currentDoc.getCell(ref).getRaw());
        }
        view.updateStatus();
    }

    /**
     * Обрабатывает выбор одиночной ячейки.
     *
     * @param ref адрес выбранной ячейки
     */
    public void onSelect(CellRef ref) {
        onSelectRange(CellRange.of(ref));
    }

    /**
     * Обрабатывает выделение диапазона ячеек.
     *
     * @param range выделенный диапазон
     */
    public void onSelectRange(CellRange range) {
        if (currentDoc == null) {
            return;
        }
        selection = range;
        view.showFormula(range.from(), currentDoc.getCell(range.from()).getRaw());
        view.updateStatus();
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
     * Печатает открытый документ.
     */
    public void onPrint() {
        if (currentDoc == null) {
            return;
        }
        view.showNotImplemented("Печать", 7);
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
            documents = documentService.listByOwner(user.get().getId());
        } catch (RuntimeException e) {
            handleError("Не удалось загрузить список документов", e);
            return;
        }
        Optional<Document> chosen = view.chooseDocument(documents);
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
}
