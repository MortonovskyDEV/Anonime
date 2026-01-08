const express = require('express');
const http = require('http');
const socketIo = require('socket.io');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const bcrypt = require('bcrypt');
const sqlite3 = require('sqlite3').verbose();
const sharp = require('sharp');
const rateLimit = require('express-rate-limit');

const app = express();
const server = http.createServer(app);
const io = socketIo(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST", "PUT", "DELETE"],
    credentials: true
  },
  transports: ['websocket', 'polling'],
  maxHttpBufferSize: 1e9 // 1GB для больших файлов
});

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 минут
  max: 1000 // лимит запросов
});
app.use('/api/', limiter);

// База данных с улучшенной структурой
const db = new sqlite3.Database('./goth_messenger.db', (err) => {
  if (err) console.error('Database error:', err);
  else console.log('[*] Database connected');
});

// Инициализация таблиц
const initDB = () => {
  db.serialize(() => {
    // Пользователи
    db.run(`CREATE TABLE IF NOT EXISTS users (
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
    )`);

    // Сессии
    db.run(`CREATE TABLE IF NOT EXISTS sessions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      session_token TEXT UNIQUE NOT NULL,
      ip_address TEXT,
      user_agent TEXT,
      expires_at DATETIME NOT NULL,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
    )`);

    // Чаты
    db.run(`CREATE TABLE IF NOT EXISTS chats (
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
    )`);

    // Участники чатов
    db.run(`CREATE TABLE IF NOT EXISTS chat_participants (
      chat_id INTEGER NOT NULL,
      user_id INTEGER NOT NULL,
      role TEXT CHECK(role IN ('member', 'admin', 'owner')) DEFAULT 'member',
      joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      notifications_enabled BOOLEAN DEFAULT 1,
      PRIMARY KEY(chat_id, user_id),
      FOREIGN KEY(chat_id) REFERENCES chats(id) ON DELETE CASCADE,
      FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
    )`);

    // Сообщения
    db.run(`CREATE TABLE IF NOT EXISTS messages (
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
    )`);

    // Звонки
    db.run(`CREATE TABLE IF NOT EXISTS calls (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      call_id TEXT UNIQUE NOT NULL,
      chat_id INTEGER NOT NULL,
      initiator_id INTEGER NOT NULL,
      type TEXT CHECK(type IN ('audio', 'video', 'screen')) DEFAULT 'audio',
      status TEXT CHECK(status IN ('ringing', 'active', 'ended', 'missed', 'rejected')) DEFAULT 'ringing',
      start_time DATETIME,
      end_time DATETIME,
      duration INTEGER,
      participants TEXT DEFAULT '[]',
      recording_url TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY(chat_id) REFERENCES chats(id),
      FOREIGN KEY(initiator_id) REFERENCES users(id)
    )`);

    // Боты
    db.run(`CREATE TABLE IF NOT EXISTS bots (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT UNIQUE NOT NULL,
      token TEXT UNIQUE NOT NULL,
      owner_id INTEGER NOT NULL,
      avatar_url TEXT,
      description TEXT,
      webhook_url TEXT,
      capabilities TEXT DEFAULT '["message","command","file"]',
      is_active BOOLEAN DEFAULT 1,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY(owner_id) REFERENCES users(id)
    )`);

    // Индексы для производительности
    db.run('CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id)');
    db.run('CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at)');
    db.run('CREATE INDEX IF NOT EXISTS idx_chat_participants_user ON chat_participants(user_id)');
    db.run('CREATE INDEX IF NOT EXISTS idx_sessions_token ON sessions(session_token)');
  });
};

initDB();

// Создаём папки
const folders = [
  './uploads',
  './uploads/files',
  './uploads/images',
  './uploads/videos',
  './uploads/audio',
  './uploads/avatars',
  './uploads/stickers',
  './recordings',
  './temp'
];

folders.forEach(folder => {
  if (!fs.existsSync(folder)) {
    fs.mkdirSync(folder, { recursive: true });
  }
});

