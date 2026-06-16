package com.mimochat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mimochat.ui.screens.*

object Routes {
    const val SESSION_LIST = "sessions"
    const val CHAT = "chat/{sessionId}/{sessionTitle}"
    const val SETTINGS = "settings"
    const val CHARACTER = "character"
    const val VOICE_CALL = "voice_call/{sessionId}"
    const val VIDEO_CALL = "video_call/{sessionId}"

    fun chat(sessionId: String, title: String) = "chat/$sessionId/$title"
    fun voiceCall(sessionId: String) = "voice_call/$sessionId"
    fun videoCall(sessionId: String) = "video_call/$sessionId"
}

@Composable
fun MiMoNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.SESSION_LIST) {
        composable(Routes.SESSION_LIST) {
            SessionListScreen(navController)
        }
        composable(
            Routes.CHAT,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("sessionTitle") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val title = backStackEntry.arguments?.getString("sessionTitle") ?: ""
            ChatScreen(navController, sessionId, title)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(navController)
        }
        composable(Routes.CHARACTER) {
            CharacterScreen(navController)
        }
        composable(
            Routes.VOICE_CALL,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            VoiceCallScreen(navController, sessionId)
        }
        composable(
            Routes.VIDEO_CALL,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            VideoCallScreen(navController, sessionId)
        }
    }
}
