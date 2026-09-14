package ru.university.spreadsheet.dao;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import ru.university.spreadsheet.model.Role;
import ru.university.spreadsheet.model.User;

/**
 * Базовый класс тестов слоя DAO: перед каждым тестом создаёт пустую базу данных
 * во временном каталоге и выполняет миграцию схемы.
 */
abstract class DatabaseTestSupport {

    @TempDir
    Path tempDir;

    Database database;
    UserDao userDao;
    DocumentDao documentDao;
    CellDao cellDao;

    @BeforeEach
    void setUpDatabase() {
        System.setProperty(Database.DB_PATH_PROPERTY, tempDir.resolve("test.db").toString());
        database = Database.getInstance();
        database.close();
        database.migrate();
        userDao = new UserDao(database);
        documentDao = new DocumentDao(database);
        cellDao = new CellDao(database);
    }

    @AfterEach
    void tearDownDatabase() {
        database.close();
        System.clearProperty(Database.DB_PATH_PROPERTY);
    }

    User saveUser(String login, Role role) {
        return userDao.save(new User(0, login, "hash-" + login, "salt-" + login, role));
    }
}
