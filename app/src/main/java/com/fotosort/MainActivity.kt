package com.fotosort

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.fotosort.ui.CategoriesScreen
import com.fotosort.ui.CategoriesState
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

private enum class Tab(val label: String, val icon: ImageVector) {
    Folders("Folders", Icons.AutoMirrored.Filled.List),
    Categories("Categories", Icons.Filled.Search),
}

private sealed interface Screen {
    data object Home : Screen
    data class Swipe(val folder: Folder) : Screen
    data object Settings : Screen
}

@Composable
private fun App(prefs: Prefs) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    // kept outside Home so coming back from a folder or settings lands on the same tab
    var tab by remember { mutableStateOf(Tab.Folders) }
    val categories = remember { CategoriesState() }
    when (val current = screen) {
        Screen.Home -> {
            val bottomBar = @Composable { TabBar(tab, onSelect = { tab = it }) }
            val open = { folder: Folder -> screen = Screen.Swipe(folder) }
            val settings = { screen = Screen.Settings }
            when (tab) {
                Tab.Folders -> FolderScreen(prefs, onOpen = open, onSettings = settings, bottomBar = bottomBar)
                Tab.Categories -> CategoriesScreen(prefs, categories, onOpen = open, onSettings = settings, bottomBar = bottomBar)
            }
        }
        is Screen.Swipe -> SwipeScreen(prefs, current.folder, onBack = { screen = Screen.Home })
        Screen.Settings -> SettingsScreen(prefs, onBack = { screen = Screen.Home })
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}
