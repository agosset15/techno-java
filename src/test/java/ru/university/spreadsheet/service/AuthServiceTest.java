package ru.university.spreadsheet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.university.spreadsheet.dao.DatabaseTestSupport;
import ru.university.spreadsheet.model.Role;
import ru.university.spreadsheet.model.User;

@DisplayName("AuthService: регистрация и вход")
class AuthServiceTest extends DatabaseTestSupport {

    private static final String PASSWORD = "secret123";

    private MutableClock clock;
    private AuthService auth;

    @BeforeEach
    void createService() {
        clock = new MutableClock(Instant.parse("2026-09-14T10:00:00Z"));
        auth = new AuthService(userDao, clock);
    }

    @Test
    @DisplayName("успешная регистрация сохраняет хеш и соль, а не пароль")
    void registerSuccess() {
        User user = auth.register("alice", PASSWORD);

        assertThat(user.getId()).isPositive();
        assertThat(user.getPwdHash()).isNotBlank().doesNotContain(PASSWORD);
        assertThat(Base64.getDecoder().decode(user.getSalt())).hasSize(16);
        assertThat(Base64.getDecoder().decode(user.getPwdHash())).hasSize(32);
        assertThat(userDao.findByLogin("alice")).contains(user);
        assertThat(auth.getCurrentUser()).as("регистрация не выполняет вход").isEmpty();
    }

    @Test
    @DisplayName("первый пользователь получает роль ADMIN, следующие — USER")
    void firstUserIsAdmin() {
        assertThat(auth.register("first", PASSWORD).getRole()).isEqualTo(Role.ADMIN);
        assertThat(auth.register("second", PASSWORD).getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("повторная регистрация с тем же логином отклоняется")
    void duplicateLogin() {
        auth.register("alice", PASSWORD);

        assertThatThrownBy(() -> auth.register("alice", "другой-пароль"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("занят");
    }

    @Test
    @DisplayName("пароль короче 8 символов отклоняется")
    void weakPassword() {
        assertThatThrownBy(() -> auth.register("alice", "1234567"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("8");
        assertThat(userDao.findByLogin("alice")).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"ab", "abcdefghijklmnopqrstuvwxyz0123456", "имя", "bad login", "a-b", "user@mail"})
    @DisplayName("некорректный логин отклоняется")
    void invalidLogin(String login) {
        assertThatThrownBy(() -> auth.register(login, PASSWORD)).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("логин из 32 символов с подчёркиванием допустим")
    void longestLogin() {
        String login = "user_" + "x".repeat(27);

        assertThat(auth.register(login, PASSWORD).getLogin()).hasSize(32);
    }

    @Test
    @DisplayName("вход с верным паролем устанавливает текущего пользователя")
    void loginSuccess() {
        User registered = auth.register("admin", PASSWORD);

        User logged = auth.login("admin", PASSWORD);

        assertThat(logged).isEqualTo(registered);
        assertThat(auth.getCurrentUser()).contains(registered);
        assertThat(auth.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("вход с неверным паролем отклоняется")
    void loginWrongPassword() {
        auth.register("alice", PASSWORD);

        assertThatThrownBy(() -> auth.login("alice", "wrong-password"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Неверный логин или пароль");
        assertThat(auth.getCurrentUser()).isEmpty();
    }

    @Test
    @DisplayName("вход под несуществующим логином отклоняется тем же сообщением")
    void loginUnknownUser() {
        assertThatThrownBy(() -> auth.login("ghost", PASSWORD))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Неверный логин или пароль");
    }

    @Test
    @DisplayName("после 5 неудачных попыток вход блокируется на 60 секунд даже с верным паролем")
    void lockAfterFiveFailures() {
        auth.register("alice", PASSWORD);
        for (int i = 1; i < AuthService.MAX_FAILED_ATTEMPTS; i++) {
            assertThatThrownBy(() -> auth.login("alice", "wrong-password"))
                    .hasMessageContaining("Осталось попыток");
        }

        assertThatThrownBy(() -> auth.login("alice", "wrong-password"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Повторите через 60 с");
        assertThatThrownBy(() -> auth.login("alice", PASSWORD))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Превышено число попыток");

        clock.advance(Duration.ofSeconds(59));
        assertThatThrownBy(() -> auth.login("alice", PASSWORD))
                .hasMessageContaining("Повторите через 1 с");

        clock.advance(Duration.ofSeconds(1));
        assertThat(auth.login("alice", PASSWORD).getLogin()).isEqualTo("alice");
    }

    @Test
    @DisplayName("успешный вход сбрасывает счётчик неудачных попыток")
    void successResetsCounter() {
        auth.register("alice", PASSWORD);
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS - 1; i++) {
            assertThatThrownBy(() -> auth.login("alice", "wrong-password")).isInstanceOf(AuthException.class);
        }
        auth.login("alice", PASSWORD);

        assertThatThrownBy(() -> auth.login("alice", "wrong-password"))
                .hasMessageContaining("Осталось попыток: 4");
    }

    @Test
    @DisplayName("блокировка одного логина не затрагивает другие")
    void lockIsPerLogin() {
        auth.register("alice", PASSWORD);
        auth.register("bob", PASSWORD);
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS; i++) {
            assertThatThrownBy(() -> auth.login("alice", "wrong-password")).isInstanceOf(AuthException.class);
        }

        assertThat(auth.login("bob", PASSWORD).getLogin()).isEqualTo("bob");
    }

    @Test
    @DisplayName("одинаковый пароль с разными солями даёт разные хеши")
    void differentSaltsGiveDifferentHashes() {
        User alice = auth.register("alice", PASSWORD);
        User bob = auth.register("bob", PASSWORD);

        assertThat(alice.getSalt()).isNotEqualTo(bob.getSalt());
        assertThat(alice.getPwdHash()).isNotEqualTo(bob.getPwdHash());
        assertThat(auth.verify(PASSWORD, alice.getPwdHash(), alice.getSalt())).isTrue();
        assertThat(auth.verify(PASSWORD, bob.getPwdHash(), bob.getSalt())).isTrue();
    }

    @Test
    @DisplayName("verify отклоняет неверный пароль, чужую соль и некорректные данные")
    void verifyRejectsInvalid() {
        User alice = auth.register("alice", PASSWORD);
        User bob = auth.register("bob", "другой-пароль");

        assertThat(auth.verify("wrong-password", alice.getPwdHash(), alice.getSalt())).isFalse();
        assertThat(auth.verify(PASSWORD, alice.getPwdHash(), bob.getSalt())).isFalse();
        assertThat(auth.verify(PASSWORD, "не base64!", alice.getSalt())).isFalse();
        assertThat(auth.verify(null, alice.getPwdHash(), alice.getSalt())).isFalse();
    }

    @Test
    @DisplayName("logout завершает сеанс")
    void logout() {
        auth.register("alice", PASSWORD);
        auth.login("alice", PASSWORD);

        auth.logout();

        assertThat(auth.getCurrentUser()).isEmpty();
        assertThat(auth.isAdmin()).isFalse();
    }
}
