package ru.university.spreadsheet.formula;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import ru.university.spreadsheet.model.Cell;
import ru.university.spreadsheet.model.CellRange;
import ru.university.spreadsheet.model.CellRef;
import ru.university.spreadsheet.model.Document;

/**
 * Вычислитель синтаксического дерева формулы в контексте документа.
 * <p>
 * Значения узлов — число ({@link Double}) или логическое значение ({@link Boolean});
 * диапазон вычисляется в список чисел и допустим только как аргумент функции.
 * Пустая ячейка равна нулю, ячейка с кодом ошибки распространяет свой код,
 * ячейка с текстом в арифметике даёт {@value FormulaException#VALUE}. Пустые ячейки
 * диапазона пропускаются и не влияют на AVG, MIN, MAX и COUNT.
 */
public class Evaluator {

    private static final long MAX_RANGE_CELLS = 100_000;
    private static final int MAX_ROUND_DIGITS = 10;

    private final Document doc;

    /**
     * Создаёт вычислитель для документа.
     *
     * @param doc документ, ячейки которого доступны формулам
     * @throws NullPointerException если документ равен null
     */
    public Evaluator(Document doc) {
        this.doc = Objects.requireNonNull(doc, "doc");
    }

    /**
     * Вычисляет значение формулы.
     *
     * @param node корневой узел синтаксического дерева
     * @return число или логическое значение
     * @throws FormulaException при ошибке вычисления; код доступен через
     *                          {@link FormulaException#getCode()}
     */
    public Object eval(Node node) {
        Object value = Objects.requireNonNull(node, "node").accept(this);
        if (value instanceof List) {
            throw new FormulaException(FormulaException.ERROR, "диапазон допустим только как аргумент функции");
        }
        return value;
    }

    Object visitNumber(NumberNode node) {
        return node.getValue();
    }

    Object visitRef(RefNode node) {
        return resolveRef(node.getRef());
    }

    Object visitRange(RangeNode node) {
        CellRange range = node.getRange();
        if (range.size() > MAX_RANGE_CELLS) {
            throw new FormulaException(FormulaException.ERROR, "слишком большой диапазон " + range);
        }
        List<Double> values = new ArrayList<>();
        for (CellRef ref : range) {
            Optional<Cell> cell = doc.findCell(ref);
            if (cell.isPresent() && !isBlank(cell.get())) {
                values.add(cellValue(cell.get()));
            }
        }
        return values;
    }

    Object visitUnary(UnaryNode node) {
        double value = toNumber(node.getOperand().accept(this));
        return node.getOp().equals("-") ? -value : value;
    }

    Object visitBinOp(BinOpNode node) {
        double left = toNumber(node.getLeft().accept(this));
        double right = toNumber(node.getRight().accept(this));
        String op = node.getOp();
        switch (op) {
            case "=":
                return left == right;
            case "<>":
                return left != right;
            case "<":
                return left < right;
            case "<=":
                return left <= right;
            case ">":
                return left > right;
            case ">=":
                return left >= right;
            case "+":
                return checkFinite(left + right);
            case "-":
                return checkFinite(left - right);
            case "*":
                return checkFinite(left * right);
            case "/":
                return divide(left, right);
            case "%":
                return remainder(left, right);
            case "^":
                return checkFinite(Math.pow(left, right));
            default:
                throw new FormulaException(FormulaException.ERROR, "неизвестная операция " + op);
        }
    }

    Object visitFunc(FuncNode node) {
        List<Object> args = new ArrayList<>();
        for (Node arg : node.getArgs()) {
            args.add(arg.accept(this));
        }
        return applyFunc(node.getName(), args);
    }

    private double resolveRef(CellRef ref) {
        return doc.findCell(ref).map(Evaluator::cellValue).orElse(0.0);
    }

    private Object applyFunc(String name, List<Object> args) {
        String function = name.toUpperCase(Locale.ROOT);
        switch (function) {
            case "SUM":
                return numbers(function, args).stream().mapToDouble(Double::doubleValue).sum();
            case "AVG":
                return average(function, args);
            case "MIN":
                return extremum(function, args, true);
            case "MAX":
                return extremum(function, args, false);
            case "COUNT":
                return (double) numbers(function, args).size();
            case "IF":
                return conditional(function, args);
            case "ABS":
                return Math.abs(singleNumber(function, args));
            case "SQRT":
                return squareRoot(function, args);
            case "ROUND":
                return round(function, args);
            case "POWER":
                return power(function, args);
            default:
                throw new FormulaException(FormulaException.NAME, "неизвестная функция " + name);
        }
    }

