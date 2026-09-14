package ru.university.spreadsheet.model;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Табличный документ: метаданные и множество ячеек.
 * <p>
 * Ячейки хранятся разреженно — в словаре по адресу. Флаг {@code modified} отражает
 * наличие несохранённых изменений и снимается методом {@link #markSaved()}.
 */
public class Document {

    private static final Comparator<Cell> BY_POSITION =
            Comparator.<Cell>comparingInt(cell -> cell.getRef().row())
                    .thenComparingInt(cell -> cell.getRef().col());

    private int id;
    private String name;
    private final int ownerId;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final Map<CellRef, Cell> cells = new HashMap<>();
    private boolean modified;

    /**
     * Создаёт новый, ещё не сохранённый документ с текущими метками времени.
     *
     * @param name    имя документа
     * @param ownerId идентификатор владельца
     * @throws NullPointerException если имя равно {@code null}
     */
    public Document(String name, int ownerId) {
        this(0, name, ownerId, now(), null);
    }

    /**
     * Создаёт документ с заданными метаданными (например, прочитанными из БД).
     *
     * @param id        идентификатор; {@code 0} — документ не сохранён
     * @param name      имя документа
     * @param ownerId   идентификатор владельца
     * @param createdAt дата и время создания
     * @param updatedAt дата и время последнего изменения; {@code null} — совпадает с датой создания
     * @throws NullPointerException     если имя или дата создания равны {@code null}
     * @throws IllegalArgumentException если идентификатор отрицательный
     */
    public Document(int id, String name, int ownerId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id < 0) {
            throw new IllegalArgumentException("Идентификатор документа не может быть отрицательным: " + id);
        }
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.ownerId = ownerId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    /**
     * Возвращает идентификатор документа.
     *
     * @return идентификатор; {@code 0}, если документ не сохранён
     */
    public int getId() {
        return id;
    }

    /**
     * Устанавливает идентификатор, присвоенный базой данных.
     *
     * @param id идентификатор
     * @throws IllegalArgumentException если идентификатор отрицательный
     */
    public void setId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("Идентификатор документа не может быть отрицательным: " + id);
        }
        this.id = id;
    }

    /**
     * Возвращает имя документа.
     *
     * @return имя
     */
    public String getName() {
        return name;
    }

    /**
     * Переименовывает документ и помечает его изменённым.
     *
     * @param name новое имя
     * @throws NullPointerException если имя равно {@code null}
     */
    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.modified = true;
    }

    /**
     * Возвращает идентификатор владельца документа.
     *
     * @return идентификатор пользователя-владельца
     */
    public int getOwnerId() {
        return ownerId;
    }

    /**
     * Возвращает дату и время создания документа.
     *
     * @return дата создания
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Возвращает дату и время последнего сохранения документа.
     *
     * @return дата изменения
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Устанавливает дату и время последнего сохранения.
     *
     * @param updatedAt дата изменения
     * @throws NullPointerException если дата равна {@code null}
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Возвращает ячейку по адресу, создавая пустую, если её ещё нет.
     * <p>
     * Созданная пустая ячейка сразу добавляется в документ, поэтому повторные вызовы
     * возвращают тот же объект. Флаг изменения при этом не устанавливается.
     *
     * @param ref адрес ячейки
     * @return ячейка документа
     * @throws NullPointerException если адрес равен {@code null}
     */
    public Cell getCell(CellRef ref) {
        Objects.requireNonNull(ref, "ref");
        return cells.computeIfAbsent(ref, Cell::new);
    }

    /**
     * Ищет ячейку по адресу без создания новой.
     *
     * @param ref адрес ячейки
     * @return ячейка или пустой {@link Optional}, если ячейки нет
     */
    public Optional<Cell> findCell(CellRef ref) {
        return Optional.ofNullable(cells.get(ref));
    }

    /**
     * Помещает ячейку в документ, заменяя прежнюю, и помечает документ изменённым.
     *
     * @param ref  адрес ячейки
     * @param cell ячейка; её адрес должен совпадать с {@code ref}
     * @throws NullPointerException     если адрес или ячейка равны {@code null}
     * @throws IllegalArgumentException если адрес ячейки не совпадает с {@code ref}
     */
    public void setCell(CellRef ref, Cell cell) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(cell, "cell");
        if (!ref.equals(cell.getRef())) {
            throw new IllegalArgumentException(
                    "Адрес ячейки " + cell.getRef().toA1() + " не совпадает с позицией " + ref.toA1());
        }
        cells.put(ref, cell);
        modified = true;
    }

    /**
     * Удаляет ячейку из документа. Если ячейка существовала, документ помечается изменённым.
     *
     * @param ref адрес ячейки
     */
    public void removeCell(CellRef ref) {
        if (cells.remove(ref) != null) {
            modified = true;
        }
    }

    /**
     * Возвращает непустые ячейки документа, упорядоченные по строкам и столбцам.
     *
     * @return неизменяемая коллекция ячеек с текстом или нестандартным стилем
     */
    public Collection<Cell> getNonEmptyCells() {
        return cells.values().stream()
                .filter(cell -> !cell.isEmpty())
                .sorted(BY_POSITION)
                .toList();
    }

    /**
     * Проверяет наличие несохранённых изменений.
     *
     * @return {@code true}, если документ изменён после последнего сохранения
     */
    public boolean isModified() {
        return modified;
    }

    /**
     * Устанавливает флаг несохранённых изменений.
     *
     * @param modified новое значение флага
     */
    public void setModified(boolean modified) {
        this.modified = modified;
    }

    /**
     * Снимает флаг несохранённых изменений после успешного сохранения.
     */
    public void markSaved() {
        this.modified = false;
    }

    /**
     * Возвращает краткое строковое представление документа.
     *
     * @return строка вида {@code Document{id=1, name='Смета'}}
     */
    @Override
    public String toString() {
        return "Document{id=" + id + ", name='" + name + "'}";
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
