package com.chencohensolutions.knesset.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.chencohensolutions.knesset.core.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF1F4FB5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDAE2FF),
    onPrimaryContainer = Color(0xFF001848),
    secondary = Color(0xFF585E71),
    secondaryContainer = Color(0xFFDDE2F9),
    tertiary = Color(0xFF735572),
    background = Color(0xFFFDFBFF),
    surface = Color(0xFFFDFBFF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB2C5FF),
    onPrimary = Color(0xFF002B74),
    primaryContainer = Color(0xFF003EA2),
    onPrimaryContainer = Color(0xFFDAE2FF),
    secondary = Color(0xFFC0C6DC),
    secondaryContainer = Color(0xFF404659),
    tertiary = Color(0xFFE1BBDC),
    background = Color(0xFF1A1B21),
    surface = Color(0xFF1A1B21),
)

/** Colors for the vote bar and status chips, chosen to read in both themes. */
object Semantic {
    val positive = Color(0xFF2E7D32)
    val negative = Color(0xFFC62828)
    val neutral = Color(0xFF9E9E9E)
    val progress = Color(0xFF1565C0)
}

@Composable
fun KnessetTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
