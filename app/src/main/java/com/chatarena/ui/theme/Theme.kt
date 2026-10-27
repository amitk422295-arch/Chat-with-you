package com.chatarena.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

// Tere Global CSS se liye gaye exact colors
val BgColor = Color(0xFFE0E5EC)
val CardColor = Color(0xFFE0E5EC)
val TextColor = Color(0xFF2D3748)
val SubTextColor = Color(0xFF7E7E92)
val PrimaryBlue = Color(0xFF007AFF)
val DangerRed = Color(0xFFFF3B5C)

// Colors ko Material Theme se connect karna
private val ChatArenaColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    background = BgColor,
    surface = CardColor,
    onBackground = TextColor,
    onSurface = TextColor,
    error = DangerRed
)

// Default Typography (Font bad me add karenge)
private val ChatArenaTypography = Typography(
    bodyLarge = TextStyle(
        color = TextColor,
        fontSize = 15.sp
    ),
    titleMedium = TextStyle(
        color = TextColor,
        fontSize = 18.sp
    )
)

@Composable
fun ChatArenaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChatArenaColorScheme,
        typography = ChatArenaTypography,
        content = content
    )
}