    private static double average(String function, List<Object> args) {
        List<Double> values = numbers(function, args);
        if (values.isEmpty()) {
            throw new FormulaException(FormulaException.DIV_BY_ZERO, "AVG без значений");
        }
        return values.stream().mapToDouble(Double::doubleValue).sum() / values.size();
    }

    private static double extremum(String function, List<Object> args, boolean minimum) {
        List<Double> values = numbers(function, args);
        if (values.isEmpty()) {
            throw new FormulaException(FormulaException.VALUE, function + " без значений");
        }
        return minimum
                ? values.stream().mapToDouble(Double::doubleValue).min().orElseThrow()
                : values.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
    }

    private static Object conditional(String function, List<Object> args) {
        requireArgCount(function, args, 3, 3);
        Object branch = toBoolean(args.get(0)) ? args.get(1) : args.get(2);
        if (branch instanceof List) {
            throw new FormulaException(FormulaException.ERROR, "IF не возвращает диапазон");
        }
        return branch;
    }

    private static double squareRoot(String function, List<Object> args) {
        double value = singleNumber(function, args);
        if (value < 0) {
            throw new FormulaException(FormulaException.VALUE, "SQRT от отрицательного числа");
        }
        return Math.sqrt(value);
    }

    private static double round(String function, List<Object> args) {
        requireArgCount(function, args, 1, 2);
        double value = toNumber(args.get(0));
        int digits = args.size() == 2 ? (int) toNumber(args.get(1)) : 0;
        if (Math.abs(digits) > MAX_ROUND_DIGITS) {
            throw new FormulaException(FormulaException.ERROR, "недопустимое число разрядов " + digits);
        }
        return BigDecimal.valueOf(value).setScale(digits, RoundingMode.HALF_UP).doubleValue();
    }

    private static double power(String function, List<Object> args) {
        requireArgCount(function, args, 2, 2);
        return checkFinite(Math.pow(toNumber(args.get(0)), toNumber(args.get(1))));
    }

    private static double singleNumber(String function, List<Object> args) {
        requireArgCount(function, args, 1, 1);
        return toNumber(args.get(0));
    }

    private static List<Double> numbers(String function, List<Object> args) {
        if (args.isEmpty()) {
            throw new FormulaException(FormulaException.ERROR, function + " требует хотя бы один аргумент");
        }
        List<Double> values = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof List<?> range) {
                range.forEach(item -> values.add(toNumber(item)));
            } else {
                values.add(toNumber(arg));
            }
        }
        return values;
    }

    private static void requireArgCount(String function, List<Object> args, int min, int max) {
        if (args.size() < min || args.size() > max) {
            throw new FormulaException(FormulaException.ERROR,
                    function + " принимает от " + min + " до " + max + " аргументов, получено " + args.size());
        }
    }

    private static double divide(double left, double right) {
        if (right == 0) {
            throw new FormulaException(FormulaException.DIV_BY_ZERO, "деление на ноль");
        }
        return checkFinite(left / right);
    }

    private static double remainder(double left, double right) {
        if (right == 0) {
            throw new FormulaException(FormulaException.DIV_BY_ZERO, "остаток от деления на ноль");
        }
        return checkFinite(left % right);
    }

    private static double cellValue(Cell cell) {
        if (cell.hasError()) {
            throw new FormulaException(cell.getError().orElse(FormulaException.ERROR));
        }
        Optional<Object> cached = cell.getCachedValue();
        if (cached.isPresent()) {
            return toNumber(cached.get());
        }
        String raw = cell.getRaw().strip();
        if (raw.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new FormulaException(FormulaException.VALUE,
                    "ячейка " + cell.getRef().toA1() + " содержит текст");
        }
    }

    private static boolean isBlank(Cell cell) {
        return !cell.hasError() && cell.getCachedValue().isEmpty() && cell.getRaw().isBlank();
    }

    private static double toNumber(Object value) {
        if (value instanceof Double number) {
            return number;
        }
        if (value instanceof Boolean flag) {
            return flag ? 1 : 0;
        }
        if (value instanceof List) {
            throw new FormulaException(FormulaException.ERROR, "диапазон допустим только как аргумент функции");
        }
        throw new FormulaException(FormulaException.VALUE, "нечисловое значение " + value);
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return toNumber(value) != 0;
    }

    private static double checkFinite(double value) {
        if (Double.isNaN(value)) {
            throw new FormulaException(FormulaException.VALUE, "неопределённое значение");
        }
        if (Double.isInfinite(value)) {
            throw new FormulaException(FormulaException.DIV_BY_ZERO, "бесконечное значение");
        }
        return value;
    }
}
