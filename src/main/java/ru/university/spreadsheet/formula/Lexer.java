package ru.university.spreadsheet.formula;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Лексический анализатор формул.
 * <p>
 * Разбивает текст формулы на лексемы. Ведущий знак равенства необязателен и
 * отбрасывается, пробелы игнорируются, точка с запятой считается таким же
 * разделителем аргументов, как запятая. Неизвестный символ приводит к
 * {@link FormulaException} с кодом {@value FormulaException#ERROR}.
 */
public class Lexer {

    private static final Pattern REF_PATTERN = Pattern.compile("[A-Za-z]+[1-9][0-9]*");
    private static final String OPERATOR_CHARS = "+-*/^%<>=";
    private static final List<String> TWO_CHAR_OPERATORS = List.of("<=", ">=", "<>");

    private final String input;
    private int pos;

    /**
     * Создаёт анализатор для текста формулы.
     *
     * @param input текст формулы; null трактуется как пустая строка
     */
    public Lexer(String input) {
        this.input = input == null ? "" : input;
    }

    /**
     * Разбирает формулу на лексемы.
     *
     * @return список лексем, последняя — {@link TokenType#EOF}
     * @throws FormulaException с кодом {@value FormulaException#ERROR} при неизвестном символе
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        pos = 0;
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == '=') {
            pos++;
        }
        while (true) {
            skipWhitespace();
            if (pos >= input.length()) {
                break;
            }
            char current = input.charAt(pos);
            if (isNumberStart(current)) {
                tokens.add(readNumber());
            } else if (Character.isLetter(current) || current == '_') {
                tokens.add(readIdentifier());
            } else if (OPERATOR_CHARS.indexOf(current) >= 0) {
                tokens.add(readOperator());
            } else {
                tokens.add(readSymbol(current));
            }
        }
        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }

    private boolean isNumberStart(char current) {
        return Character.isDigit(current)
                || (current == '.' && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1)));
    }

    private Token readNumber() {
        int start = pos;
        boolean dotSeen = false;
        while (pos < input.length()) {
            char current = input.charAt(pos);
            if (Character.isDigit(current)) {
                pos++;
            } else if (current == '.' && !dotSeen) {
                dotSeen = true;
                pos++;
            } else {
                break;
            }
        }
        return new Token(TokenType.NUMBER, input.substring(start, pos));
    }

    private Token readIdentifier() {
        int start = pos;
        while (pos < input.length()
                && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
            pos++;
        }
        String text = input.substring(start, pos);
        int lookahead = pos;
        while (lookahead < input.length() && Character.isWhitespace(input.charAt(lookahead))) {
            lookahead++;
        }
        boolean callFollows = lookahead < input.length() && input.charAt(lookahead) == '(';
        if (!callFollows && REF_PATTERN.matcher(text).matches()) {
            return new Token(TokenType.REF, text);
        }
        return new Token(TokenType.FUNC, text);
    }

    private Token readOperator() {
        if (pos + 1 < input.length()) {
            String pair = input.substring(pos, pos + 2);
            if (TWO_CHAR_OPERATORS.contains(pair)) {
                pos += 2;
                return new Token(TokenType.OP, pair);
            }
        }
        String operator = String.valueOf(input.charAt(pos));
        pos++;
        return new Token(TokenType.OP, operator);
    }

    private Token readSymbol(char current) {
        pos++;
        switch (current) {
            case '(':
                return new Token(TokenType.LPAREN, "(");
            case ')':
                return new Token(TokenType.RPAREN, ")");
            case ',':
            case ';':
                return new Token(TokenType.COMMA, String.valueOf(current));
            case ':':
                return new Token(TokenType.COLON, ":");
            default:
                throw new FormulaException(FormulaException.ERROR,
                        "неизвестный символ в позиции " + (pos - 1));
        }
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }
}
