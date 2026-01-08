package com.goth.messenger.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.goth.messenger.ui.screens.auth.LoginScreen
import com.goth.messenger.ui.screens.auth.RegisterScreen
import com.goth.messenger.ui.screens.chat.ChatScreen
import com.goth.messenger.ui.screens.chats.ChatsScreen
import com.goth.messenger.ui.screens.profile.ProfileScreen
import com.goth.messenger.ui.screens.settings.SettingsScreen
import com.goth.messenger.ui.screens.video.VideoCallScreen

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavigationHost(navController = navController)
    }
}

@Composable
fun NavigationHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = if (isAuthenticated()) "chats" else "login"
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { navController.navigate("chats") },
                onRegisterClick = { navController.navigate("register") }
            )
        }
        
        composable("register") {
            RegisterScreen(
                onRegisterSuccess = { navController.navigate("chats") },
                onLoginClick = { navController.popBackStack() }
            )
        }
        
        composable("chats") {
            ChatsScreen(
                onChatSelected = { chatId ->
                    navController.navigate("chat/$chatId")
                },
                onProfileClick = { navController.navigate("profile") },
                onSettingsClick = { navController.navigate("settings") }
            )
        }
        
        composable("chat/{chatId}") { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")?.toLongOrNull()
            ChatScreen(
                chatId = chatId,
                onBackClick = { navController.popBackStack() },
                onVideoCallClick = { navController.navigate("video/$chatId") }
            )
        }
        
        composable("profile") {
            ProfileScreen(onBackClick = { navController.popBackStack() })
        }
        
        composable("settings") {
            SettingsScreen(onBackClick = { navController.popBackStack() })
        }
        
        composable("video/{chatId}") { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")?.toLongOrNull()
            VideoCallScreen(
                chatId = chatId,
                onEndCall = { navController.popBackStack() }
            )
        }
    }
}

fun isAuthenticated(): Boolean {
    // Проверка токена в SharedPreferences
    return false
}