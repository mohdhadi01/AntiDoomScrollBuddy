package com.doombuddy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * DoomBuddy's visual language: a midnight canvas, bright citrus action color and
 * soft candy accents. Surfaces stay quiet so the character remains the hero.
 */
val Ink0 = Color(0xFF090A10)
val Ink1 = Color(0xFF11131C)
val Ink2 = Color(0xFF191C28)
val Line = Color(0xFF2A2E40)
val Fog = Color(0xFF969DB2)
val Snow = Color(0xFFF7F7FB)

val Lime = Color(0xFFD4F95A)
val LimeDeep = Color(0xFFA9D62B)
val Coral = Color(0xFFFF8298)
val Sun = Color(0xFFFFC857)
val Sky = Color(0xFF80D8FF)
val Violet = Color(0xFFC6B6FF)
val Lavender = Color(0xFF25233A)

val Paper = Color(0xFFF8F7F1)
val PaperCard = Color(0xFFFFFFFF)
val OliveDeep = Color(0xFF465B0E)

private val DarkColors = darkColorScheme(
    primary = Lime,
    onPrimary = Color(0xFF182000),
    primaryContainer = Color(0xFF344616),
    onPrimaryContainer = Lime,
    secondary = Violet,
    onSecondary = Color(0xFF241A3B),
    secondaryContainer = Lavender,
    onSecondaryContainer = Color(0xFFE8E0FF),
    tertiary = Coral,
    onTertiary = Color(0xFF3B0B16),
    tertiaryContainer = Color(0xFF4A202C),
    onTertiaryContainer = Color(0xFFFFD9E0),
    background = Ink0,
    onBackground = Snow,
    surface = Ink1,
    onSurface = Snow,
    surfaceVariant = Ink2,
    onSurfaceVariant = Fog,
    outline = Line,
    outlineVariant = Line,
    error = Color(0xFFFF8A80),
)

private val LightColors = lightColorScheme(
    primary = OliveDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4F5B5),
    onPrimaryContainer = Color(0xFF243307),
    secondary = Color(0xFF6D5FA8),
    onSecondary = Color.White,
    tertiary = Color(0xFFB3324A),
    background = Paper,
    onBackground = Color(0xFF171A20),
    surface = PaperCard,
    onSurface = Color(0xFF171A20),
    surfaceVariant = Color(0xFFEDECE4),
    onSurfaceVariant = Color(0xFF59616E),
    outline = Color(0xFFD9D8CE),
)

val BuddyTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Black, fontSize = 40.sp, letterSpacing = (-1.5).sp, lineHeight = 43.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 33.sp, letterSpacing = (-1.1).sp, lineHeight = 37.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, letterSpacing = (-0.6).sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.5.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.35.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 1.2.sp),
)

private val BuddyShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun DoomBuddyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = BuddyTypography,
        shapes = BuddyShapes,
        content = content,
    )
}
