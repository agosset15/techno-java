package ru.university.spreadsheet.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Parser: построение синтаксического дерева")
class ParserTest {

    private static String ast(String formula) {
        return new Parser(new Lexer(formula).tokenize()).parse().toString();
    }

    private static String errorCode(String formula) {
        return catchThrowableOfType(() -> ast(formula), FormulaException.class).getCode();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'=2+2*2', '(2 + (2 * 2))'",
            "'=(2+2)*2', '((2 + 2) * 2)'",
            "'=2-3-4', '((2 - 3) - 4)'",
            "'=8/4/2', '((8 / 4) / 2)'",
            "'=2^3^2', '(2 ^ (3 ^ 2))'",
            "'=-3+5', '((-3) + 5)'",
            "'=-2^2', '((-2) ^ 2)'",
            "'=A1', 'A1'",
            "'=A1:B2', 'A1:B2'",
            "'=SUM(A1:A3, 2)', 'SUM(A1:A3, 2)'",
            "'=IF(B1>0,2,3)', 'IF((B1 > 0), 2, 3)'",
            "'=1<>2', '(1 <> 2)'"
    })
    @DisplayName("приоритет операций, ассоциативность и структура дерева")
    void buildsTree(String formula, String expected) {
        assertThat(ast(formula)).isEqualTo(expected);
    }

    @Test
    @DisplayName("степень правоассоциативна, умножение — левоассоциативно")
    void associativity() {
        assertThat(ast("=2*3*4")).isEqualTo("((2 * 3) * 4)");
        assertThat(ast("=2^3^2")).isEqualTo("(2 ^ (3 ^ 2))");
    }

    @Test
    @DisplayName("функция без аргументов допустима синтаксически")
    void functionWithoutArguments() {
        assertThat(ast("=SUM()")).isEqualTo("SUM()");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"=2+", "=(2", "=2 2", "=SUM(", "=SUM(1,)", "=,2", "=*3", "=A1:", "=2)", "=SUM(1 2)"})
    @DisplayName("синтаксическая ошибка даёт код #ERROR!")
    void syntaxErrors(String formula) {
        assertThat(errorCode(formula)).isEqualTo(FormulaException.ERROR);
    }

    @Test
    @DisplayName("имя без скобок вызова даёт код #NAME?")
    void unknownName() {
        assertThat(errorCode("=ИТОГО")).isEqualTo(FormulaException.NAME);
        assertThat(errorCode("=A1+ИТОГО")).isEqualTo(FormulaException.NAME);
    }

    @Test
    @DisplayName("пустая формула даёт код #ERROR!")
    void emptyFormula() {
        assertThat(errorCode("=")).isEqualTo(FormulaException.ERROR);
    }
}
