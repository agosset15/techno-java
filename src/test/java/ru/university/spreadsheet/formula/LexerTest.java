package ru.university.spreadsheet.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Lexer: лексический разбор формул")
class LexerTest {

    private static List<Token> tokens(String input) {
        return new Lexer(input).tokenize();
    }

    private static List<TokenType> types(String input) {
        return tokens(input).stream().map(Token::type).toList();
    }

    private static List<String> values(String input) {
        return tokens(input).stream().map(Token::value).toList();
    }

    @Test
    @DisplayName("числа, в том числе дробные с точкой")
    void numbers() {
        assertThat(types("=2 + 3.5")).containsExactly(
                TokenType.NUMBER, TokenType.OP, TokenType.NUMBER, TokenType.EOF);
        assertThat(values("=2 + 3.5")).containsExactly("2", "+", "3.5", "");
        assertThat(values("=.5")).containsExactly(".5", "");
    }

    @Test
    @DisplayName("ссылки, диапазон и имя функции")
    void refsAndFunctions() {
        assertThat(types("=SUM(A1:BC27)")).containsExactly(
                TokenType.FUNC, TokenType.LPAREN, TokenType.REF, TokenType.COLON,
                TokenType.REF, TokenType.RPAREN, TokenType.EOF);
        assertThat(values("=SUM(A1:BC27)")).containsExactly("SUM", "(", "A1", ":", "BC27", ")", "");
    }

    @Test
    @DisplayName("точка с запятой — такой же разделитель аргументов, как запятая")
    void semicolonSeparator() {
        assertThat(types("=IF(A1>0;1;2)")).isEqualTo(types("=IF(A1>0,1,2)"));
    }

    @Test
    @DisplayName("операторы сравнения из двух символов")
    void comparisonOperators() {
        assertThat(values("=1<=2")).containsExactly("1", "<=", "2", "");
        assertThat(values("=1>=2")).containsExactly("1", ">=", "2", "");
        assertThat(values("=1<>2")).containsExactly("1", "<>", "2", "");
        assertThat(values("=1<2")).containsExactly("1", "<", "2", "");
    }

    @Test
    @DisplayName("арифметические операторы")
    void arithmeticOperators() {
        assertThat(values("=1+2-3*4/5^6%7"))
                .containsExactly("1", "+", "2", "-", "3", "*", "4", "/", "5", "^", "6", "%", "7", "");
    }

    @Test
    @DisplayName("ведущий знак «=» необязателен, пробелы игнорируются")
    void optionalLeadingEquals() {
        assertThat(types("  =  2 + 2 ")).isEqualTo(types("2+2"));
    }

    @Test
    @DisplayName("имя, не похожее на ссылку, разбирается как имя функции")
    void unknownNameIsFunction() {
        assertThat(types("=ИТОГО")).containsExactly(TokenType.FUNC, TokenType.EOF);
        assertThat(types("=A0")).containsExactly(TokenType.FUNC, TokenType.EOF);
    }

    @Test
    @DisplayName("пустая формула даёт единственную лексему EOF")
    void emptyInput() {
        assertThat(types("")).containsExactly(TokenType.EOF);
        assertThat(types("=")).containsExactly(TokenType.EOF);
    }

    @Test
    @DisplayName("неизвестный символ приводит к ошибке #ERROR!")
    void unknownSymbol() {
        FormulaException error = catchThrowableOfType(() -> tokens("=2 # 3"), FormulaException.class);

        assertThat(error.getCode()).isEqualTo(FormulaException.ERROR);
    }
}
