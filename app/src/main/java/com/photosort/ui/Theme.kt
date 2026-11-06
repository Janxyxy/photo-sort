package com.photosort.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.photosort.ThemeMode

val KeepColor = Color(0xFF2E7D32)
val TrashColor = Color(0xFFC62828)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun PhotoSortTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        dark && dynamic -> dynamicDarkColorScheme(context)
        dark -> darkColorScheme()
        dynamic -> dynamicLightColorScheme(context)
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
