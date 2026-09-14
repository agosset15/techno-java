package ru.university.spreadsheet.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import ru.university.spreadsheet.dao.UserDao;
import ru.university.spreadsheet.model.Role;
import ru.university.spreadsheet.model.User;

/**
 * Сервис аутентификации: регистрация, вход и выход пользователя.
 * <p>
 * Пароли хранятся в виде хеша PBKDF2WithHmacSHA256 (120 000 итераций, ключ 256 бит)
 * со случайной 16-байтовой солью; хеш и соль кодируются в Base64. После пяти неудачных
 * попыток входа подряд вход под этим логином блокируется на 60 секунд. Счётчик
 * попыток хранится в памяти и сбрасывается при успешном входе или по истечении блокировки.
 */
public class AuthService {

    /** Минимальная длина пароля. */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /** Число неудачных попыток входа подряд, после которого вход блокируется. */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** Длительность блокировки входа. */
    public static final Duration LOCK_DURATION = Duration.ofSeconds(60);

    private static final Pattern LOGIN_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,32}");
    private static final String HASH_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int HASH_ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final double MILLIS_PER_SECOND = 1000.0;

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());

    private final UserDao userDao;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Integer> failedAttempts = new HashMap<>();
    private final Map<String, Instant> lockedUntil = new HashMap<>();
    private final String dummySalt = generateSalt();
    private User currentUser;

    /**
     * Создаёт сервис, работающий с базой данных по умолчанию и системными часами.
     */
    public AuthService() {
        this(new UserDao());
    }

    /**
     * Создаёт сервис с указанным DAO пользователей и системными часами.
     *
     * @param userDao DAO пользователей
     */
    public AuthService(UserDao userDao) {
        this(userDao, Clock.systemUTC());
    }

    /**
     * Создаёт сервис с указанным DAO пользователей и источником времени
     * (используется в тестах блокировки).
     *
     * @param userDao DAO пользователей
     * @param clock   источник текущего времени
     */
    public AuthService(UserDao userDao, Clock clock) {
        this.userDao = Objects.requireNonNull(userDao, "userDao");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Регистрирует нового пользователя. Первый зарегистрированный пользователь получает
     * роль {@link Role#ADMIN}, остальные — {@link Role#USER}. Вход после регистрации
     * не выполняется.
     *
     * @param login    логин: от 3 до 32 символов из латинских букв, цифр и подчёркивания
     * @param password пароль не короче {@value #MIN_PASSWORD_LENGTH} символов
     * @return сохранённый пользователь с присвоенным идентификатором
     * @throws AuthException если логин или пароль некорректны либо логин уже занят
     * @throws ru.university.spreadsheet.dao.DataAccessException при ошибке базы данных
     */
    public synchronized User register(String login, String password) {
        Role role = userDao.findAll().isEmpty() ? Role.ADMIN : Role.USER;
        User user = userDao.save(prepareNewUser(login, password, role));
        LOG.info(() -> "Зарегистрирован пользователь " + user.getLogin() + " с ролью " + user.getRole());
        return user;
    }

    /**
     * Выполняет вход и делает пользователя текущим.
     *
     * @param login    логин
     * @param password пароль
     * @return вошедший пользователь
     * @throws AuthException если учётные данные неверны или вход временно заблокирован
     * @throws ru.university.spreadsheet.dao.DataAccessException при ошибке базы данных
     */
    public synchronized User login(String login, String password) {
        String key = login == null ? "" : login;
        Instant now = clock.instant();
        ensureNotLocked(key, now);

        Optional<User> found = login == null ? Optional.empty() : userDao.findByLogin(login);
        boolean valid;
        if (found.isPresent()) {
            valid = verify(password, found.get().getPwdHash(), found.get().getSalt());
        } else {
            // Вычисление хеша для несуществующего логина выравнивает время ответа.
            verify(password, "", dummySalt);
            valid = false;
        }

        if (!valid) {
            throw registerFailure(key, now);
        }
        failedAttempts.remove(key);
        currentUser = found.get();
        LOG.info(() -> "Выполнен вход пользователя " + key);
        return currentUser;
    }

    /**
     * Завершает сеанс текущего пользователя.
     */
    public synchronized void logout() {
        currentUser = null;
    }

    /**
     * Возвращает пользователя, выполнившего вход.
     *
     * @return текущий пользователь или пустой {@link Optional}, если вход не выполнен
     */
    public synchronized Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    /**
     * Проверяет, является ли текущий пользователь администратором.
     *
     * @return {@code true}, если вход выполнен пользователем с ролью {@link Role#ADMIN}
     */
    public synchronized boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == Role.ADMIN;
    }

    /**
     * Проверяет пароль по сохранённым хешу и соли. Сравнение хешей выполняется
     * за время, не зависящее от совпадающего префикса, что защищает от атак по времени.
     *
     * @param password проверяемый пароль
     * @param hash     сохранённый хеш в Base64
     * @param salt     сохранённая соль в Base64
     * @return {@code true}, если пароль соответствует хешу; {@code false} также при
     *         {@code null}-аргументах и некорректной кодировке Base64
     */
    public boolean verify(String password, String hash, String salt) {
        if (password == null || hash == null || salt == null) {
            return false;
        }
        try {
            byte[] expected = Base64.getDecoder().decode(hash);
            byte[] actual = Base64.getDecoder().decode(hash(password, salt));
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Проверяет логин и пароль и формирует несохранённого пользователя со свежей солью
     * и хешем пароля. Используется при регистрации и при создании учётной записи
     * администратором.
     *
     * @param login    логин
     * @param password пароль
     * @param role     роль
     * @return пользователь с идентификатором {@code 0}
     * @throws AuthException если логин или пароль некорректны либо логин уже занят
     */
    User prepareNewUser(String login, String password, Role role) {
        Objects.requireNonNull(role, "role");
        if (login == null || !LOGIN_PATTERN.matcher(login).matches()) {
            throw new AuthException(
                    "Логин должен содержать от 3 до 32 символов: латинские буквы, цифры или подчёркивание");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new AuthException("Пароль должен содержать не менее " + MIN_PASSWORD_LENGTH + " символов");
        }
        if (userDao.findByLogin(login).isPresent()) {
            throw new AuthException("Логин «" + login + "» уже занят");
        }
        String salt = generateSalt();
        return new User(0, login, hash(password, salt), salt, role);
    }

    /**
     * Обновляет сведения о текущем пользователе, если изменена именно его учётная запись.
     *
     * @param updated изменённый пользователь
     */
    synchronized void refreshCurrentUser(User updated) {
        if (currentUser != null && currentUser.getId() == updated.getId()) {
            currentUser = updated;
        }
    }

    private void ensureNotLocked(String key, Instant now) {
        Instant until = lockedUntil.get(key);
        if (until == null) {
            return;
        }
        if (now.isBefore(until)) {
            throw lockedException(until, now);
        }
        lockedUntil.remove(key);
        failedAttempts.remove(key);
    }

    private AuthException registerFailure(String key, Instant now) {
        int attempts = failedAttempts.merge(key, 1, Integer::sum);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            Instant until = now.plus(LOCK_DURATION);
            lockedUntil.put(key, until);
            LOG.warning(() -> "Вход под логином " + key + " заблокирован после " + attempts + " неудачных попыток");
            return lockedException(until, now);
        }
        return new AuthException(
                "Неверный логин или пароль. Осталось попыток: " + (MAX_FAILED_ATTEMPTS - attempts));
    }

    private static AuthException lockedException(Instant until, Instant now) {
        long seconds = (long) Math.ceil(Duration.between(now, until).toMillis() / MILLIS_PER_SECOND);
        return new AuthException("Превышено число попыток входа. Повторите через " + seconds + " с");
    }

    private String hash(String pwd, String salt) {
        PBEKeySpec spec = new PBEKeySpec(pwd.toCharArray(), Base64.getDecoder().decode(salt),
                HASH_ITERATIONS, KEY_LENGTH_BITS);
        try {
            byte[] key = SecretKeyFactory.getInstance(HASH_ALGORITHM).generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(key);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Алгоритм " + HASH_ALGORITHM + " недоступен", e);
        } finally {
            spec.clearPassword();
        }
    }

    private String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
}
