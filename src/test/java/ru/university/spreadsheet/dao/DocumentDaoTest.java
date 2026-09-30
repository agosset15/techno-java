package ru.university.spreadsheet.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.university.spreadsheet.model.Document;
import ru.university.spreadsheet.model.Role;
import ru.university.spreadsheet.model.User;

@DisplayName("DocumentDao")
class DocumentDaoTest extends DatabaseTestSupport {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 9, 1, 10, 15, 30);

    private User owner;

    @BeforeEach
    void createOwner() {
        owner = saveUser("owner", Role.USER);
    }

    @Test
    @DisplayName("save присваивает идентификатор, findById возвращает метаданные")
    void saveAndFindById() {
        Document doc = documentDao.save(new Document(0, "Смета", owner.getId(), CREATED, CREATED.plusHours(1)));

        assertThat(doc.getId()).isPositive();
        Document loaded = documentDao.findById(doc.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Смета");
        assertThat(loaded.getOwnerId()).isEqualTo(owner.getId());
        assertThat(loaded.getCreatedAt()).isEqualTo(CREATED);
        assertThat(loaded.getUpdatedAt()).isEqualTo(CREATED.plusHours(1));
        assertThat(loaded.isModified()).isFalse();
    }

    @Test
    @DisplayName("findById для несуществующего документа возвращает пустой Optional")
    void findMissing() {
        assertThat(documentDao.findById(12345)).isEmpty();
    }

    @Test
    @DisplayName("findByOwner возвращает только документы владельца, последние изменённые первыми")
    void findByOwner() {
        User stranger = saveUser("stranger", Role.USER);
        Document older = documentDao.save(new Document(0, "Старый", owner.getId(), CREATED, CREATED));
        Document newer = documentDao.save(new Document(0, "Новый", owner.getId(), CREATED, CREATED.plusDays(1)));
        documentDao.save(new Document(0, "Чужой", stranger.getId(), CREATED, CREATED));

        assertThat(documentDao.findByOwner(owner.getId()))
                .extracting(Document::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    @DisplayName("update меняет имя и дату изменения")
    void update() {
        Document doc = documentDao.save(new Document(0, "Черновик", owner.getId(), CREATED, CREATED));
        doc.setName("Отчёт");
        doc.setUpdatedAt(CREATED.plusMinutes(5));

        documentDao.update(doc);

        Document loaded = documentDao.findById(doc.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Отчёт");
        assertThat(loaded.getUpdatedAt()).isEqualTo(CREATED.plusMinutes(5));
        assertThat(loaded.getCreatedAt()).isEqualTo(CREATED);
    }

    @Test
    @DisplayName("update несуществующего документа приводит к ошибке")
    void updateMissing() {
        Document ghost = new Document(777, "Призрак", owner.getId(), CREATED, CREATED);

        assertThatThrownBy(() -> documentDao.update(ghost)).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("delete удаляет документ")
    void delete() {
        Document doc = documentDao.save(new Document("Удаляемый", owner.getId()));

        documentDao.delete(doc.getId());

        assertThat(documentDao.findById(doc.getId())).isEmpty();
    }

    @Test
    @DisplayName("документ с несуществующим владельцем не сохраняется (внешний ключ)")
    void rejectsUnknownOwner() {
        assertThatThrownBy(() -> documentDao.save(new Document("Сирота", 9999)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("удаление пользователя каскадно удаляет его документы")
    void cascadeOnUserDelete() {
        Document doc = documentDao.save(new Document("Документ", owner.getId()));

        userDao.delete(owner.getId());

        assertThat(documentDao.findById(doc.getId())).isEmpty();
    }

    @Test
    @DisplayName("findAll возвращает документы всех владельцев")
    void findAll() {
        User stranger = saveUser("stranger", Role.USER);
        Document mine = documentDao.save(new Document(0, "Мой", owner.getId(), CREATED, CREATED));
        Document foreign = documentDao.save(new Document(0, "Чужой", stranger.getId(), CREATED, CREATED.plusDays(1)));

        assertThat(documentDao.findAll()).extracting(Document::getId)
                .containsExactly(foreign.getId(), mine.getId());
    }
}