// Конфигурация загрузки файлов
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    let folder = './uploads/files';
    const mimetype = file.mimetype;
    
    if (mimetype.startsWith('image/')) folder = './uploads/images';
    else if (mimetype.startsWith('video/')) folder = './uploads/videos';
    else if (mimetype.startsWith('audio/')) folder = './uploads/audio';
    
    cb(null, folder);
  },
  filename: (req, file, cb) => {
    const uniqueName = `${Date.now()}-${crypto.randomBytes(8).toString('hex')}${path.extname(file.originalname)}`;
    cb(null, uniqueName);
  }
});

const upload = multer({
  storage,
  limits: {
    fileSize: 2 * 1024 * 1024 * 1024, // 2GB
    files: 10
  },
  fileFilter: (req, file, cb) => {
    const allowedTypes = [
      'image/jpeg', 'image/png', 'image/gif', 'image/webp',
      'video/mp4', 'video/webm', 'video/ogg',
      'audio/mpeg', 'audio/wav', 'audio/ogg',
      'application/pdf', 'application/zip',
      'text/plain', 'application/json'
    ];
    
    if (allowedTypes.includes(file.mimetype)) {
      cb(null, true);
    } else {
      cb(new Error('File type not allowed'), false);
    }
  }
});

// Middleware
app.use(express.json({ limit: '50mb' }));
app.use(express.static('public'));
app.use('/uploads', express.static('uploads'));
app.use('/recordings', express.static('recordings'));

// Генерация токенов
const generateToken = () => crypto.randomBytes(32).toString('hex');
const generateSessionToken = () => crypto.randomBytes(64).toString('hex');

// Аутентификация
const authenticateToken = (req, res, next) => {
  const token = req.headers['authorization']?.split(' ')[1];
  
  if (!token) {
    return res.status(401).json({ error: 'Token required' });
  }
  
  db.get(
    `SELECT u.*, s.expires_at FROM sessions s 
     JOIN users u ON s.user_id = u.id 
     WHERE s.session_token = ? AND s.expires_at > datetime('now')`,
    [token],
    (err, result) => {
      if (err || !result) {
        return res.status(403).json({ error: 'Invalid or expired token' });
      }
      
      req.user = result;
      next();
    }
  );
};

