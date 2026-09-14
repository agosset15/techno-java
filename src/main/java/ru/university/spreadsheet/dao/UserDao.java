package ru.university.spreadsheet.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import ru.university.spreadsheet.model.Role;
import ru.university.spreadsheet.model.User;

/**
 * Доступ к таблице {@code users}.
 */
public class UserDao {

    private static final String SQL_FIND_BY_LOGIN =
            "SELECT id, login, pwd_hash, salt, role FROM users WHERE login = ?";
    private static final String SQL_FIND_BY_ID =
            "SELECT id, login, pwd_hash, salt, role FROM users WHERE id = ?";
    private static final String SQL_FIND_ALL =
            "SELECT id, login, pwd_hash, salt, role FROM users ORDER BY id";
    private static final String SQL_INSERT =
            "INSERT INTO users (login, pwd_hash, salt, role) VALUES (?, ?, ?, ?)";
    private static final String SQL_UPDATE =
            "UPDATE users SET login = ?, pwd_hash = ?, salt = ?, role = ? WHERE id = ?";
    private static final String SQL_DELETE =
            "DELETE FROM users WHERE id = ?";
    private static final String SQL_COUNT_BY_ROLE =
            "SELECT COUNT(*) FROM users WHERE role = ?";

    private final Database database;

    /**
     * Создаёт DAO, работающий с общим экземпляром {@link Database}.
     */
    public UserDao() {
        this(Database.getInstance());
    }

    /**
     * Создаёт DAO, работающий с указанной базой данных.
     *
     * @param database база данных
     */
    public UserDao(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /**
     * Ищет пользователя по логину (с учётом регистра).
     *
     * @param login логин
     * @return пользователь или пустой {@link Optional}
     * @throws DataAccessException при ошибке базы данных
     */
    public Optional<User> findByLogin(String login) {
        return queryOne(SQL_FIND_BY_LOGIN, login, "Ошибка поиска пользователя по логину " + login);
    }

    /**
     * Ищет пользователя по идентификатору.
     *
     * @param id идентификатор
     * @return пользователь или пустой {@link Optional}
     * @throws DataAccessException при ошибке базы данных
     */
    public Optional<User> findById(int id) {
        return queryOne(SQL_FIND_BY_ID, id, "Ошибка поиска пользователя id=" + id);
    }

    /**
     * Возвращает всех пользователей, упорядоченных по идентификатору.
     *
     * @return список пользователей; пустой, если пользователей нет
     * @throws DataAccessException при ошибке базы данных
     */
    public List<User> findAll() {
        try (PreparedStatement statement = connection().prepareStatement(SQL_FIND_ALL);
             ResultSet rs = statement.executeQuery()) {
            List<User> users = new ArrayList<>();
            while (rs.next()) {
                users.add(mapRow(rs));
            }
            return users;
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка чтения списка пользователей", e);
        }
    }

    /**
     * Сохраняет нового пользователя.
     *
     * @param user пользователь с идентификатором {@code 0}
     * @return копия пользователя с идентификатором, присвоенным базой данных
     * @throws IllegalArgumentException если пользователь уже имеет идентификатор
     * @throws DataAccessException      при ошибке базы данных, в том числе при повторе логина
     */
    public User save(User user) {
        Objects.requireNonNull(user, "user");
        if (user.getId() != 0) {
            throw new IllegalArgumentException("Пользователь уже сохранён: id=" + user.getId());
        }
        try (PreparedStatement statement =
                     connection().prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.getLogin());
            statement.setString(2, user.getPwdHash());
            statement.setString(3, user.getSalt());
            statement.setString(4, user.getRole().name());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("База данных не вернула идентификатор пользователя");
                }
                return user.withId(keys.getInt(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка сохранения пользователя " + user.getLogin(), e);
        }
    }

    /**
     * Обновляет логин, хеш пароля, соль и роль существующего пользователя.
     *
     * @param user пользователь с ненулевым идентификатором
     * @throws DataAccessException если пользователь не найден или произошла ошибка базы данных
     */
    public void update(User user) {
        Objects.requireNonNull(user, "user");
        try (PreparedStatement statement = connection().prepareStatement(SQL_UPDATE)) {
            statement.setString(1, user.getLogin());
            statement.setString(2, user.getPwdHash());
            statement.setString(3, user.getSalt());
            statement.setString(4, user.getRole().name());
            statement.setInt(5, user.getId());
            if (statement.executeUpdate() == 0) {
                throw new DataAccessException("Пользователь не найден: id=" + user.getId());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка обновления пользователя id=" + user.getId(), e);
        }
    }

    /**
     * Удаляет пользователя вместе с его документами (каскадно).
     * Удаление несуществующего пользователя не считается ошибкой.
     *
     * @param id идентификатор пользователя
     * @throws DataAccessException при ошибке базы данных
     */
    public void delete(int id) {
        try (PreparedStatement statement = connection().prepareStatement(SQL_DELETE)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка удаления пользователя id=" + id, e);
        }
    }

    /**
     * Подсчитывает пользователей с указанной ролью.
     *
     * @param role роль
     * @return количество пользователей
     * @throws DataAccessException при ошибке базы данных
     */
    public int countByRole(Role role) {
        Objects.requireNonNull(role, "role");
        try (PreparedStatement statement = connection().prepareStatement(SQL_COUNT_BY_ROLE)) {
            statement.setString(1, role.name());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ошибка подсчёта пользователей с ролью " + role, e);
        }
    }

    private Optional<User> queryOne(String sql, Object parameter, String errorMessage) {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException(errorMessage, e);
        }
    }

    private static User mapRow(ResultSet rs) throws SQLException {
        String roleName = rs.getString("role");
        Role role;
        try {
            role = Role.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("Неизвестная роль в базе данных: " + roleName, e);
        }
        return new User(rs.getInt("id"), rs.getString("login"), rs.getString("pwd_hash"),
                rs.getString("salt"), role);
    }

    private Connection connection() {
        return database.getConnection();
    }
}
