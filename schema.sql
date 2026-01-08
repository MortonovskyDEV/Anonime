-- Пользователи
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    email TEXT UNIQUE,
    password_hash TEXT NOT NULL,
    avatar_url TEXT DEFAULT '/avatars/default.png',
    public_key TEXT,
    status TEXT DEFAULT 'offline',
    last_seen DATETIME,
    theme TEXT DEFAULT 'dark',
    language TEXT DEFAULT 'ru',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Сессии
CREATE TABLE IF NOT EXISTS sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    session_token TEXT UNIQUE NOT NULL,
    ip_address TEXT,
    user_agent TEXT,
    expires_at DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Чаты
CREATE TABLE IF NOT EXISTS chats (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT CHECK(type IN ('private', 'group', 'channel')) DEFAULT 'private',
    name TEXT,
    avatar_url TEXT,
    created_by INTEGER,
    encrypted_key TEXT,
    last_message_id INTEGER,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(created_by) REFERENCES users(id)
);

-- Участники чатов
CREATE TABLE IF NOT EXISTS chat_participants (
    chat_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    role TEXT CHECK(role IN ('member', 'admin', 'owner')) DEFAULT 'member',
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    notifications_enabled BOOLEAN DEFAULT 1,
    PRIMARY KEY(chat_id, user_id),
    FOREIGN KEY(chat_id) REFERENCES chats(id) ON DELETE CASCADE,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Сообщения
CREATE TABLE IF NOT EXISTS messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id INTEGER NOT NULL,
    sender_id INTEGER NOT NULL,
    content TEXT,
    type TEXT CHECK(type IN ('text', 'image', 'video', 'audio', 'file', 'sticker', 'system')) DEFAULT 'text',
    encrypted BOOLEAN DEFAULT 1,
    encryption_key TEXT,
    iv TEXT,
    file_url TEXT,
    file_size INTEGER,
    file_name TEXT,
    replied_to INTEGER,
    edited BOOLEAN DEFAULT 0,
    edited_at DATETIME,
    deleted BOOLEAN DEFAULT 0,
    deleted_at DATETIME,
    read_by TEXT DEFAULT '[]',
    reactions TEXT DEFAULT '{}',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(chat_id) REFERENCES chats(id) ON DELETE CASCADE,
    FOREIGN KEY(sender_id) REFERENCES users(id),
    FOREIGN KEY(replied_to) REFERENCES messages(id)
);

-- Индексы
CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at);
CREATE INDEX IF NOT EXISTS idx_chat_participants_user ON chat_participants(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_token ON sessions(session_token);