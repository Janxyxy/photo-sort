package com.fotosort.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val KeepColor = Color(0xFF2E7D32)
val TrashColor = Color(0xFFC62828)

@Composable
fun FotoSortTheme(content: @Composable () -> Unit) {
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        darkColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
