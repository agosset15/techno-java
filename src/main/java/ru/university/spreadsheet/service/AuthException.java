package ru.university.spreadsheet.service;

/**
 * Ошибка аутентификации или авторизации: неверные учётные данные, временная блокировка
 * входа, некорректные логин или пароль при регистрации, недостаточные права.
 */
public class AuthException extends ServiceException {

    /**
     * Создаёт исключение с сообщением.
     *
     * @param message описание ошибки для пользователя
     */
    public AuthException(String message) {
        super(message);
    }
}
