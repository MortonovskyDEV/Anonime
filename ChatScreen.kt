package com.goth.messenger.ui.screens.chat

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.goth.messenger.data.models.Message
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: Long?,
    onBackClick: () -> Unit,
    onVideoCallClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var messages by remember { mutableStateOf(emptyList<Message>()) }
    var inputText by remember { mutableStateOf("") }
    val configuration = LocalConfiguration.current
    
    LaunchedEffect(chatId) {
        // Загрузка сообщений
    }
    
    Scaffold(
        topBar = {
            ChatTopBar(
                chatName = "Имя чата",
                isOnline = true,
                onBackClick = onBackClick,
                onVideoCallClick = onVideoCallClick,
                onInfoClick = { /* Информация о чате */ }
            )
        },
        bottomBar = {
            MessageInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSendClick = {
                    if (inputText.isNotBlank()) {
                        // Отправка сообщения
                        inputText = ""
                    }
                },
                onAttachClick = { /* Прикрепление файла */ }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Список сообщений
            MessagesList(
                messages = messages,
                listState = listState,
                modifier = Modifier.fillMaxSize()
            )
            
            // Кнопка прокрутки вниз
            ScrollToBottomButton(
                listState = listState,
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                }
            )
        }
    }
}

@Composable
fun ChatTopBar(
    chatName: String,
    isOnline: Boolean,
    onBackClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = "avatar_url",
                    contentDescription = chatName,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = chatName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    
                    Text(
                        text = if (isOnline) "online" else "offline",
                        fontSize = 12.sp,
                        color = if (isOnline) MaterialTheme.colorScheme.success 
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onVideoCallClick) {
                Icon(Icons.Default.VideoCall, contentDescription = "Video call")
            }
            IconButton(onClick = onInfoClick) {
                Icon(Icons.Default.Info, contentDescription = "Chat info")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        )
    )
}

@Composable
fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Кнопка прикрепления
            IconButton(onClick = onAttachClick) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach file"
                )
            }
            
            // Поле ввода
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 120.dp),
                placeholder = {
                    Text("Введите сообщение...")
                },
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                trailingIcon = {
                    Row {
                        // Эмодзи
                        IconButton(onClick = { /* Эмодзи пикер */ }) {
                            Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji")
                        }
                        
                        // Голосовое сообщение
                        IconButton(onClick = { /* Запись аудио */ }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice message")
                        }
                    }
                },
                singleLine = false
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Кнопка отправки
            FloatingActionButton(
                onClick = onSendClick,
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                enabled = text.isNotBlank()
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send message",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun MessagesList(
    messages: List<Message>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(messages.reversed()) { message ->
            MessageBubble(
                message = message,
                isOwnMessage = message.senderId == 1 // Текущий пользователь
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isOwnMessage: Boolean
) {
    val bubbleColor = if (isOwnMessage) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    val textColor = if (isOwnMessage) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 8.dp,
                vertical = 2.dp
            ),
        contentAlignment = if (isOwnMessage) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Row(
            horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (!isOwnMessage) {
                // Аватар отправителя
                AsyncImage(
                    model = message.senderAvatar,
                    contentDescription = message.senderName,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Column(
                horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start
            ) {
                // Имя отправителя (только для групповых чатов)
                if (!isOwnMessage && message.chatId > 0) { // Group chat
                    Text(
                        text = message.senderName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                
                // Пузырь сообщения
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isOwnMessage) 16.dp else 4.dp,
                        bottomEnd = if (isOwnMessage) 4.dp else 16.dp
                    ),
                    color = bubbleColor,
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { /* Меню сообщения */ },
                                onTap = { /* Быстрый ответ */ }
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        // Контент сообщения
                        when (message.type) {
                            "text" -> {
                                Text(
                                    text = message.content,
                                    color = textColor,
                                    fontSize = 14.sp
                                )
                            }
                            "image" -> {
                                // Изображение
                                AsyncImage(
                                    model = message.fileUrl,
                                    contentDescription = "Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            // Другие типы сообщений
                        }
                        
                        // Время и статус
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatMessageTime(message.timestamp),
                                fontSize = 10.sp,
                                color = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            
                            if (isOwnMessage) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Read",
                                    modifier = Modifier.size(12.dp),
                                    tint = if (message.readBy.isNotEmpty()) 
                                        MaterialTheme.colorScheme.tertiary 
                                    else textColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                
                // Реакции
                if (message.reactions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        message.reactions.forEach { (emoji, users) ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(
                                    text = "$emoji ${users.size}",
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScrollToBottomButton(
    listState: LazyListState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 5
        }
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.BottomEnd),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.ArrowDownward, contentDescription = "Scroll to bottom")
        }
    }
}

fun formatMessageTime(date: java.util.Date): String {
    // Форматирование времени сообщения
    return "12:00"
}