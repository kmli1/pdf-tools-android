package com.pdfatolyesi.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pdfatolyesi.app.data.ThemeMode

private val Light = lightColorScheme(primary = Color(0xFF006B5E), onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1DF), onPrimaryContainer = Color(0xFF002019),
    background = Color(0xFFF6F8F5), surface = Color(0xFFF6F8F5), surfaceVariant = Color(0xFFDDE5DF))
private val Dark = darkColorScheme(primary = Color(0xFF8BD5BF), onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005142), onPrimaryContainer = Color(0xFFB4F1DF),
    background = Color(0xFF101512), surface = Color(0xFF101512))
@Composable fun PdfTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
