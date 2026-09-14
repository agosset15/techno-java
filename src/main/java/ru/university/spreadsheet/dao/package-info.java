/**
 * Слой доступа к данным: подключение к SQLite, миграция схемы и DAO-классы
 * для таблиц {@code users}, {@code documents} и {@code cells}.
 * <p>
 * Все запросы выполняются через {@link java.sql.PreparedStatement}; исключения
 * {@link java.sql.SQLException} оборачиваются в {@link ru.university.spreadsheet.dao.DataAccessException}.
 */
package ru.university.spreadsheet.dao;
