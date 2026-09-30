package ru.university.spreadsheet.formula;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Узел вызова функции.
 */
public final class FuncNode extends Node {

    private final String name;
    private final List<Node> args;

    /**
     * Создаёт узел вызова функции.
     *
     * @param name имя функции в том виде, в каком его ввёл пользователь
     * @param args аргументы вызова
     * @throws NullPointerException если имя или список аргументов равны null
     */
    public FuncNode(String name, List<Node> args) {
        this.name = Objects.requireNonNull(name, "name");
        this.args = List.copyOf(Objects.requireNonNull(args, "args"));
    }

    /**
     * Возвращает имя функции.
     *
     * @return имя функции
     */
    public String getName() {
        return name;
    }

    /**
     * Возвращает аргументы вызова.
     *
     * @return неизменяемый список узлов-аргументов
     */
    public List<Node> getArgs() {
        return args;
    }

    @Override
    public Object accept(Evaluator evaluator) {
        return evaluator.visitFunc(this);
    }

    /**
     * Возвращает вызов функции с аргументами.
     *
     * @return строка вида SUM(A1:A3, 2)
     */
    @Override
    public String toString() {
        return name + "(" + args.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
    }
}
