package ru.university.spreadsheet.formula;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ru.university.spreadsheet.model.CellRange;
import ru.university.spreadsheet.model.CellRef;

/**
 * Синтаксический анализатор формул методом рекурсивного спуска.
 * <p>
 * Реализуемая грамматика:
 * <pre>
 * parse      := comparison EOF
 * comparison := expr (('=' | '&lt;&gt;' | '&lt;' | '&lt;=' | '&gt;' | '&gt;=') expr)?
 * expr       := term (('+' | '-') term)*
 * term       := factor (('*' | '/' | '%') factor)*
 * factor     := unary ('^' factor)?
 * unary      := ('-' | '+')? primary
 * primary    := NUMBER | REF (':' REF)? | FUNC '(' (comparison (',' comparison)*)? ')' | '(' comparison ')'
 * </pre>
 * Степень правоассоциативна, остальные бинарные операции левоассоциативны.
 */
public class Parser {

    private static final List<String> COMPARISON_OPERATORS = List.of("=", "<>", "<", "<=", ">", ">=");

    private final List<Token> tokens;
    private int pos;

    /**
     * Создаёт анализатор для списка лексем.
     *
     * @param tokens лексемы формулы, полученные от {@link Lexer}
     * @throws NullPointerException если список равен null
     */
    public Parser(List<Token> tokens) {
        this.tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
    }

    /**
     * Строит синтаксическое дерево формулы.
     *
     * @return корневой узел дерева
     * @throws FormulaException с кодом {@value FormulaException#ERROR} при синтаксической ошибке
     *                          или с кодом {@value FormulaException#NAME} при имени без вызова функции
     */
    public Node parse() {
        pos = 0;
        Node node = comparison();
        if (current().type() != TokenType.EOF) {
            throw new FormulaException(FormulaException.ERROR,
                    "лишние символы после выражения: " + current().value());
        }
        return node;
    }

    private Node comparison() {
        Node left = expr();
        if (current().type() == TokenType.OP && COMPARISON_OPERATORS.contains(current().value())) {
            String op = current().value();
            pos++;
            return new BinOpNode(op, left, expr());
        }
        return left;
    }

    private Node expr() {
        Node node = term();
        while (isOperator("+") || isOperator("-")) {
            String op = current().value();
            pos++;
            node = new BinOpNode(op, node, term());
        }
        return node;
    }

    private Node term() {
        Node node = factor();
        while (isOperator("*") || isOperator("/") || isOperator("%")) {
            String op = current().value();
            pos++;
            node = new BinOpNode(op, node, factor());
        }
        return node;
    }

    private Node factor() {
        Node base = unary();
        if (isOperator("^")) {
            pos++;
            return new BinOpNode("^", base, factor());
        }
        return base;
    }

    private Node unary() {
        if (isOperator("-") || isOperator("+")) {
            String op = current().value();
            pos++;
            return new UnaryNode(op, primary());
        }
        return primary();
    }

    private Node primary() {
        Token token = current();
        switch (token.type()) {
            case NUMBER:
                pos++;
                return new NumberNode(parseNumber(token.value()));
            case REF:
                pos++;
                return reference(token.value());
            case FUNC:
                pos++;
                return functionCall(token.value());
            case LPAREN:
                pos++;
                Node inner = comparison();
                expect(TokenType.RPAREN, "ожидалась закрывающая скобка");
                return inner;
            default:
                throw new FormulaException(FormulaException.ERROR, "неожиданная лексема " + token);
        }
    }

    private Node reference(String text) {
        CellRef from = toRef(text);
        if (current().type() != TokenType.COLON) {
            return new RefNode(from);
        }
        pos++;
        if (current().type() != TokenType.REF) {
            throw new FormulaException(FormulaException.ERROR, "после двоеточия ожидалась ссылка на ячейку");
        }
        CellRef to = toRef(current().value());
        pos++;
        return new RangeNode(new CellRange(from, to));
    }

    private Node functionCall(String name) {
        if (current().type() != TokenType.LPAREN) {
            throw new FormulaException(FormulaException.NAME, "неизвестное имя " + name);
        }
        pos++;
        List<Node> args = new ArrayList<>();
        if (current().type() != TokenType.RPAREN) {
            args.add(comparison());
            while (current().type() == TokenType.COMMA) {
                pos++;
                args.add(comparison());
            }
        }
        expect(TokenType.RPAREN, "ожидалась закрывающая скобка вызова функции " + name);
        return new FuncNode(name, args);
    }

    private void expect(TokenType type, String message) {
        if (current().type() != type) {
            throw new FormulaException(FormulaException.ERROR, message);
        }
        pos++;
    }

    private boolean isOperator(String operator) {
        return current().type() == TokenType.OP && current().value().equals(operator);
    }

    private Token current() {
        return tokens.get(Math.min(pos, tokens.size() - 1));
    }

    private static double parseNumber(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new FormulaException(FormulaException.ERROR, "некорректное число " + text);
        }
    }

    private static CellRef toRef(String text) {
        try {
            return CellRef.parse(text);
        } catch (IllegalArgumentException e) {
            throw new FormulaException(FormulaException.ERROR, "некорректная ссылка " + text);
        }
    }
}
