package ru.university.spreadsheet.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Ячейка табличного документа.
 * <p>
 * Хранит исходный текст, введённый пользователем ({@code raw}), кэшированное вычисленное
 * значение, стиль и код ошибки вычисления формулы. Изменение исходного текста
 * сбрасывает кэшированное значение и ошибку.
 */
public class Cell {

    private static final double MAX_EXACT_INTEGER = 1e15;

    private final CellRef ref;
    private String raw;
    private Object cachedValue;
    private CellStyle style;
    private String error;

    /**
     * Создаёт пустую ячейку со стилем по умолчанию.
     *
     * @param ref адрес ячейки
     * @throws NullPointerException если адрес равен {@code null}
     */
    public Cell(CellRef ref) {
        this(ref, "", CellStyle.DEFAULT);
    }

    /**
     * Создаёт ячейку с исходным текстом и стилем по умолчанию.
     *
     * @param ref адрес ячейки
     * @param raw исходный текст; {@code null} трактуется как пустая строка
     * @throws NullPointerException если адрес равен {@code null}
     */
    public Cell(CellRef ref, String raw) {
        this(ref, raw, CellStyle.DEFAULT);
    }

    /**
     * Создаёт ячейку с исходным текстом и стилем.
     *
     * @param ref   адрес ячейки
     * @param raw   исходный текст; {@code null} трактуется как пустая строка
     * @param style стиль ячейки
     * @throws NullPointerException если адрес или стиль равны {@code null}
     */
    public Cell(CellRef ref, String raw, CellStyle style) {
        this.ref = Objects.requireNonNull(ref, "ref");
        this.raw = raw == null ? "" : raw;
        this.style = Objects.requireNonNull(style, "style");
    }

    /**
     * Возвращает адрес ячейки.
     *
     * @return адрес
     */
    public CellRef getRef() {
        return ref;
    }

    /**
     * Возвращает исходный текст ячейки.
     *
     * @return исходный текст; пустая строка для пустой ячейки
     */
    public String getRaw() {
        return raw;
    }

    /**
     * Устанавливает исходный текст ячейки и сбрасывает кэшированное значение и ошибку.
     *
     * @param raw новый текст; {@code null} трактуется как пустая строка
     */
    public void setRaw(String raw) {
        this.raw = raw == null ? "" : raw;
        this.cachedValue = null;
        this.error = null;
    }

    /**
     * Возвращает кэшированное вычисленное значение.
     *
     * @return значение или пустой {@link Optional}, если значение не вычислялось
     */
    public Optional<Object> getCachedValue() {
        return Optional.ofNullable(cachedValue);
    }

    /**
     * Устанавливает кэшированное значение и сбрасывает код ошибки.
     *
     * @param value вычисленное значение; {@code null} очищает кэш
     */
    public void setCachedValue(Object value) {
        this.cachedValue = value;
        this.error = null;
    }

    /**
     * Возвращает стиль ячейки.
     *
     * @return стиль
     */
    public CellStyle getStyle() {
        return style;
    }

    /**
     * Устанавливает стиль ячейки.
     *
     * @param style новый стиль
     * @throws NullPointerException если стиль равен {@code null}
     */
    public void setStyle(CellStyle style) {
        this.style = Objects.requireNonNull(style, "style");
    }

    /**
     * Возвращает код ошибки вычисления формулы.
     *
     * @return код ошибки (например, {@code "#DIV/0!"}) или пустой {@link Optional}
     */
    public Optional<String> getError() {
        return Optional.ofNullable(error);
    }

    /**
     * Проверяет, содержит ли ячейка ошибку вычисления.
     *
     * @return {@code true}, если код ошибки установлен
     */
    public boolean hasError() {
        return error != null;
    }

    /**
     * Записывает код ошибки и очищает кэшированное значение.
     *
     * @param code код ошибки, например {@code "#CYCLE!"}
     * @throws NullPointerException если код равен {@code null}
     */
    public void setError(String code) {
        this.error = Objects.requireNonNull(code, "code");
        this.cachedValue = null;
    }

    /**
     * Сбрасывает код ошибки.
     */
    public void clearError() {
        this.error = null;
    }

    /**
     * Проверяет, содержит ли ячейка формулу.
     *
     * @return {@code true}, если исходный текст начинается с символа {@code =}
     */
    public boolean isFormula() {
        return raw.startsWith("=");
    }

    /**
     * Проверяет, является ли ячейка пустой: без текста и со стилем по умолчанию.
     * Пустые ячейки не сохраняются в базе данных.
     *
     * @return {@code true}, если ячейка пустая
     */
    public boolean isEmpty() {
        return raw.isEmpty() && style.isDefault();
    }

    /**
     * Возвращает текст для отображения в таблице.
     * <p>
     * Приоритет: код ошибки, затем кэшированное значение, затем исходный текст.
     * Целые числа выводятся без дробной части.
     *
     * @return отображаемый текст
     */
    public String getDisplayValue() {
        if (error != null) {
            return error;
        }
        if (cachedValue != null) {
            return formatValue(cachedValue);
        }
        return raw;
    }

    /**
     * Возвращает краткое строковое представление ячейки для отладки.
     *
     * @return строка вида {@code Cell{A1, raw='=1+2'}}
     */
    @Override
    public String toString() {
        return "Cell{" + ref.toA1() + ", raw='" + raw + "'}";
    }

    private static String formatValue(Object value) {
        if (value instanceof Boolean flag) {
            return flag ? "TRUE" : "FALSE";
        }
        if (value instanceof Double number && Double.isFinite(number)) {
            if (number == Math.rint(number) && Math.abs(number) < MAX_EXACT_INTEGER) {
                return Long.toString(number.longValue());
            }
            return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }
}
