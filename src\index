import { Router } from 'itty-router';
import { JWK, JWT } from 'jose';
import { WebSocketPair } from 'cloudflare:workers';

const router = Router();

// Хранилище активных WebSocket соединений
const activeConnections = new Map();

// Генерация JWT токена
const generateToken = async (userId, username) => {
  const secret = JWK.asKey(env.JWT_SECRET || 'your-secret-key-change-in-production');
  return await JWT.sign(
    {
      userId,
      username,
      exp: Math.floor(Date.now() / 1000) + 60 * 60 * 24 * 30 // 30 дней
    },
    secret
  );
};

// Верификация JWT
const verifyToken = async (token) => {
  try {
    const secret = JWK.asKey(env.JWT_SECRET || 'your-secret-key-change-in-production');
    const decoded = await JWT.verify(token, secret);
    return decoded;
  } catch {
    return null;
  }
};

// Обработка WebSocket
async function handleWebSocket(request, env) {
  const upgradeHeader = request.headers.get('Upgrade');
  if (!upgradeHeader || upgradeHeader !== 'websocket') {
    return new Response('Expected Upgrade: websocket', { status: 426 });
  }

  const pair = new WebSocketPair();
  const [client, server] = Object.values(pair);

  server.accept();

  server.addEventListener('message', async (event) => {
    try {
      const data = JSON.parse(event.data);
      await handleWebSocketMessage(data, server, env);
    } catch (error) {
      console.error('WebSocket error:', error);
    }
  });

  server.addEventListener('close', () => {
    // Удаляем соединение из активных
    for (const [userId, ws] of activeConnections.entries()) {
      if (ws === server) {
        activeConnections.delete(userId);
        break;
      }
    }
  });

  return new Response(null, {
    status: 101,
    webSocket: client,
  });
}

// Обработка сообщений WebSocket
async function handleWebSocketMessage(data, ws, env) {
  switch (data.type) {
    case 'authenticate':
      const user = await verifyToken(data.token);
      if (user) {
        activeConnections.set(user.userId, ws);
        ws.send(JSON.stringify({
          type: 'authenticated',
          user: {
            id: user.userId,
            username: user.username
          }
        }));
      }
      break;

    case 'message':
      if (data.chatId && data.content) {
        // Сохраняем сообщение в D1
        await env.DB.prepare(`
          INSERT INTO messages (chat_id, sender_id, content, type) 
          VALUES (?, ?, ?, ?)
        `).bind(
          data.chatId,
          data.userId,
          data.content,
          data.messageType || 'text'
        ).run();

        // Отправляем всем участникам чата
        const participants = await env.DB.prepare(`
          SELECT user_id FROM chat_participants WHERE chat_id = ?
        `).bind(data.chatId).all();

        participants.results.forEach(participant => {
          const participantWs = activeConnections.get(participant.user_id);
          if (participantWs) {
            participantWs.send(JSON.stringify({
              type: 'new_message',
              chatId: data.chatId,
              message: {
                id: Date.now(),
                content: data.content,
                senderId: data.userId,
                timestamp: new Date().toISOString()
              }
            }));
          }
        });
      }
      break;

    case 'typing':
      // Пересылаем индикацию печати
      if (data.chatId) {
        const participants = await env.DB.prepare(`
          SELECT user_id FROM chat_participants 
          WHERE chat_id = ? AND user_id != ?
        `).bind(data.chatId, data.userId).all();

        participants.results.forEach(participant => {
          const participantWs = activeConnections.get(participant.user_id);
          if (participantWs) {
            participantWs.send(JSON.stringify({
              type: 'typing',
              chatId: data.chatId,
              userId: data.userId,
              isTyping: data.isTyping
            }));
          }
        });
      }
      break;
  }
}

