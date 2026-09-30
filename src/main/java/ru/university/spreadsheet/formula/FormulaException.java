package ru.university.spreadsheet.formula;

import java.util.Objects;

/**
 * Ошибка разбора или вычисления формулы.
 * <p>
 * Несёт код ошибки, который отображается в ячейке таблицы вместо значения:
 * {@value #ERROR}, {@value #DIV_BY_ZERO}, {@value #NAME}, {@value #VALUE}, {@value #CYCLE}.
 */
public class FormulaException extends RuntimeException {

    /** Лексическая или синтаксическая ошибка формулы. */
    public static final String ERROR = "#ERROR!";

    /** Деление на ноль. */
    public static final String DIV_BY_ZERO = "#DIV/0!";

    /** Неизвестная функция или имя. */
    public static final String NAME = "#NAME?";

    /** Значение недопустимого типа, например текст в арифметическом выражении. */
    public static final String VALUE = "#VALUE!";

    /** Циклическая зависимость ячеек. */
    public static final String CYCLE = "#CYCLE!";

    private final String code;

    /**
     * Создаёт исключение с кодом ошибки.
     *
     * @param code код ошибки, например {@value #DIV_BY_ZERO}
     */
    public FormulaException(String code) {
        super(code);
        this.code = Objects.requireNonNull(code, "code");
    }

    /**
     * Создаёт исключение с кодом ошибки и пояснением для журнала.
     *
     * @param code   код ошибки
     * @param detail пояснение причины
     */
    public FormulaException(String code, String detail) {
        super(code + ": " + detail);
        this.code = Objects.requireNonNull(code, "code");
    }

    /**
     * Возвращает код ошибки для отображения в ячейке.
     *
     * @return код ошибки
     */
    public String getCode() {
        return code;
    }
}
