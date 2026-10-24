package com.fotosort

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fotosort.ui.FolderScreen
import com.fotosort.ui.FotoSortTheme
import com.fotosort.ui.SettingsScreen
import com.fotosort.ui.SwipeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(applicationContext)
        setContent {
            FotoSortTheme {
                App(prefs)
            }
        }
    }
}

private sealed interface Screen {
    data object Folders : Screen
    data class Swipe(val folder: Folder) : Screen
    data object Settings : Screen
}

@Composable
private fun App(prefs: Prefs) {
    var screen by remember { mutableStateOf<Screen>(Screen.Folders) }
    when (val current = screen) {
        Screen.Folders -> FolderScreen(
            prefs = prefs,
            onOpen = { screen = Screen.Swipe(it) },
            onSettings = { screen = Screen.Settings },
        )
        is Screen.Swipe -> SwipeScreen(prefs, current.folder, onBack = { screen = Screen.Folders })
        Screen.Settings -> SettingsScreen(prefs, onBack = { screen = Screen.Folders })
    }
}