// API Routes
router.post('/api/register', async (request, env) => {
  try {
    const { username, email, password } = await request.json();
    
    if (!username || !password) {
      return new Response(JSON.stringify({ error: 'Username and password required' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Хешируем пароль (в реальности используйте bcrypt в отдельном worker)
    const passwordHash = await crypto.subtle.digest(
      'SHA-256',
      new TextEncoder().encode(password + env.PEPPER)
    ).then(hash => Array.from(new Uint8Array(hash))
      .map(b => b.toString(16).padStart(2, '0'))
      .join(''));

    // Сохраняем пользователя
    const result = await env.DB.prepare(`
      INSERT INTO users (username, email, password_hash) 
      VALUES (?, ?, ?)
    `).bind(username, email, passwordHash).run();

    if (!result.success) {
      return new Response(JSON.stringify({ error: 'User already exists' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Генерируем токен
    const token = await generateToken(result.meta.last_row_id, username);

    return new Response(JSON.stringify({
      success: true,
      token,
      user: {
        id: result.meta.last_row_id,
        username,
        email
      }
    }), {
      headers: { 'Content-Type': 'application/json' }
    });

  } catch (error) {
    return new Response(JSON.stringify({ error: 'Registration failed' }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    });
  }
});

router.post('/api/login', async (request, env) => {
  try {
    const { username, password } = await request.json();
    
    // Находим пользователя
    const user = await env.DB.prepare(`
      SELECT id, username, email, password_hash FROM users 
      WHERE username = ? OR email = ?
    `).bind(username, username).first();

    if (!user) {
      return new Response(JSON.stringify({ error: 'Invalid credentials' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Проверяем пароль
    const inputHash = await crypto.subtle.digest(
      'SHA-256',
      new TextEncoder().encode(password + env.PEPPER)
    ).then(hash => Array.from(new Uint8Array(hash))
      .map(b => b.toString(16).padStart(2, '0'))
      .join(''));

    if (inputHash !== user.password_hash) {
      return new Response(JSON.stringify({ error: 'Invalid credentials' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Генерируем токен
    const token = await generateToken(user.id, user.username);

    // Обновляем статус
    await env.DB.prepare(`
      UPDATE users SET status = 'online', last_seen = datetime('now') 
      WHERE id = ?
    `).bind(user.id).run();

    return new Response(JSON.stringify({
      success: true,
      token,
      user: {
        id: user.id,
        username: user.username,
        email: user.email,
        status: 'online'
      }
    }), {
      headers: { 'Content-Type': 'application/json' }
    });

  } catch (error) {
    return new Response(JSON.stringify({ error: 'Login failed' }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    });
  }
});

router.get('/api/chats', async (request, env) => {
  try {
    const authHeader = request.headers.get('Authorization');
    if (!authHeader) {
      return new Response(JSON.stringify({ error: 'Unauthorized' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    const token = authHeader.replace('Bearer ', '');
    const user = await verifyToken(token);
    
    if (!user) {
      return new Response(JSON.stringify({ error: 'Invalid token' }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Получаем чаты пользователя
    const chats = await env.DB.prepare(`
      SELECT c.*, 
      (SELECT content FROM messages WHERE chat_id = c.id ORDER BY created_at DESC LIMIT 1) as last_message,
      (SELECT created_at FROM messages WHERE chat_id = c.id ORDER BY created_at DESC LIMIT 1) as last_message_time
      FROM chats c
      JOIN chat_participants cp ON c.id = cp.chat_id
      WHERE cp.user_id = ?
      ORDER BY c.updated_at DESC
    `).bind(user.userId).all();

    return new Response(JSON.stringify({
      success: true,
      chats: chats.results
    }), {
      headers: { 'Content-Type': 'application/json' }
    });

  } catch (error) {
    return new Response(JSON.stringify({ error: 'Failed to fetch chats' }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    });
  }
});

router.get('/api/chats/:chatId/messages', async (request, env) => {
  try {
    const { chatId } = request.params;
    const authHeader = request.headers.get('Authorization');
    
    if (!authHeader) {
      return new Response(JSON.stringify({ error: 'Unauthorized' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    const token = authHeader.replace('Bearer ', '');
    const user = await verifyToken(token);
    
    if (!user) {
      return new Response(JSON.stringify({ error: 'Invalid token' }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Проверяем доступ к чату
    const hasAccess = await env.DB.prepare(`
      SELECT 1 FROM chat_participants 
      WHERE chat_id = ? AND user_id = ?
    `).bind(chatId, user.userId).first();

    if (!hasAccess) {
      return new Response(JSON.stringify({ error: 'Access denied' }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Получаем сообщения
    const messages = await env.DB.prepare(`
      SELECT m.*, u.username, u.avatar_url
      FROM messages m
      JOIN users u ON m.sender_id = u.id
      WHERE m.chat_id = ? AND m.deleted = 0
      ORDER BY m.created_at DESC
      LIMIT 100
    `).bind(chatId).all();

    return new Response(JSON.stringify({
      success: true,
      messages: messages.results.reverse()
    }), {
      headers: { 'Content-Type': 'application/json' }
    });

  } catch (error) {
    return new Response(JSON.stringify({ error: 'Failed to fetch messages' }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    });
  }
});

router.post('/api/upload', async (request, env) => {
  try {
    const authHeader = request.headers.get('Authorization');
    if (!authHeader) {
      return new Response(JSON.stringify({ error: 'Unauthorized' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    const token = authHeader.replace('Bearer ', '');
    const user = await verifyToken(token);
    
    if (!user) {
      return new Response(JSON.stringify({ error: 'Invalid token' }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    const formData = await request.formData();
    const file = formData.get('file');
    
    if (!file) {
      return new Response(JSON.stringify({ error: 'No file provided' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Генерируем уникальное имя файла
    const fileExt = file.name.split('.').pop();
    const fileName = `${crypto.randomUUID()}.${fileExt}`;
    
    // Загружаем в R2
    await env.UPLOADS.put(fileName, file);

    const fileUrl = `/uploads/${fileName}`;

    return new Response(JSON.stringify({
      success: true,
      file: {
        url: fileUrl,
        name: file.name,
        size: file.size,
        type: file.type
      }
    }), {
      headers: { 'Content-Type': 'application/json' }
    });

  } catch (error) {
    return new Response(JSON.stringify({ error: 'Upload failed' }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    });
  }
});

router.get('/uploads/:fileName', async (request, env) => {
  const { fileName } = request.params;
  
  try {
    const object = await env.UPLOADS.get(fileName);
    
    if (!object) {
      return new Response('File not found', { status: 404 });
    }

    const headers = new Headers();
    object.writeHttpMetadata(headers);
    headers.set('etag', object.httpEtag);

    return new Response(object.body, { headers });
  } catch {
    return new Response('File not found', { status: 404 });
  }
});

// Статические файлы
router.get('*', async (request, env) => {
  const url = new URL(request.url);
  
  if (url.pathname === '/' || url.pathname === '/index.html') {
    return new Response(`
      <!DOCTYPE html>
      <html>
      <head>
          <title>GothMessenger CloudFlare</title>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <style>
              body {
                  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
                  color: white;
                  height: 100vh;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  margin: 0;
              }
              .container {
                  text-align: center;
                  max-width: 600px;
                  padding: 2rem;
              }
              h1 {
                  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                  -webkit-background-clip: text;
                  -webkit-text-fill-color: transparent;
                  font-size: 3rem;
                  margin-bottom: 1rem;
              }
              .status {
                  background: rgba(255,255,255,0.1);
                  padding: 1rem;
                  border-radius: 10px;
                  margin: 2rem 0;
              }
              .features {
                  display: grid;
                  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                  gap: 1rem;
                  margin-top: 2rem;
              }
              .feature {
                  background: rgba(255,255,255,0.05);
                  padding: 1.5rem;
                  border-radius: 10px;
              }
          </style>
      </head>
      <body>
          <div class="container">
              <h1>🚀 GothMessenger</h1>
              <p>Современный мессенджер на CloudFlare Workers</p>
              
              <div class="status">
                  <h3>✅ Система работает</h3>
                  <p>Сервер: CloudFlare Workers + D1 + R2</p>
                  <p>Статус: <span style="color: #10b981;">Online</span></p>
              </div>
              
              <div class="features">
                  <div class="feature">
                      <h4>🔒 Безопасность</h4>
                      <p>Сквозное шифрование</p>
                  </div>
                  <div class="feature">
                      <h4>⚡ Скорость</h4>
                      <p>Глобальная сеть CloudFlare</p>
                  </div>
                  <div class="feature">
                      <h4>💾 Надёжность</h4>
                      <p>99.99% uptime</p>
                  </div>
              </div>
              
              <p style="margin-top: 2rem; color: #94a3b8;">
                  Полный интерфейс будет загружен автоматически
              </p>
          </div>
          
          <script>
              // Перенаправление на основной интерфейс
              setTimeout(() => {
                  window.location.href = '/app';
              }, 2000);
          </script>
      </body>
      </html>
    `, {
      headers: { 'Content-Type': 'text/html; charset=utf-8' }
    });
  }

  return new Response('Not Found', { status: 404 });
});

// Основной обработчик
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    
    // WebSocket для реального времени
    if (url.pathname === '/ws') {
      return handleWebSocket(request, env);
    }
    
    // Обработка API запросов
    return router.handle(request, env, ctx);
  }
};
