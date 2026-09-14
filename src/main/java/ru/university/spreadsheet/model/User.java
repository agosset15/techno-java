package ru.university.spreadsheet.model;

import java.util.Objects;

/**
 * Учётная запись пользователя системы.
 * <p>
 * Хранит логин, хеш пароля, соль и роль. Логика хеширования и проверки пароля
 * находится в сервисе аутентификации, поэтому класс не содержит бизнес-логики.
 * Объект неизменяем: изменённые копии создаются методами {@link #withId(int)}
 * и {@link #withRole(Role)}.
 */
public final class User {

    private final int id;
    private final String login;
    private final String pwdHash;
    private final String salt;
    private final Role role;

    /**
     * Создаёт объект пользователя.
     *
     * @param id      идентификатор; {@code 0} означает, что пользователь ещё не сохранён в БД
     * @param login   логин
     * @param pwdHash хеш пароля в кодировке Base64
     * @param salt    соль в кодировке Base64
     * @param role    роль пользователя
     * @throws IllegalArgumentException если идентификатор отрицательный
     * @throws NullPointerException     если логин, хеш, соль или роль равны {@code null}
     */
    public User(int id, String login, String pwdHash, String salt, Role role) {
        if (id < 0) {
            throw new IllegalArgumentException("Идентификатор пользователя не может быть отрицательным: " + id);
        }
        this.id = id;
        this.login = Objects.requireNonNull(login, "login");
        this.pwdHash = Objects.requireNonNull(pwdHash, "pwdHash");
        this.salt = Objects.requireNonNull(salt, "salt");
        this.role = Objects.requireNonNull(role, "role");
    }

    /**
     * Возвращает идентификатор пользователя.
     *
     * @return идентификатор; {@code 0}, если пользователь не сохранён
     */
    public int getId() {
        return id;
    }

    /**
     * Возвращает логин пользователя.
     *
     * @return логин
     */
    public String getLogin() {
        return login;
    }

    /**
     * Возвращает хеш пароля.
     *
     * @return хеш пароля в кодировке Base64
     */
    public String getPwdHash() {
        return pwdHash;
    }

    /**
     * Возвращает соль, использованную при хешировании пароля.
     *
     * @return соль в кодировке Base64
     */
    public String getSalt() {
        return salt;
    }

    /**
     * Возвращает роль пользователя.
     *
     * @return роль
     */
    public Role getRole() {
        return role;
    }

    /**
     * Возвращает копию пользователя с указанным идентификатором.
     *
     * @param newId новый идентификатор
     * @return новый объект пользователя
     */
    public User withId(int newId) {
        return new User(newId, login, pwdHash, salt, role);
    }

    /**
     * Возвращает копию пользователя с указанной ролью.
     *
     * @param newRole новая роль
     * @return новый объект пользователя
     */
    public User withRole(Role newRole) {
        return new User(id, login, pwdHash, salt, newRole);
    }

    /**
     * Сравнивает пользователей по всем полям.
     *
     * @param o объект для сравнения
     * @return {@code true}, если все поля совпадают
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return id == other.id
                && login.equals(other.login)
                && pwdHash.equals(other.pwdHash)
                && salt.equals(other.salt)
                && role == other.role;
    }

    /**
     * Вычисляет хеш-код по всем полям.
     *
     * @return хеш-код
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, login, pwdHash, salt, role);
    }

    /**
     * Возвращает строковое представление без хеша пароля и соли.
     *
     * @return строка вида {@code User{id=1, login='admin', role=ADMIN}}
     */
    @Override
    public String toString() {
        return "User{id=" + id + ", login='" + login + "', role=" + role + '}';
    }
}
