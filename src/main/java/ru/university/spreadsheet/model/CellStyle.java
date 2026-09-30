package ru.university.spreadsheet.model;

import java.util.Locale;
import java.util.Objects;

import javafx.scene.paint.Color;

/**
 * Стиль оформления ячейки: цвет фона, цвет текста и полужирное начертание.
 * Объект неизменяем.
 *
 * @param bg   цвет фона
 * @param fg   цвет текста
 * @param bold признак полужирного начертания
 */
public record CellStyle(Color bg, Color fg, boolean bold) {

    /** Стиль по умолчанию: белый фон, чёрный текст, обычное начертание. */
    public static final CellStyle DEFAULT = new CellStyle(Color.WHITE, Color.BLACK, false);

    private static final int MAX_CHANNEL = 255;

    /**
     * Создаёт стиль ячейки.
     *
     * @param bg   цвет фона
     * @param fg   цвет текста
     * @param bold признак полужирного начертания
     * @throws NullPointerException если один из цветов равен {@code null}
     */
    public CellStyle {
        Objects.requireNonNull(bg, "bg");
        Objects.requireNonNull(fg, "fg");
    }

    /**
     * Формирует CSS-описание стиля для элемента JavaFX.
     *
     * @return строка вида
     *         {@code -fx-background-color: #RRGGBB; -fx-text-fill: #RRGGBB; -fx-font-weight: bold;}
     */
    public String toCss() {
        return "-fx-background-color: " + toHex(bg)
                + "; -fx-text-fill: " + toHex(fg)
                + "; -fx-font-weight: " + (bold ? "bold" : "normal") + ";";
    }

    /**
     * Проверяет, совпадает ли стиль со стилем по умолчанию.
     *
     * @return {@code true}, если стиль равен {@link #DEFAULT}
     */
    public boolean isDefault() {
        return DEFAULT.equals(this);
    }

    /**
     * Преобразует цвет в шестнадцатеричную запись без альфа-канала.
     *
     * @param color цвет
     * @return строка вида {@code #RRGGBB} в верхнем регистре
     * @throws NullPointerException если цвет равен {@code null}
     */
    public static String toHex(Color color) {
        Objects.requireNonNull(color, "color");
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                toChannel(color.getRed()), toChannel(color.getGreen()), toChannel(color.getBlue()));
    }

    private static int toChannel(double component) {
        return (int) Math.round(component * MAX_CHANNEL);
    }

    /**
     * Возвращает копию стиля с другим цветом фона.
     *
     * @param newBg новый цвет фона
     * @return новый стиль
     */
    public CellStyle withBg(Color newBg) {
        return new CellStyle(newBg, fg, bold);
    }

    /**
     * Возвращает копию стиля с другим цветом текста.
     *
     * @param newFg новый цвет текста
     * @return новый стиль
     */
    public CellStyle withFg(Color newFg) {
        return new CellStyle(bg, newFg, bold);
    }

    /**
     * Возвращает копию стиля с другим начертанием.
     *
     * @param newBold признак полужирного начертания
     * @return новый стиль
     */
    public CellStyle withBold(boolean newBold) {
        return new CellStyle(bg, fg, newBold);
    }
}
