package ru.university.spreadsheet.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Database: подключение и миграция схемы")
class DatabaseTest extends DatabaseTestSupport {

    @Test
    @DisplayName("миграция создаёт таблицы и индекс")
    void migrateCreatesSchema() throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement statement = database.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type IN ('table', 'index') ORDER BY name")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        assertThat(names).contains("users", "documents", "cells", "idx_documents_owner");
    }

    @Test
    @DisplayName("повторная миграция не приводит к ошибке")
    void migrateIsIdempotent() {
        assertThatCode(database::migrate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("контроль внешних ключей включён")
    void foreignKeysEnabled() throws SQLException {
        try (Statement statement = database.getConnection().createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA foreign_keys")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("после close() соединение открывается заново")
    void reopensAfterClose() throws SQLException {
        database.close();
        assertThat(database.getConnection().isClosed()).isFalse();
    }

    @Test
    @DisplayName("разбор сценария удаляет комментарии и пустые операторы")
    void splitStatementsSkipsComments() {
        String script = """
                -- комментарий
                CREATE TABLE a (id INTEGER); -- хвостовой комментарий
                ;
                CREATE TABLE b (id INTEGER);
                """;
        assertThat(Database.splitStatements(script))
                .containsExactly("CREATE TABLE a (id INTEGER)", "CREATE TABLE b (id INTEGER)");
    }
}
