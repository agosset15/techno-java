package ru.university.spreadsheet.service;

/**
 * Непроверяемое исключение сервисного слоя: нарушение бизнес-правила,
 * например попытка удалить последнего администратора.
 * <p>
 * Сообщение исключения предназначено для показа пользователю.
 */
public class ServiceException extends RuntimeException {

    /**
     * Создаёт исключение с сообщением.
     *
     * @param message описание ошибки для пользователя
     */
    public ServiceException(String message) {
        super(message);
    }

    /**
     * Создаёт исключение с сообщением и исходной причиной.
     *
     * @param message описание ошибки для пользователя
     * @param cause   исходное исключение
     */
    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
