package ru.university.spreadsheet.model;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CellStyle: стиль ячейки")
class CellStyleTest {

    @Test
    @DisplayName("toCss для стиля по умолчанию")
    void defaultCss() {
        assertThat(CellStyle.DEFAULT.toCss())
                .isEqualTo("-fx-background-color: #FFFFFF; -fx-text-fill: #000000; -fx-font-weight: normal;");
    }

    @Test
    @DisplayName("toCss для жёлтого фона, синего текста и полужирного начертания")
    void customCss() {
        CellStyle style = new CellStyle(Color.YELLOW, Color.BLUE, true);

        assertThat(style.toCss())
                .isEqualTo("-fx-background-color: #FFFF00; -fx-text-fill: #0000FF; -fx-font-weight: bold;");
    }

    @Test
    @DisplayName("toHex округляет компоненты цвета до байта")
    void toHex() {
        assertThat(CellStyle.toHex(Color.rgb(26, 43, 60))).isEqualTo("#1A2B3C");
        assertThat(CellStyle.toHex(Color.web("#fedcba"))).isEqualTo("#FEDCBA");
    }

    @Test
    @DisplayName("isDefault распознаёт стиль по умолчанию")
    void isDefault() {
        assertThat(new CellStyle(Color.web("#FFFFFF"), Color.web("#000000"), false).isDefault()).isTrue();
        assertThat(new CellStyle(Color.WHITE, Color.BLACK, true).isDefault()).isFalse();
    }
}
