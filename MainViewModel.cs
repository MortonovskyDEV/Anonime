using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using System.Windows.Input;
using Microsoft.Toolkit.Mvvm.ComponentModel;
using Microsoft.Toolkit.Mvvm.Input;
using GothMessenger.Models;
using GothMessenger.Services;

namespace GothMessenger.ViewModels
{
    public class MainViewModel : ObservableObject
    {
        private readonly ApiService _apiService;
        private readonly WebSocketClient _webSocketClient;
        private readonly string _serverUrl = "https://your-worker.workers.dev";
        
        private User _currentUser;
        private Chat _currentChat;
        private string _messageText;
        private string _typingIndicator;
        private bool _isInCall;
        private string _callDuration;
        
        public User CurrentUser
        {
            get => _currentUser;
            set => SetProperty(ref _currentUser, value);
        }
        
        public Chat CurrentChat
        {
            get => _currentChat;
            set => SetProperty(ref _currentChat, value);
        }
        
        public string MessageText
        {
            get => _messageText;
            set => SetProperty(ref _messageText, value);
        }
        
        public string TypingIndicator
        {
            get => _typingIndicator;
            set => SetProperty(ref _typingIndicator, value);
        }
        
        public bool IsInCall
        {
            get => _isInCall;
            set => SetProperty(ref _isInCall, value);
        }
        
        public string CallDuration
        {
            get => _callDuration;
            set => SetProperty(ref _callDuration, value);
        }
        
        public ObservableCollection<Chat> Chats { get; } = new ObservableCollection<Chat>();
        public ObservableCollection<Message> Messages { get; } = new ObservableCollection<Message>();
        
        public ICommand LoadChatsCommand { get; }
        public ICommand SelectChatCommand { get; }
        public ICommand SendMessageCommand { get; }
        public ICommand StartVoiceCallCommand { get; }
        public ICommand StartVideoCallCommand { get; }
        public ICommand AttachFileCommand { get; }
        public ICommand ShowEmojiCommand { get; }
        public ICommand LogoutCommand { get; }
        
        public MainViewModel()
        {
            _apiService = new ApiService(_serverUrl);
            
            // Команды
            LoadChatsCommand = new AsyncRelayCommand(LoadChatsAsync);
            SelectChatCommand = new RelayCommand<Chat>(SelectChat);
            SendMessageCommand = new AsyncRelayCommand(SendMessageAsync, CanSendMessage);
            StartVoiceCallCommand = new RelayCommand(StartVoiceCall);
            StartVideoCallCommand = new RelayCommand(StartVideoCall);
            AttachFileCommand = new RelayCommand(AttachFile);
            ShowEmojiCommand = new RelayCommand(ShowEmoji);
            LogoutCommand = new RelayCommand(Logout);
            
            // Загрузка данных
            _ = InitializeAsync();
        }
        
