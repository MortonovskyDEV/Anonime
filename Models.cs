using System;
using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace GothMessenger.Models
{
    public class User
    {
        public long Id { get; set; }
        public string Username { get; set; }
        public string Email { get; set; }
        public string AvatarUrl { get; set; }
        public string Status { get; set; }
        public DateTime? LastSeen { get; set; }
        public string PublicKey { get; set; }
        public string Theme { get; set; } = "dark";
        
        [JsonIgnore]
        public string Initials => GetInitials();
        
        [JsonIgnore]
        public string StatusColor => Status == "online" ? "#10B981" : "#64748B";
        
        private string GetInitials()
        {
            if (string.IsNullOrEmpty(Username))
                return "??";
            
            var parts = Username.Split(' ', StringSplitOptions.RemoveEmptyEntries);
            if (parts.Length >= 2)
                return $"{parts[0][0]}{parts[1][0]}".ToUpper();
            
            return Username.Length >= 2 
                ? Username.Substring(0, 2).ToUpper() 
                : Username.ToUpper();
        }
    }
    
    public class Chat
    {
        public long Id { get; set; }
        public string Type { get; set; } // "private", "group", "channel"
        public string Name { get; set; }
        public string AvatarUrl { get; set; }
        public long CreatedBy { get; set; }
        public Message LastMessage { get; set; }
        public int UnreadCount { get; set; }
        public DateTime UpdatedAt { get; set; }
        public List<User> Participants { get; set; } = new List<User>();
        
        [JsonIgnore]
        public bool IsSelected { get; set; }
        
        [JsonIgnore]
        public string LastMessageTime => FormatTime(UpdatedAt);
        
        private string FormatTime(DateTime date)
        {
            var now = DateTime.Now;
            var diff = now - date;
            
            if (diff.TotalMinutes < 1)
                return "только что";
            if (diff.TotalHours < 1)
                return $"{(int)diff.TotalMinutes} мин";
            if (diff.TotalDays < 1)
                return date.ToString("HH:mm");
            if (diff.TotalDays < 7)
                return date.ToString("ddd HH:mm");
            
            return date.ToString("dd.MM.yy");
        }
    }
    
    public class Message
    {
        public long Id { get; set; }
        public long ChatId { get; set; }
        public long SenderId { get; set; }
        public string SenderName { get; set; }
        public string SenderAvatar { get; set; }
        public string Content { get; set; }
        public string Type { get; set; } = "text";
        public string FileUrl { get; set; }
        public string FileName { get; set; }
        public long? FileSize { get; set; }
        public Message RepliedTo { get; set; }
        public Dictionary<string, List<long>> Reactions { get; set; } = new Dictionary<string, List<long>>();
        public List<long> ReadBy { get; set; } = new List<long>();
        public bool Encrypted { get; set; } = true;
        public string IV { get; set; }
        public DateTime Timestamp { get; set; }
        public bool Edited { get; set; }
        
        [JsonIgnore]
        public bool IsOwnMessage { get; set; }
        
        [JsonIgnore]
        public string TimeDisplay => Timestamp.ToString("HH:mm");
        
        [JsonIgnore]
        public string FormattedSize => FormatFileSize(FileSize ?? 0);
        
        private string FormatFileSize(long bytes)
        {
            string[] sizes = { "B", "KB", "MB", "GB", "TB" };
            int order = 0;
            double len = bytes;
            
            while (len >= 1024 && order < sizes.Length - 1)
            {
                order++;
                len = len / 1024;
            }
            
            return $"{len:0.##} {sizes[order]}";
        }
    }
    
    public class ApiResponse<T>
    {
        public bool Success { get; set; }
        public T Data { get; set; }
        public string Error { get; set; }
    }
    
    public class LoginRequest
    {
        public string Username { get; set; }
        public string Password { get; set; }
    }
    
    public class LoginResponse
    {
        public string Token { get; set; }
        public User User { get; set; }
        public DateTime ExpiresAt { get; set; }
    }
    
    public class WebSocketMessage
    {
        public string Type { get; set; }
        public long? ChatId { get; set; }
        public Message Message { get; set; }
        public TypingEvent Typing { get; set; }
        public CallEvent Call { get; set; }
    }
    
    public class TypingEvent
    {
        public long ChatId { get; set; }
        public long UserId { get; set; }
        public bool IsTyping { get; set; }
    }
    
    public class CallEvent
    {
        public string CallId { get; set; }
        public long ChatId { get; set; }
        public long InitiatorId { get; set; }
        public string Type { get; set; }
        public DateTime Timestamp { get; set; }
    }
}