package com.goth.messenger.ui.screens.video

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import org.webrtc.*

@Composable
fun VideoCallScreen(
    chatId: Long?,
    onEndCall: () -> Unit
) {
    val viewModel: VideoCallViewModel = viewModel()
    
    LaunchedEffect(chatId) {
        chatId?.let { viewModel.startCall(it) }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Удалённое видео
        if (viewModel.remoteVideoTrack != null) {
            AndroidView(
                factory = { context ->
                    SurfaceViewRenderer(context).apply {
                        init(viewModel.eglBase.eglBaseContext, null)
                        setMirror(true)
                        setEnableHardwareScaler(true)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        viewModel.remoteVideoTrack?.addSink(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Заставка
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = "user_avatar",
                    contentDescription = "Calling",
                    modifier = Modifier.size(120.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }
        
        // Локальное видео (пип)
        if (viewModel.localVideoTrack != null) {
            AndroidView(
                factory = { context ->
                    SurfaceViewRenderer(context).apply {
                        init(viewModel.eglBase.eglBaseContext, null)
                        setMirror(true)
                        setEnableHardwareScaler(true)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        viewModel.localVideoTrack?.addSink(this)
                    }
                },
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }
        
        // Панель управления звонком
        CallControls(
            isMuted = viewModel.isMuted,
            isVideoEnabled = viewModel.isVideoEnabled,
            isSpeakerEnabled = viewModel.isSpeakerEnabled,
            onToggleMute = { viewModel.toggleMute() },
            onToggleVideo = { viewModel.toggleVideo() },
            onToggleSpeaker = { viewModel.toggleSpeaker() },
            onEndCall = {
                viewModel.endCall()
                onEndCall()
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        
        // Информация о звонке
        CallInfo(
            callerName = "Имя собеседника",
            callDuration = viewModel.callDuration,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        )
    }
}

@Composable
fun CallControls(
    isMuted: Boolean,
    isVideoEnabled: Boolean,
    isSpeakerEnabled: Boolean,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier.padding(bottom = 48.dp)
    ) {
        // Кнопка микрофона
        CallControlButton(
            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            onClick = onToggleMute,
            backgroundColor = Color.White.copy(alpha = 0.3f)
        )
        
        // Кнопка камеры
        CallControlButton(
            icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
            onClick = onToggleVideo,
            backgroundColor = Color.White.copy(alpha = 0.3f)
        )
        
        // Кнопка динамика
        CallControlButton(
            icon = if (isSpeakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
            onClick = onToggleSpeaker,
            backgroundColor = Color.White.copy(alpha = 0.3f)
        )
        
        // Кнопка завершения
        CallControlButton(
            icon = Icons.Default.CallEnd,
            onClick = onEndCall,
            backgroundColor = Color.Red,
            contentColor = Color.White
        )
    }
}

@Composable
fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    backgroundColor: Color,
    contentColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        containerColor = backgroundColor,
        contentColor = contentColor
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun CallInfo(
    callerName: String,
    callDuration: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = callerName,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = callDuration,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp
            )
        }
    }
}