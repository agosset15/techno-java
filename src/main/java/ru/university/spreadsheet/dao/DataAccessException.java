package ru.university.spreadsheet.dao;

/**
 * Непроверяемое исключение слоя доступа к данным.
 * <p>
 * Оборачивает {@link java.sql.SQLException} и другие ошибки работы с базой данных,
 * чтобы проверяемые исключения JDBC не выходили за пределы слоя DAO.
 */
public class DataAccessException extends RuntimeException {

    /**
     * Создаёт исключение с сообщением.
     *
     * @param message описание ошибки
     */
    public DataAccessException(String message) {
        super(message);
    }

    /**
     * Создаёт исключение с сообщением и исходной причиной.
     *
     * @param message описание ошибки
     * @param cause   исходное исключение
     */
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
