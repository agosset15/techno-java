package ru.university.spreadsheet.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Адрес ячейки таблицы: номер строки и номер столбца, отсчитываемые от нуля.
 * <p>
 * Адрес «A1» соответствует {@code new CellRef(0, 0)}, «B3» — {@code new CellRef(2, 1)}.
 * Буквенная часть адреса записывается в биективной системе счисления с основанием 26:
 * A…Z, AA…AZ, BA…ZZ, AAA и так далее. Объект неизменяем и пригоден в качестве ключа
 * хеш-таблицы.
 *
 * @param row номер строки, начиная с нуля
 * @param col номер столбца, начиная с нуля
 */
public record CellRef(int row, int col) {

    private static final Pattern A1_PATTERN = Pattern.compile("([A-Za-z]+)([1-9][0-9]*)");
    private static final int ALPHABET_SIZE = 26;

    /**
     * Создаёт адрес ячейки.
     *
     * @param row номер строки, начиная с нуля
     * @param col номер столбца, начиная с нуля
     * @throws IllegalArgumentException если строка или столбец отрицательные
     */
    public CellRef {
        if (row < 0 || col < 0) {
            throw new IllegalArgumentException(
                    "Номера строки и столбца не могут быть отрицательными: row=" + row + ", col=" + col);
        }
    }

    /**
     * Разбирает адрес ячейки в нотации A1 без учёта регистра букв.
     *
     * @param a1 адрес, например {@code "A1"}, {@code "bc27"}
     * @return адрес ячейки
     * @throws IllegalArgumentException если строка равна {@code null}, не соответствует
     *                                  формату «буквы + номер строки от 1» или выходит за пределы {@code int}
     */
    public static CellRef parse(String a1) {
        if (a1 == null) {
            throw new IllegalArgumentException("Адрес ячейки не задан");
        }
        Matcher matcher = A1_PATTERN.matcher(a1);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Некорректный адрес ячейки: \"" + a1 + "\"");
        }
        int col = parseColumn(matcher.group(1), a1);
        int row = parseRow(matcher.group(2), a1);
        return new CellRef(row, col);
    }

    /**
     * Возвращает буквенное обозначение столбца.
     *
     * @param col номер столбца, начиная с нуля
     * @return обозначение, например {@code "A"} для 0, {@code "AA"} для 26
     * @throws IllegalArgumentException если номер столбца отрицательный
     */
    public static String columnName(int col) {
        if (col < 0) {
            throw new IllegalArgumentException("Номер столбца не может быть отрицательным: " + col);
        }
        StringBuilder letters = new StringBuilder();
        long n = (long) col + 1;
        while (n > 0) {
            n--;
            letters.append((char) ('A' + n % ALPHABET_SIZE));
            n /= ALPHABET_SIZE;
        }
        return letters.reverse().toString();
    }

    /**
     * Преобразует адрес в нотацию A1.
     *
     * @return строка вида {@code "BC27"}
     */
    public String toA1() {
        return columnName(col) + ((long) row + 1);
    }

    /**
     * Возвращает адрес в нотации A1.
     *
     * @return результат {@link #toA1()}
     */
    @Override
    public String toString() {
        return toA1();
    }

    private static int parseColumn(String letters, String source) {
        long value = 0;
        for (int i = 0; i < letters.length(); i++) {
            char letter = Character.toUpperCase(letters.charAt(i));
            value = value * ALPHABET_SIZE + (letter - 'A' + 1);
            if (value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Слишком большой номер столбца: \"" + source + "\"");
            }
        }
        return (int) (value - 1);
    }

    private static int parseRow(String digits, String source) {
        try {
            return Integer.parseInt(digits) - 1;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Слишком большой номер строки: \"" + source + "\"", e);
        }
    }
}
