using System;
using System.Collections.Generic;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using GothMessenger.Models;

namespace GothMessenger.Services
{
    public class WebSocketClient : IDisposable
    {
        private readonly ClientWebSocket _webSocket;
        private readonly Uri _serverUri;
        private readonly string _token;
        private CancellationTokenSource _cancellationTokenSource;
        
        public event EventHandler<WebSocketMessage> MessageReceived;
        public event EventHandler Connected;
        public event EventHandler<string> ConnectionClosed;
        
        public bool IsConnected => _webSocket.State == WebSocketState.Open;
        
        public WebSocketClient(string serverUrl, string token)
        {
            _webSocket = new ClientWebSocket();
            _serverUri = new Uri($"{serverUrl}/ws");
            _token = token;
            _cancellationTokenSource = new CancellationTokenSource();
            
            // Настройка заголовков
            _webSocket.Options.SetRequestHeader("Authorization", $"Bearer {token}");
        }
        
        public async Task ConnectAsync()
        {
            try
            {
                await _webSocket.ConnectAsync(_serverUri, _cancellationTokenSource.Token);
                Connected?.Invoke(this, EventArgs.Empty);
                
                // Начинаем слушать сообщения
                _ = Task.Run(ListenAsync, _cancellationTokenSource.Token);
                
                // Аутентификация
                await SendAsync(new WebSocketMessage
                {
                    Type = "authenticate"
                });
            }
            catch (Exception ex)
            {
                Console.WriteLine($"WebSocket connection error: {ex.Message}");
                throw;
            }
        }
        
        private async Task ListenAsync()
        {
            var buffer = new byte[4096];
            
            try
            {
                while (_webSocket.State == WebSocketState.Open && 
                       !_cancellationTokenSource.Token.IsCancellationRequested)
                {
                    var result = await _webSocket.ReceiveAsync(
                        new ArraySegment<byte>(buffer), 
                        _cancellationTokenSource.Token);
                    
                    if (result.MessageType == WebSocketMessageType.Text)
                    {
                        var messageJson = Encoding.UTF8.GetString(buffer, 0, result.Count);
                        var message = JsonConvert.DeserializeObject<WebSocketMessage>(messageJson);
                        
                        MessageReceived?.Invoke(this, message);
                    }
                    else if (result.MessageType == WebSocketMessageType.Close)
                    {
                        await _webSocket.CloseAsync(
                            WebSocketCloseStatus.NormalClosure,
                            "Connection closed",
                            CancellationToken.None);
                        
                        ConnectionClosed?.Invoke(this, "Connection closed");
                        break;
                    }
                }
            }
            catch (OperationCanceledException)
            {
                // Отмена операции - нормальное завершение
            }
            catch (Exception ex)
            {
                Console.WriteLine($"WebSocket listen error: {ex.Message}");
                ConnectionClosed?.Invoke(this, ex.Message);
            }
        }
        
        public async Task SendAsync(WebSocketMessage message)
        {
            if (!IsConnected)
                throw new InvalidOperationException("WebSocket is not connected");
            
            var messageJson = JsonConvert.SerializeObject(message);
            var buffer = Encoding.UTF8.GetBytes(messageJson);
            
            await _webSocket.SendAsync(
                new ArraySegment<byte>(buffer),
                WebSocketMessageType.Text,
                true,
                _cancellationTokenSource.Token);
        }
        
        public async Task SendMessageAsync(long chatId, string content)
        {
            await SendAsync(new WebSocketMessage
            {
                Type = "message",
                ChatId = chatId,
                Message = new Message
                {
                    ChatId = chatId,
                    Content = content,
                    Timestamp = DateTime.UtcNow
                }
            });
        }
        
        public async Task SendTypingAsync(long chatId, bool isTyping)
        {
            await SendAsync(new WebSocketMessage
            {
                Type = "typing",
                Typing = new TypingEvent
                {
                    ChatId = chatId,
                    IsTyping = isTyping
                }
            });
        }
        
        public async Task DisconnectAsync()
        {
            if (_webSocket.State == WebSocketState.Open)
            {
                await _webSocket.CloseAsync(
                    WebSocketCloseStatus.NormalClosure,
                    "Client disconnect",
                    CancellationToken.None);
            }
            
            _cancellationTokenSource.Cancel();
        }
        
        public void Dispose()
        {
            _cancellationTokenSource?.Cancel();
            _webSocket?.Dispose();
            _cancellationTokenSource?.Dispose();
        }
    }
}