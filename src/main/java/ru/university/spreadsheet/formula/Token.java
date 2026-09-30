package ru.university.spreadsheet.formula;

import java.util.Objects;

/**
 * Лексема формулы: вид и исходный текст.
 *
 * @param type  вид лексемы
 * @param value исходный текст лексемы; для {@link TokenType#EOF} — пустая строка
 */
public record Token(TokenType type, String value) {

    /**
     * Создаёт лексему.
     *
     * @param type  вид лексемы
     * @param value исходный текст лексемы
     * @throws NullPointerException если вид или текст равны null
     */
    public Token {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }

    /**
     * Возвращает строковое представление лексемы.
     *
     * @return строка вида NUMBER(3.5)
     */
    @Override
    public String toString() {
        return type + "(" + value + ")";
    }
}
