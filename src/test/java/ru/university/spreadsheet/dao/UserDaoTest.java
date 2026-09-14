package ru.university.spreadsheet.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.university.spreadsheet.model.Role;
import ru.university.spreadsheet.model.User;

@DisplayName("UserDao")
class UserDaoTest extends DatabaseTestSupport {

    @Test
    @DisplayName("save присваивает идентификатор, findByLogin возвращает те же данные")
    void saveAndFindByLogin() {
        User saved = userDao.save(new User(0, "alice", "hash", "salt", Role.ADMIN));

        assertThat(saved.getId()).isPositive();
        assertThat(userDao.findByLogin("alice")).contains(saved);
    }

    @Test
    @DisplayName("findById находит сохранённого пользователя")
    void findById() {
        User saved = saveUser("bob", Role.USER);

        assertThat(userDao.findById(saved.getId())).contains(saved);
    }

    @Test
    @DisplayName("поиск несуществующего пользователя возвращает пустой Optional")
    void findMissing() {
        assertThat(userDao.findByLogin("nobody")).isEmpty();
        assertThat(userDao.findById(42)).isEmpty();
    }

    @Test
    @DisplayName("findAll возвращает пользователей в порядке создания")
    void findAll() {
        User first = saveUser("first", Role.ADMIN);
        User second = saveUser("second", Role.USER);

        assertThat(userDao.findAll()).containsExactly(first, second);
    }

    @Test
    @DisplayName("повторный логин отклоняется базой данных")
    void duplicateLogin() {
        saveUser("alice", Role.USER);

        assertThatThrownBy(() -> saveUser("alice", Role.USER))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("save отклоняет пользователя с уже присвоенным идентификатором")
    void saveRejectsPersistedUser() {
        User saved = saveUser("alice", Role.USER);

        assertThatThrownBy(() -> userDao.save(saved)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("update сохраняет новую роль и хеш пароля")
    void update() {
        User saved = saveUser("carol", Role.USER);
        User changed = new User(saved.getId(), "carol", "new-hash", "new-salt", Role.ADMIN);

        userDao.update(changed);

        assertThat(userDao.findById(saved.getId())).contains(changed);
    }

    @Test
    @DisplayName("update несуществующего пользователя приводит к ошибке")
    void updateMissing() {
        User ghost = new User(999, "ghost", "h", "s", Role.USER);

        assertThatThrownBy(() -> userDao.update(ghost)).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("delete удаляет пользователя")
    void delete() {
        User saved = saveUser("dave", Role.USER);

        userDao.delete(saved.getId());

        assertThat(userDao.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("countByRole считает пользователей по роли")
    void countByRole() {
        saveUser("admin1", Role.ADMIN);
        saveUser("user1", Role.USER);
        saveUser("user2", Role.USER);

        assertThat(userDao.countByRole(Role.ADMIN)).isEqualTo(1);
        assertThat(userDao.countByRole(Role.USER)).isEqualTo(2);
    }
}
