-- Схема базы данных ИС «Электронная таблица» (SQLite)

PRAGMA foreign_keys = ON;

-- Учётные записи пользователей
CREATE TABLE IF NOT EXISTS users (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    login    TEXT    NOT NULL UNIQUE,
    pwd_hash TEXT    NOT NULL,
    salt     TEXT    NOT NULL,
    role     TEXT    NOT NULL CHECK (role IN ('USER', 'ADMIN'))
);

-- Метаданные табличных документов
CREATE TABLE IF NOT EXISTS documents (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL,
    owner_id   INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TEXT    NOT NULL,
    updated_at TEXT    NOT NULL
);

-- Непустые ячейки документов; цвета хранятся в виде #RRGGBB
CREATE TABLE IF NOT EXISTS cells (
    doc_id  INTEGER NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    row_idx INTEGER NOT NULL CHECK (row_idx >= 0),
    col_idx INTEGER NOT NULL CHECK (col_idx >= 0),
    raw     TEXT    NOT NULL DEFAULT '',
    bg      TEXT    NOT NULL DEFAULT '#FFFFFF',
    fg      TEXT    NOT NULL DEFAULT '#000000',
    bold    INTEGER NOT NULL DEFAULT 0 CHECK (bold IN (0, 1)),
    PRIMARY KEY (doc_id, row_idx, col_idx)
);

CREATE INDEX IF NOT EXISTS idx_documents_owner ON documents(owner_id);