// API Routes
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Регистрация
app.post('/api/register', async (req, res) => {
  try {
    const { username, email, password } = req.body;
    
    if (!username || !password) {
      return res.status(400).json({ error: 'Username and password required' });
    }
    
    const passwordHash = await bcrypt.hash(password, 12);
    const publicKey = crypto.generateKeyPairSync('rsa', {
      modulusLength: 2048,
      publicKeyEncoding: { type: 'spki', format: 'pem' },
      privateKeyEncoding: { type: 'pkcs8', format: 'pem' }
    }).publicKey;
    
    db.run(
      `INSERT INTO users (username, email, password_hash, public_key) VALUES (?, ?, ?, ?)`,
      [username, email, passwordHash, publicKey],
      function(err) {
        if (err) {
          return res.status(400).json({ 
            error: err.message.includes('UNIQUE') ? 'User already exists' : 'Registration failed' 
          });
        }
        
        // Создаём сессию
        const sessionToken = generateSessionToken();
        const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000); // 30 дней
        
        db.run(
          `INSERT INTO sessions (user_id, session_token, expires_at) VALUES (?, ?, ?)`,
          [this.lastID, sessionToken, expiresAt.toISOString()],
          function(err) {
            if (err) {
              return res.status(500).json({ error: 'Session creation failed' });
            }
            
            res.json({
              success: true,
              user: {
                id: this.lastID,
                username,
                email,
                avatar_url: '/avatars/default.png',
                theme: 'dark'
              },
              session_token: sessionToken,
              expires_at: expiresAt
            });
          }
        );
      }
    );
  } catch (error) {
    console.error('Registration error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Вход
app.post('/api/login', async (req, res) => {
  const { username, password } = req.body;
  
  db.get(
    `SELECT id, username, email, password_hash, avatar_url, theme FROM users WHERE username = ? OR email = ?`,
    [username, username],
    async (err, user) => {
      if (err || !user) {
        return res.status(401).json({ error: 'Invalid credentials' });
      }
      
      const valid = await bcrypt.compare(password, user.password_hash);
      if (!valid) {
        return res.status(401).json({ error: 'Invalid credentials' });
      }
      
      // Создаём сессию
      const sessionToken = generateSessionToken();
      const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
      
      db.run(
        `INSERT INTO sessions (user_id, session_token, expires_at) VALUES (?, ?, ?)`,
        [user.id, sessionToken, expiresAt.toISOString()],
        function(err) {
          if (err) {
            return res.status(500).json({ error: 'Session creation failed' });
          }
          
          // Обновляем статус пользователя
          db.run(`UPDATE users SET status = 'online', last_seen = datetime('now') WHERE id = ?`, [user.id]);
          
          res.json({
            success: true,
            user: {
              id: user.id,
              username: user.username,
              email: user.email,
              avatar_url: user.avatar_url,
              theme: user.theme
            },
            session_token: sessionToken,
            expires_at: expiresAt
          });
        }
      );
    }
  );
});

// Получение профиля
app.get('/api/profile', authenticateToken, (req, res) => {
  res.json({
    success: true,
    user: req.user
  });
});

// Загрузка файла
app.post('/api/upload', authenticateToken, upload.single('file'), (req, res) => {
  if (!req.file) {
    return res.status(400).json({ error: 'No file uploaded' });
  }
  
  const fileUrl = `/uploads/${req.file.path.split('uploads/')[1]}`;
  
  res.json({
    success: true,
    file: {
      url: fileUrl,
      name: req.file.originalname,
      size: req.file.size,
      mimetype: req.file.mimetype,
      uploaded_at: new Date().toISOString()
    }
  });
});

// Получение списка чатов
app.get('/api/chats', authenticateToken, (req, res) => {
  const userId = req.user.id;
  
  db.all(
    `SELECT c.*, 
     (SELECT content FROM messages WHERE chat_id = c.id ORDER BY created_at DESC LIMIT 1) as last_message,
     (SELECT created_at FROM messages WHERE chat_id = c.id ORDER BY created_at DESC LIMIT 1) as last_message_time,
     (SELECT COUNT(*) FROM messages WHERE chat_id = c.id AND read_by NOT LIKE ? AND sender_id != ?) as unread_count
     FROM chats c
     JOIN chat_participants cp ON c.id = cp.chat_id
     WHERE cp.user_id = ?
     ORDER BY c.updated_at DESC`,
    [`%"${userId}"%`, userId, userId],
    (err, chats) => {
      if (err) {
        console.error('Chats error:', err);
        return res.status(500).json({ error: 'Database error' });
      }
      
      res.json({ success: true, chats });
    }
  );
});

// Получение сообщений чата
app.get('/api/chats/:chatId/messages', authenticateToken, (req, res) => {
  const { chatId } = req.params;
  const { limit = 50, offset = 0 } = req.query;
  
  // Проверяем доступ к чату
  db.get(
    `SELECT 1 FROM chat_participants WHERE chat_id = ? AND user_id = ?`,
    [chatId, req.user.id],
    (err, hasAccess) => {
      if (err || !hasAccess) {
        return res.status(403).json({ error: 'Access denied' });
      }
      
      db.all(
        `SELECT m.*, u.username, u.avatar_url,
         (SELECT COUNT(*) FROM messages WHERE replied_to = m.id) as reply_count
         FROM messages m
         JOIN users u ON m.sender_id = u.id
         WHERE m.chat_id = ? AND m.deleted = 0
         ORDER BY m.created_at DESC
         LIMIT ? OFFSET ?`,
        [chatId, parseInt(limit), parseInt(offset)],
        (err, messages) => {
          if (err) {
            console.error('Messages error:', err);
            return res.status(500).json({ error: 'Database error' });
          }
          
          res.json({ 
            success: true, 
            messages: messages.reverse(),
            hasMore: messages.length === parseInt(limit)
          });
        }
      );
    }
  );
});

// WebSocket подключения
const activeUsers = new Map(); // userId -> socketId
const typingUsers = new Map(); // chatId -> Set of userIds

io.on('connection', (socket) => {
  console.log(`[+] New connection: ${socket.id}`);
  
  socket.on('authenticate', (data) => {
    const { session_token } = data;
    
    db.get(
      `SELECT u.* FROM sessions s 
       JOIN users u ON s.user_id = u.id 
       WHERE s.session_token = ? AND s.expires_at > datetime('now')`,
      [session_token],
      (err, user) => {
        if (err || !user) {
          socket.emit('auth_error', { error: 'Invalid session' });
          return;
        }
        
        // Сохраняем данные пользователя
        socket.userId = user.id;
        socket.username = user.username;
        activeUsers.set(user.id, socket.id);
        
        // Обновляем статус
        db.run(`UPDATE users SET status = 'online' WHERE id = ?`, [user.id]);
        
        // Уведомляем всех
        io.emit('user_status', {
          userId: user.id,
          status: 'online',
          username: user.username
        });
        
        socket.emit('authenticated', {
          user: {
            id: user.id,
            username: user.username,
            avatar_url: user.avatar_url,
            status: 'online'
          }
        });
        
        console.log(`[*] Authenticated: ${user.username} (${user.id})`);
      }
    );
  });
  
  // Присоединение к чату
  socket.on('join_chat', (data) => {
    const { chatId } = data;
    
    // Проверяем доступ
    db.get(
      `SELECT 1 FROM chat_participants WHERE chat_id = ? AND user_id = ?`,
      [chatId, socket.userId],
      (err, hasAccess) => {
        if (!err && hasAccess) {
          socket.join(`chat_${chatId}`);
          socket.currentChat = chatId;
          
          // Отправляем историю сообщений
          db.all(
            `SELECT m.*, u.username, u.avatar_url 
             FROM messages m 
             JOIN users u ON m.sender_id = u.id 
             WHERE m.chat_id = ? AND m.deleted = 0 
             ORDER BY m.created_at DESC 
             LIMIT 100`,
            [chatId],
            (err, messages) => {
              if (!err) {
                socket.emit('chat_history', {
                  chatId,
                  messages: messages.reverse()
                });
              }
            }
          );
        }
      }
    );
  });
  
  // Отправка сообщения
  socket.on('send_message', (data) => {
    const { chatId, content, type, repliedTo, fileUrl, fileName, fileSize } = data;
    
    if (!socket.userId) return;
    
    // Проверяем доступ к чату
    db.get(
      `SELECT 1 FROM chat_participants WHERE chat_id = ? AND user_id = ?`,
      [chatId, socket.userId],
      (err, hasAccess) => {
        if (err || !hasAccess) {
          socket.emit('error', { message: 'Access denied' });
          return;
        }
        
        // Сохраняем сообщение
        db.run(
          `INSERT INTO messages (chat_id, sender_id, content, type, file_url, file_name, file_size, replied_to) 
           VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
          [chatId, socket.userId, content, type || 'text', fileUrl, fileName, fileSize, repliedTo],
          function(err) {
            if (err) {
              console.error('Message save error:', err);
              socket.emit('error', { message: 'Failed to send message' });
              return;
            }
            
            const messageId = this.lastID;
            
            // Обновляем время чата
            db.run(
              `UPDATE chats SET updated_at = datetime('now'), last_message_id = ? WHERE id = ?`,
              [messageId, chatId]
            );
            
            // Получаем полные данные сообщения
            db.get(
              `SELECT m.*, u.username, u.avatar_url 
               FROM messages m 
               JOIN users u ON m.sender_id = u.id 
               WHERE m.id = ?`,
              [messageId],
              (err, message) => {
                if (!err && message) {
                  // Отправляем всем участникам чата
                  io.to(`chat_${chatId}`).emit('new_message', {
                    chatId,
                    message
                  });
                  
                  // Уведомляем участников (кроме отправителя)
                  db.all(
                    `SELECT user_id FROM chat_participants 
                     WHERE chat_id = ? AND user_id != ? AND notifications_enabled = 1`,
                    [chatId, socket.userId],
                    (err, participants) => {
                      participants.forEach(p => {
                        const userSocketId = activeUsers.get(p.user_id);
                        if (userSocketId && userSocketId !== socket.id) {
                          io.to(userSocketId).emit('notification', {
                            chatId,
                            message: {
                              id: messageId,
                              content: content.length > 100 ? content.substring(0, 100) + '...' : content,
                              sender: socket.username,
                              type
                            }
                          });
                        }
                      });
                    }
                  );
                }
              }
            );
          }
        );
      }
    );
  });
  
  // Пользователь печатает
  socket.on('typing', (data) => {
    const { chatId, isTyping } = data;
    
    if (!socket.userId || !chatId) return;
    
    if (isTyping) {
      if (!typingUsers.has(chatId)) {
        typingUsers.set(chatId, new Set());
      }
      typingUsers.get(chatId).add(socket.userId);
    } else {
      if (typingUsers.has(chatId)) {
        typingUsers.get(chatId).delete(socket.userId);
      }
    }
    
    // Отправляем обновление всем в чате
    socket.to(`chat_${chatId}`).emit('typing_update', {
      chatId,
      typingUsers: Array.from(typingUsers.get(chatId) || [])
    });
  });
  
  // Сообщение прочитано
  socket.on('message_read', (data) => {
    const { messageId } = data;
    
    db.get(
      `SELECT read_by FROM messages WHERE id = ?`,
      [messageId],
      (err, message) => {
        if (!err && message) {
          let readBy = JSON.parse(message.read_by || '[]');
          if (!readBy.includes(socket.userId)) {
            readBy.push(socket.userId);
            
            db.run(
              `UPDATE messages SET read_by = ? WHERE id = ?`,
              [JSON.stringify(readBy), messageId],
              (err) => {
                if (!err) {
                  // Уведомляем отправителя
                  db.get(
                    `SELECT sender_id, chat_id FROM messages WHERE id = ?`,
                    [messageId],
                    (err, msgInfo) => {
                      if (!err && msgInfo) {
                        const senderSocketId = activeUsers.get(msgInfo.sender_id);
                        if (senderSocketId) {
                          io.to(senderSocketId).emit('message_read_receipt', {
                            messageId,
                            readBy,
                            readAt: new Date().toISOString()
                          });
                        }
                      }
                    }
                  );
                }
              }
            );
          }
        }
      }
    );
  });
  
  // Реакции на сообщения
  socket.on('message_reaction', (data) => {
    const { messageId, reaction } = data;
    
    db.get(
      `SELECT reactions FROM messages WHERE id = ?`,
      [messageId],
      (err, message) => {
        if (!err && message) {
          let reactions = JSON.parse(message.reactions || '{}');
          
          if (!reactions[reaction]) {
            reactions[reaction] = [];
          }
          
          if (reactions[reaction].includes(socket.userId)) {
            // Удаляем реакцию
            reactions[reaction] = reactions[reaction].filter(id => id !== socket.userId);
            if (reactions[reaction].length === 0) {
              delete reactions[reaction];
            }
          } else {
            // Добавляем реакцию
            // Удаляем предыдущие реакции пользователя
            Object.keys(reactions).forEach(r => {
              reactions[r] = reactions[r].filter(id => id !== socket.userId);
              if (reactions[r].length === 0) delete reactions[r];
            });
            
            reactions[reaction] = [...(reactions[reaction] || []), socket.userId];
          }
          
          db.run(
            `UPDATE messages SET reactions = ? WHERE id = ?`,
            [JSON.stringify(reactions), messageId],
            (err) => {
              if (!err) {
                // Отправляем обновление всем в чате
                db.get(`SELECT chat_id FROM messages WHERE id = ?`, [messageId], (err, result) => {
                  if (!err && result) {
                    io.to(`chat_${result.chat_id}`).emit('message_reaction_update', {
                      messageId,
                      reactions
                    });
                  }
                });
              }
            }
          );
        }
      }
    );
  });
  
  // Звонки
  socket.on('call_initiate', (data) => {
    const { chatId, type } = data;
    
    // Проверяем доступ к чату
    db.get(`SELECT type FROM chats WHERE id = ?`, [chatId], (err, chat) => {
      if (err || !chat) return;
      
      const callId = `call_${Date.now()}_${crypto.randomBytes(8).toString('hex')}`;
      
      // Сохраняем в БД
      db.run(
        `INSERT INTO calls (call_id, chat_id, initiator_id, type, participants) VALUES (?, ?, ?, ?, ?)`,
        [callId, chatId, socket.userId, type, JSON.stringify([socket.userId])]
      );
      
      // Отправляем участникам чата
      socket.to(`chat_${chatId}`).emit('incoming_call', {
        callId,
        chatId,
        initiatorId: socket.userId,
        initiatorName: socket.username,
        type,
        timestamp: new Date().toISOString()
      });
      
      socket.emit('call_created', { callId });
    });
  });
  
  socket.on('call_join', (data) => {
    const { callId } = data;
    
    db.get(`SELECT * FROM calls WHERE call_id = ?`, [callId], (err, call) => {
      if (err || !call) return;
      
      let participants = JSON.parse(call.participants);
      if (!participants.includes(socket.userId)) {
        participants.push(socket.userId);
        
        db.run(
          `UPDATE calls SET participants = ?, status = 'active' WHERE call_id = ?`,
          [JSON.stringify(participants), callId]
        );
        
        // Уведомляем всех участников
        participants.forEach(userId => {
          const userSocketId = activeUsers.get(userId);
          if (userSocketId) {
            io.to(userSocketId).emit('call_participant_joined', {
              callId,
              userId: socket.userId,
              username: socket.username
            });
          }
        });
      }
    });
  });
  
  socket.on('call_leave', (data) => {
    const { callId } = data;
    
    db.get(`SELECT * FROM calls WHERE call_id = ?`, [callId], (err, call) => {
      if (err || !call) return;
      
      let participants = JSON.parse(call.participants);
      participants = participants.filter(id => id !== socket.userId);
      
      if (participants.length === 0) {
        // Закрываем звонок
        db.run(
          `UPDATE calls SET status = 'ended', end_time = datetime('now') WHERE call_id = ?`,
          [callId]
        );
        
        io.emit('call_ended', { callId });
      } else {
        db.run(
          `UPDATE calls SET participants = ? WHERE call_id = ?`,
          [JSON.stringify(participants), callId]
        );
        
        // Уведомляем остальных участников
        participants.forEach(userId => {
          const userSocketId = activeUsers.get(userId);
          if (userSocketId) {
            io.to(userSocketId).emit('call_participant_left', {
              callId,
              userId: socket.userId
            });
          }
        });
      }
    });
  });
  
  // WebRTC сигналинг
  socket.on('webrtc_signal', (data) => {
    const { toUserId, signal, type } = data;
    
    const targetSocketId = activeUsers.get(parseInt(toUserId));
    if (targetSocketId) {
      io.to(targetSocketId).emit('webrtc_signal', {
        fromUserId: socket.userId,
        signal,
        type
      });
    }
  });
  
  // Отключение
  socket.on('disconnect', () => {
    if (socket.userId) {
      activeUsers.delete(socket.userId);
      
      // Обновляем статус
      db.run(
        `UPDATE users SET status = 'offline', last_seen = datetime('now') WHERE id = ?`,
        [socket.userId]
      );
      
      // Уведомляем всех
      io.emit('user_status', {
        userId: socket.userId,
        status: 'offline',
        username: socket.username
      });
      
      console.log(`[-] Disconnected: ${socket.username} (${socket.userId})`);
    }
  });
});

// Запуск сервера
const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`[*] Server running on port ${PORT}`);
  console.log(`[*] Web interface: http://localhost:${PORT}`);
  console.log(`[*] WebSocket: ws://localhost:${PORT}`);
  console.log(`[*] Database: goth_messenger.db`);
});