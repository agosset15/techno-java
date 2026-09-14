package ru.university.spreadsheet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.university.spreadsheet.dao.DatabaseTestSupport;
import ru.university.spreadsheet.model.Role;
import ru.university.spreadsheet.model.User;

@DisplayName("UserManagementService: управление пользователями")
class UserManagementServiceTest extends DatabaseTestSupport {

    private static final String PASSWORD = "secret123";

    private AuthService auth;
    private UserManagementService service;
    private User admin;

    @BeforeEach
    void loginAsAdmin() {
        auth = new AuthService(userDao);
        service = new UserManagementService(userDao, auth);
        admin = auth.register("admin", PASSWORD);
        auth.login("admin", PASSWORD);
    }

    @Test
    @DisplayName("удаление последнего администратора запрещено")
    void cannotDeleteLastAdmin() {
        assertThatThrownBy(() -> service.deleteUser(admin.getId()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("последнего администратора");
        assertThat(userDao.findById(admin.getId())).isPresent();
    }

    @Test
    @DisplayName("понижение роли последнего администратора запрещено")
    void cannotDemoteLastAdmin() {
        assertThatThrownBy(() -> service.changeRole(admin.getId(), Role.USER))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("последнего администратора");
        assertThat(userDao.countByRole(Role.ADMIN)).isEqualTo(1);
    }

    @Test
    @DisplayName("администратора можно понизить, если есть другой администратор")
    void demoteWhenAnotherAdminExists() {
        User second = service.createUser("second", PASSWORD, Role.ADMIN);

        service.changeRole(second.getId(), Role.USER);

        assertThat(userDao.findById(second.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("понижение собственной роли обновляет текущего пользователя")
    void selfDemotionRefreshesCurrentUser() {
        service.createUser("second", PASSWORD, Role.ADMIN);

        service.changeRole(admin.getId(), Role.USER);

        assertThat(auth.isAdmin()).isFalse();
        assertThatThrownBy(service::listUsers).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("createUser создаёт учётную запись, под которой можно войти")
    void createUser() {
        User created = service.createUser("worker", PASSWORD, Role.USER);

        assertThat(created.getRole()).isEqualTo(Role.USER);
        assertThat(new AuthService(userDao).login("worker", PASSWORD)).isEqualTo(created);
    }

    @Test
    @DisplayName("createUser отклоняет занятый логин и слабый пароль")
    void createUserValidation() {
        assertThatThrownBy(() -> service.createUser("admin", PASSWORD, Role.USER))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.createUser("worker", "short", Role.USER))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("changeRole повышает пользователя до администратора")
    void promote() {
        User worker = service.createUser("worker", PASSWORD, Role.USER);

        service.changeRole(worker.getId(), Role.ADMIN);

        assertThat(userDao.countByRole(Role.ADMIN)).isEqualTo(2);
    }

    @Test
    @DisplayName("deleteUser удаляет обычного пользователя")
    void deleteUser() {
        User worker = service.createUser("worker", PASSWORD, Role.USER);

        service.deleteUser(worker.getId());

        assertThat(service.listUsers()).containsExactly(admin);
    }

    @Test
    @DisplayName("нельзя удалить собственную учётную запись")
    void cannotDeleteSelf() {
        service.createUser("second", PASSWORD, Role.ADMIN);

        assertThatThrownBy(() -> service.deleteUser(admin.getId()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("собственную");
    }

    @Test
    @DisplayName("операции над несуществующим пользователем отклоняются")
    void missingUser() {
        assertThatThrownBy(() -> service.deleteUser(999)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.changeRole(999, Role.ADMIN)).isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("listUsers возвращает всех пользователей")
    void listUsers() {
        User worker = service.createUser("worker", PASSWORD, Role.USER);

        assertThat(service.listUsers()).containsExactly(admin, worker);
    }

    @Test
    @DisplayName("обычный пользователь и гость не имеют доступа")
    void requiresAdmin() {
        service.createUser("worker", PASSWORD, Role.USER);
        auth.login("worker", PASSWORD);
        assertThatThrownBy(service::listUsers)
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("администратору");

        auth.logout();
        assertThatThrownBy(() -> service.createUser("guest", PASSWORD, Role.USER))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("вход");
    }
}
