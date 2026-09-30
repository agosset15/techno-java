package ru.university.spreadsheet.formula;

/**
 * Узел синтаксического дерева формулы.
 * <p>
 * Вычисление выполняется по схеме «Посетитель»: узел вызывает соответствующий метод
 * вычислителя.
 */
public abstract class Node {

    /**
     * Передаёт узел вычислителю.
     *
     * @param evaluator вычислитель формул
     * @return значение узла: число, логическое значение или список чисел для диапазона
     */
    public abstract Object accept(Evaluator evaluator);
}