        private async Task InitializeAsync()
        {
            // Загрузка токена из настроек
            var token = Properties.Settings.Default.AuthToken;
            if (string.IsNullOrEmpty(token))
            {
                // Показать окно входа
                ShowLoginWindow();
                return;
            }
            
            try
            {
                // Загрузка профиля
                var profileResponse = await _apiService.GetProfileAsync(token);
                if (profileResponse.Success)
                {
                    CurrentUser = profileResponse.Data;
                    
                    // Подключение WebSocket
                    _webSocketClient = new WebSocketClient(_serverUrl, token);
                    _webSocketClient.MessageReceived += OnWebSocketMessage;
                    _webSocketClient.Connected += OnWebSocketConnected;
                    _webSocketClient.ConnectionClosed += OnWebSocketClosed;
                    
                    await _webSocketClient.ConnectAsync();
                    await LoadChatsAsync();
                }
                else
                {
                    ShowLoginWindow();
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Initialization error: {ex.Message}");
                ShowLoginWindow();
            }
        }
        
        private async Task LoadChatsAsync()
        {
            try
            {
                var response = await _apiService.GetChatsAsync();
                if (response.Success)
                {
                    Chats.Clear();
                    foreach (var chat in response.Data)
                    {
                        Chats.Add(chat);
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Load chats error: {ex.Message}");
            }
        }
        
        private void SelectChat(Chat chat)
        {
            if (chat == null) return;
            
            // Сброс выделения у всех чатов
            foreach (var c in Chats)
            {
                c.IsSelected = false;
            }
            
            chat.IsSelected = true;
            CurrentChat = chat;
            Messages.Clear();
            
            // Загрузка сообщений
            _ = LoadMessagesAsync(chat.Id);
        }
        
        private async Task LoadMessagesAsync(long chatId)
        {
            try
            {
                var response = await _apiService.GetMessagesAsync(chatId);
                if (response.Success)
                {
                    Messages.Clear();
                    foreach (var message in response.Data.OrderBy(m => m.Timestamp))
                    {
                        message.IsOwnMessage = message.SenderId == CurrentUser?.Id;
                        Messages.Add(message);
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Load messages error: {ex.Message}");
            }
        }
        
        private bool CanSendMessage()
        {
            return !string.IsNullOrWhiteSpace(MessageText) && 
                   CurrentChat != null && 
                   _webSocketClient?.IsConnected == true;
        }
        
        private async Task SendMessageAsync()
        {
            if (!CanSendMessage()) return;
            
            try
            {
                await _webSocketClient.SendMessageAsync(CurrentChat.Id, MessageText);
                MessageText = string.Empty;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Send message error: {ex.Message}");
            }
        }
        
        private void StartVoiceCall()
        {
            if (CurrentChat == null) return;
            
            // Инициирование голосового звонка
            IsInCall = true;
            // TODO: WebRTC реализация
        }
        
        private void StartVideoCall()
        {
            if (CurrentChat == null) return;
            
            // Инициирование видеозвонка
            IsInCall = true;
            // TODO: WebRTC реализация
        }
        
        private void AttachFile()
        {
            // Диалог выбора файла
            var dialog = new Microsoft.Win32.OpenFileDialog
            {
                Filter = "Все файлы (*.*)|*.*|Изображения (*.jpg;*.png;*.gif)|*.jpg;*.png;*.gif|Видео (*.mp4;*.avi)|*.mp4;*.avi",
                Multiselect = false
            };
            
            if (dialog.ShowDialog() == true)
            {
                // Загрузка файла
                _ = UploadFileAsync(dialog.FileName);
            }
        }
        
        private async Task UploadFileAsync(string filePath)
        {
            try
            {
                var response = await _apiService.UploadFileAsync(filePath);
                if (response.Success)
                {
                    // Отправка сообщения с файлом
                    await _webSocketClient.SendAsync(new WebSocketMessage
                    {
                        Type = "message",
                        ChatId = CurrentChat.Id,
                        Message = new Message
                        {
                            ChatId = CurrentChat.Id,
                            Content = "",
                            Type = GetFileType(filePath),
                            FileUrl = response.Data.Url,
                            FileName = System.IO.Path.GetFileName(filePath),
                            FileSize = new System.IO.FileInfo(filePath).Length,
                            Timestamp = DateTime.UtcNow
                        }
                    });
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Upload file error: {ex.Message}");
            }
        }
        
        private string GetFileType(string filePath)
        {
            var extension = System.IO.Path.GetExtension(filePath).ToLower();
            return extension switch
            {
                ".jpg" or ".jpeg" or ".png" or ".gif" => "image",
                ".mp4" or ".avi" or ".mov" => "video",
                ".mp3" or ".wav" => "audio",
                _ => "file"
            };
        }
        
        private void ShowEmoji()
        {
            // Показ эмодзи пикера
            var emojiWindow = new EmojiPickerWindow();
            emojiWindow.EmojiSelected += (sender, emoji) =>
            {
                MessageText += emoji;
            };
            emojiWindow.ShowDialog();
        }
        
        private void Logout()
        {
            // Очистка токена
            Properties.Settings.Default.AuthToken = string.Empty;
            Properties.Settings.Default.Save();
            
            // Закрытие приложения
            System.Windows.Application.Current.Shutdown();
        }
        
        private void ShowLoginWindow()
        {
            var loginWindow = new LoginWindow();
            loginWindow.LoginSuccess += async (sender, token) =>
            {
                Properties.Settings.Default.AuthToken = token;
                Properties.Settings.Default.Save();
                
                loginWindow.Close();
                await InitializeAsync();
            };
            loginWindow.ShowDialog();
        }
        
        private void OnWebSocketMessage(object sender, WebSocketMessage message)
        {
            System.Windows.Application.Current.Dispatcher.Invoke(() =>
            {
                switch (message.Type)
                {
                    case "new_message":
                        if (message.ChatId == CurrentChat?.Id)
                        {
                            message.Message.IsOwnMessage = message.Message.SenderId == CurrentUser?.Id;
                            Messages.Add(message.Message);
                        }
                        break;
                        
                    case "typing":
                        if (message.Typing.ChatId == CurrentChat?.Id && 
                            message.Typing.UserId != CurrentUser?.Id)
                        {
                            TypingIndicator = message.Typing.IsTyping 
                                ? "Собеседник печатает..." 
                                : string.Empty;
                        }
                        break;
                        
                    case "incoming_call":
                        // Входящий звонок
                        ShowIncomingCallDialog(message.Call);
                        break;
                }
            });
        }
        
        private void OnWebSocketConnected(object sender, EventArgs e)
        {
            Console.WriteLine("WebSocket connected");
        }
        
        private void OnWebSocketClosed(object sender, string reason)
        {
            Console.WriteLine($"WebSocket closed: {reason}");
        }
        
        private void ShowIncomingCallDialog(CallEvent callEvent)
        {
            var result = System.Windows.MessageBox.Show(
                $"Входящий {callEvent.Type} звонок. Принять?",
                "Входящий звонок",
                System.Windows.MessageBoxButton.YesNo,
                System.Windows.MessageBoxImage.Question);
            
            if (result == System.Windows.MessageBoxResult.Yes)
            {
                IsInCall = true;
                // TODO: Принятие звонка
            }
        }
    }
}