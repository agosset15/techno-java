package ru.university.spreadsheet;

/**
 * Главный класс исполняемого jar-файла.
 * <p>
 * JavaFX не позволяет запускать из обычного classpath класс, наследующий
 * {@link javafx.application.Application}, в качестве главного, поэтому запуск
 * через {@code java -jar} выполняется через этот класс-посредник.
 */
public final class Launcher {

    private Launcher() {
    }

    /**
     * Запускает приложение.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        App.main(args);
    }
}
