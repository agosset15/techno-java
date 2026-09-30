package ru.university.spreadsheet.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.university.spreadsheet.model.Cell;
import ru.university.spreadsheet.model.CellRef;
import ru.university.spreadsheet.model.Document;

@DisplayName("Evaluator: вычисление формул")
class EvaluatorTest {

    private Document doc;

    @BeforeEach
    void createDocument() {
        doc = new Document("Тест", 1);
    }

    private Object eval(String formula) {
        return new Evaluator(doc).eval(new Parser(new Lexer(formula).tokenize()).parse());
    }

    private double number(String formula) {
        return (Double) eval(formula);
    }

    private String errorCode(String formula) {
        return catchThrowableOfType(() -> eval(formula), FormulaException.class).getCode();
    }

    private void set(String a1, String raw) {
        doc.getCell(CellRef.parse(a1)).setRaw(raw);
    }

    private void setError(String a1, String code) {
        Cell cell = doc.getCell(CellRef.parse(a1));
        cell.setRaw("=1/0");
        cell.setError(code);
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "'=2+2*2', 6",
            "'=(2+2)*2', 8",
            "'=2^3^2', 512",
            "'=-3+5', 2",
            "'=-2^2', 4",
            "'=7/2', 3.5",
            "'=7%2', 1",
            "'=2*3-4/2', 4",
            "'=+5', 5"
    })
    @DisplayName("арифметика: приоритет, скобки, ассоциативность, унарный минус")
    void arithmetic(String formula, double expected) {
        assertThat(number(formula)).isCloseTo(expected, within(1e-9));
    }

    @Test
    @DisplayName("ссылка на ячейку с числом")
    void reference() {
        set("A1", "5");

        assertThat(number("=A1*2")).isEqualTo(10);
    }

    @Test
    @DisplayName("пустая ячейка трактуется как 0")
    void emptyCellIsZero() {
        assertThat(number("=A1+1")).isEqualTo(1);
        assertThat(number("=B7")).isZero();
    }

    @Test
    @DisplayName("используется вычисленное значение ячейки, если оно есть")
    void cachedValueIsUsed() {
        Cell cell = doc.getCell(CellRef.parse("A1"));
        cell.setRaw("=1+2");
        cell.setCachedValue(3.0);

        assertThat(number("=A1*10")).isEqualTo(30);
    }

    @Test
    @DisplayName("SUM по диапазону и отдельным аргументам")
    void sum() {
        set("A1", "1");
        set("A2", "2");
        set("A3", "3");

        assertThat(number("=SUM(A1:A3)")).isEqualTo(6);
        assertThat(number("=SUM(A1:A3, 10)")).isEqualTo(16);
        assertThat(number("=SUM(1,2,3)")).isEqualTo(6);
    }

    @Test
    @DisplayName("AVG, MIN, MAX и COUNT пропускают пустые ячейки диапазона")
    void aggregates() {
        set("A1", "1");
        set("A2", "2");
        set("A3", "6");

        assertThat(number("=AVG(A1:A5)")).isEqualTo(3);
        assertThat(number("=MIN(A1:A5)")).isEqualTo(1);
        assertThat(number("=MAX(A1:A5)")).isEqualTo(6);
        assertThat(number("=COUNT(A1:A5)")).isEqualTo(3);
    }

    @Test
    @DisplayName("IF выбирает ветвь по условию")
    void conditional() {
        set("B1", "5");

        assertThat(number("=IF(B1>0,2,3)")).isEqualTo(2);
        assertThat(number("=IF(B1<0,2,3)")).isEqualTo(3);
        assertThat(number("=IF(0,2,3)")).isEqualTo(3);
        assertThat(number("=IF(1,2,3)")).isEqualTo(2);
    }

    @Test
    @DisplayName("ABS, ROUND, POWER и SQRT")
    void mathFunctions() {
        assertThat(number("=ABS(-4)")).isEqualTo(4);
        assertThat(number("=ROUND(2.567, 2)")).isEqualTo(2.57);
        assertThat(number("=ROUND(2.5)")).isEqualTo(3);
        assertThat(number("=POWER(2, 10)")).isEqualTo(1024);
        assertThat(number("=SQRT(16)")).isEqualTo(4);
    }

    @Test
    @DisplayName("вложенные функции и выражения")
    void nested() {
        set("A1", "1");
        set("A2", "2");
        set("A3", "3");
        set("B1", "5");

        assertThat(number("=SUM(A1:A3)*IF(B1>0,2,3)")).isEqualTo(12);
        assertThat(number("=MAX(SUM(A1:A3), POWER(2,3))")).isEqualTo(8);
        assertThat(number("=ROUND(AVG(A1:A3), 0)")).isEqualTo(2);
    }

    @Test
    @DisplayName("сравнения возвращают логическое значение")
    void comparisons() {
        assertThat(eval("=1<2")).isEqualTo(Boolean.TRUE);
        assertThat(eval("=2<>2")).isEqualTo(Boolean.FALSE);
        assertThat(eval("=2>=2")).isEqualTo(Boolean.TRUE);
        assertThat(eval("=2=2")).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("деление на ноль даёт #DIV/0!")
    void divisionByZero() {
        assertThat(errorCode("=1/0")).isEqualTo(FormulaException.DIV_BY_ZERO);
        assertThat(errorCode("=1%0")).isEqualTo(FormulaException.DIV_BY_ZERO);
        assertThat(errorCode("=AVG(A1:A3)")).isEqualTo(FormulaException.DIV_BY_ZERO);
    }

    @Test
    @DisplayName("неизвестная функция даёт #NAME?")
    void unknownFunction() {
        assertThat(errorCode("=FOO(1)")).isEqualTo(FormulaException.NAME);
        assertThat(errorCode("=СУММ(1)")).isEqualTo(FormulaException.NAME);
    }

    @Test
    @DisplayName("текст в арифметике даёт #VALUE!")
    void textInArithmetic() {
        set("A1", "Привет");

        assertThat(errorCode("=A1+1")).isEqualTo(FormulaException.VALUE);
        assertThat(errorCode("=SUM(A1:A1)")).isEqualTo(FormulaException.VALUE);
        assertThat(errorCode("=SQRT(-1)")).isEqualTo(FormulaException.VALUE);
    }

    @Test
    @DisplayName("диапазон вне функции даёт #ERROR!")
    void rangeOutsideFunction() {
        assertThat(errorCode("=A1:A3")).isEqualTo(FormulaException.ERROR);
        assertThat(errorCode("=A1:A3+1")).isEqualTo(FormulaException.ERROR);
        assertThat(errorCode("=IF(1, A1:A3, 0)")).isEqualTo(FormulaException.ERROR);
    }

    @Test
    @DisplayName("неверное число аргументов даёт #ERROR!")
    void wrongArgumentCount() {
        assertThat(errorCode("=IF(1,2)")).isEqualTo(FormulaException.ERROR);
        assertThat(errorCode("=SQRT(1,2)")).isEqualTo(FormulaException.ERROR);
        assertThat(errorCode("=POWER(2)")).isEqualTo(FormulaException.ERROR);
        assertThat(errorCode("=SUM()")).isEqualTo(FormulaException.ERROR);
    }

    @Test
    @DisplayName("ошибка ячейки распространяется на зависящие формулы")
    void errorPropagation() {
        setError("A1", FormulaException.DIV_BY_ZERO);

        assertThat(errorCode("=A1+1")).isEqualTo(FormulaException.DIV_BY_ZERO);
        assertThat(errorCode("=SUM(A1:A3)")).isEqualTo(FormulaException.DIV_BY_ZERO);
    }

    @Test
    @DisplayName("слишком большой диапазон отклоняется")
    void hugeRange() {
        assertThat(errorCode("=SUM(A1:ZZ100000)")).isEqualTo(FormulaException.ERROR);
    }
}
