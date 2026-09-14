package ru.university.spreadsheet.dao;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Подключение к файловой базе данных SQLite (одиночка).
 * <p>
 * Путь к файлу БД по умолчанию — {@value #DEFAULT_DB_PATH}; он переопределяется системным
 * свойством {@value #DB_PATH_PROPERTY}. Соединение открывается лениво при первом обращении
 * и повторно — после {@link #close()}, при этом путь читается заново.
 */
public final class Database {

    /** Имя системного свойства, задающего путь к файлу базы данных. */
    public static final String DB_PATH_PROPERTY = "spreadsheet.db.path";

    /** Путь к файлу базы данных по умолчанию. */
    public static final String DEFAULT_DB_PATH = "./spreadsheet.db";

    private static final String JDBC_URL_PREFIX = "jdbc:sqlite:";
    private static final String SCHEMA_RESOURCE = "/schema.sql";
    private static final String SQL_ENABLE_FOREIGN_KEYS = "PRAGMA foreign_keys = ON";
    private static final String COMMENT_PREFIX = "--";
    private static final String STATEMENT_SEPARATOR = ";";

    private static final Logger LOG = Logger.getLogger(Database.class.getName());
    private static final Database INSTANCE = new Database();

    private Connection connection;

    private Database() {
    }

    /**
     * Возвращает единственный экземпляр класса.
     *
     * @return экземпляр {@code Database}
     */
    public static Database getInstance() {
        return INSTANCE;
    }

    /**
     * Возвращает открытое соединение с базой данных, открывая его при необходимости.
     * Для каждого нового соединения включается контроль внешних ключей.
     *
     * @return соединение JDBC
     * @throws DataAccessException если соединение не удалось открыть
     */
    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                String path = System.getProperty(DB_PATH_PROPERTY, DEFAULT_DB_PATH);
                connection = DriverManager.getConnection(JDBC_URL_PREFIX + path);
                try (Statement statement = connection.createStatement()) {
                    statement.execute(SQL_ENABLE_FOREIGN_KEYS);
                }
                LOG.info(() -> "Открыто соединение с базой данных: " + path);
            }
            return connection;
        } catch (SQLException e) {
            throw new DataAccessException("Не удалось открыть соединение с базой данных", e);
        }
    }

    /**
     * Создаёт отсутствующие таблицы и индексы, выполняя сценарий {@code schema.sql}
     * из ресурсов приложения. Повторный вызов безопасен.
     *
     * @throws DataAccessException если сценарий не найден или завершился ошибкой
     */
    public synchronized void migrate() {
        List<String> statements = splitStatements(readSchema());
        try (Statement statement = getConnection().createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            LOG.info(() -> "Миграция схемы выполнена, операторов: " + statements.size());
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка миграции схемы базы данных", e);
        }
    }

    /**
     * Закрывает соединение с базой данных, если оно открыто.
     * Ошибка закрытия записывается в журнал и не пробрасывается.
     */
    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
            LOG.info("Соединение с базой данных закрыто");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Ошибка при закрытии соединения с базой данных", e);
        } finally {
            connection = null;
        }
    }

    /**
     * Разбивает SQL-сценарий на отдельные операторы, удаляя однострочные комментарии.
     *
     * @param script текст сценария
     * @return список непустых операторов без завершающей точки с запятой
     */
    static List<String> splitStatements(String script) {
        StringBuilder withoutComments = new StringBuilder();
        for (String line : script.split("\\R")) {
            int commentStart = line.indexOf(COMMENT_PREFIX);
            withoutComments.append(commentStart >= 0 ? line.substring(0, commentStart) : line).append('\n');
        }
        List<String> statements = new ArrayList<>();
        for (String part : withoutComments.toString().split(STATEMENT_SEPARATOR)) {
            String sql = part.strip();
            if (!sql.isEmpty()) {
                statements.add(sql);
            }
        }
        return statements;
    }

    private static String readSchema() {
        try (InputStream in = Database.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new DataAccessException("Не найден ресурс со схемой базы данных: " + SCHEMA_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("Не удалось прочитать схему базы данных", e);
        }
    }
}
