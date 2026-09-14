package ru.university.spreadsheet.dao;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import ru.university.spreadsheet.model.Role;
import ru.university.spreadsheet.model.User;

/**
 * Базовый класс тестов, работающих с базой данных: перед каждым тестом создаёт пустую
 * базу данных во временном каталоге и выполняет миграцию схемы.
 */
public abstract class DatabaseTestSupport {

    @TempDir
    protected Path tempDir;

    protected Database database;
    protected UserDao userDao;
    protected DocumentDao documentDao;
    protected CellDao cellDao;

    @BeforeEach
    protected void setUpDatabase() {
        System.setProperty(Database.DB_PATH_PROPERTY, tempDir.resolve("test.db").toString());
        database = Database.getInstance();
        database.close();
        database.migrate();
        userDao = new UserDao(database);
        documentDao = new DocumentDao(database);
        cellDao = new CellDao(database);
    }

    @AfterEach
    protected void tearDownDatabase() {
        database.close();
        System.clearProperty(Database.DB_PATH_PROPERTY);
    }

    protected User saveUser(String login, Role role) {
        return userDao.save(new User(0, login, "hash-" + login, "salt-" + login, role));
    }
}
