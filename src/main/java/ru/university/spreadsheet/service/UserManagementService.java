package ru.university.spreadsheet.service;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import ru.university.spreadsheet.dao.UserDao;
import ru.university.spreadsheet.model.Role;
import ru.university.spreadsheet.model.User;

/**
 * Управление учётными записями пользователей. Все операции доступны только
 * администратору, выполнившему вход.
 * <p>
 * В системе всегда остаётся хотя бы один администратор: удаление и понижение роли
 * последнего администратора запрещены.
 */
public class UserManagementService {

    private static final Logger LOG = Logger.getLogger(UserManagementService.class.getName());

    private final UserDao userDao;
    private final AuthService authService;

    /**
     * Создаёт сервис, работающий с базой данных по умолчанию.
     *
     * @param authService сервис аутентификации, хранящий текущего пользователя
     */
    public UserManagementService(AuthService authService) {
        this(new UserDao(), authService);
    }

    /**
     * Создаёт сервис с указанным DAO пользователей.
     *
     * @param userDao     DAO пользователей
     * @param authService сервис аутентификации, хранящий текущего пользователя
     */
    public UserManagementService(UserDao userDao, AuthService authService) {
        this.userDao = Objects.requireNonNull(userDao, "userDao");
        this.authService = Objects.requireNonNull(authService, "authService");
    }

    /**
     * Создаёт учётную запись с указанной ролью.
     *
     * @param login    логин: от 3 до 32 символов из латинских букв, цифр и подчёркивания
     * @param password пароль не короче {@value AuthService#MIN_PASSWORD_LENGTH} символов
     * @param role     роль новой учётной записи
     * @return сохранённый пользователь
     * @throws AuthException если текущий пользователь не администратор, логин или пароль
     *                       некорректны либо логин уже занят
     */
    public User createUser(String login, String password, Role role) {
        requireAdmin();
        User user = userDao.save(authService.prepareNewUser(login, password, role));
        LOG.info(() -> "Администратор создал пользователя " + user.getLogin() + " с ролью " + user.getRole());
        return user;
    }

    /**
     * Изменяет роль пользователя.
     *
     * @param userId идентификатор пользователя
     * @param role   новая роль
     * @throws AuthException    если текущий пользователь не администратор
     * @throws ServiceException если пользователь не найден или понижается последний администратор
     */
    public void changeRole(int userId, Role role) {
        Objects.requireNonNull(role, "role");
        requireAdmin();
        User user = findExisting(userId);
        if (user.getRole() == role) {
            return;
        }
        if (user.getRole() == Role.ADMIN) {
            ensureNotLastAdmin();
        }
        User updated = user.withRole(role);
        userDao.update(updated);
        authService.refreshCurrentUser(updated);
        LOG.info(() -> "Роль пользователя " + user.getLogin() + " изменена на " + role);
    }

    /**
     * Удаляет пользователя вместе с его документами.
     *
     * @param userId идентификатор пользователя
     * @throws AuthException    если текущий пользователь не администратор
     * @throws ServiceException если пользователь не найден, является последним администратором
     *                          или совпадает с текущим пользователем
     */
    public void deleteUser(int userId) {
        User current = requireAdmin();
        User user = findExisting(userId);
        if (user.getRole() == Role.ADMIN) {
            ensureNotLastAdmin();
        }
        if (user.getId() == current.getId()) {
            throw new ServiceException("Нельзя удалить собственную учётную запись");
        }
        userDao.delete(userId);
        LOG.info(() -> "Пользователь " + user.getLogin() + " удалён");
    }

    /**
     * Возвращает список всех пользователей.
     *
     * @return пользователи в порядке создания
     * @throws AuthException если текущий пользователь не администратор
     */
    public List<User> listUsers() {
        requireAdmin();
        return userDao.findAll();
    }

    private User requireAdmin() {
        User current = authService.getCurrentUser()
                .orElseThrow(() -> new AuthException("Необходимо выполнить вход"));
        if (current.getRole() != Role.ADMIN) {
            throw new AuthException("Операция доступна только администратору");
        }
        return current;
    }

    private User findExisting(int userId) {
        return userDao.findById(userId)
                .orElseThrow(() -> new ServiceException("Пользователь не найден: id=" + userId));
    }

    private void ensureNotLastAdmin() {
        if (userDao.countByRole(Role.ADMIN) <= 1) {
            throw new ServiceException("Нельзя удалить или понизить роль последнего администратора");
        }
    }
}
