package app.aesthory.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aesthory.R

private val blue = Color(0xFF2845CE)
private val daylight = lightColorScheme(
    primary = blue, onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE4FF), onPrimaryContainer = Color(0xFF101D58),
    secondary = Color(0xFF9B351D), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDACE), onSecondaryContainer = Color(0xFF431B0D),
    tertiary = Color(0xFF25633D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4F2D9), onTertiaryContainer = Color(0xFF143B23),
    background = Color(0xFFF4F5FB), onBackground = Color(0xFF161D34),
    surface = Color.White, onSurface = Color(0xFF161D34),
    surfaceVariant = Color(0xFFE2E5F1), onSurfaceVariant = Color(0xFF42495F),
    surfaceContainer = Color(0xFFECEEF8), surfaceContainerLow = Color(0xFFF0F2FA),
    surfaceContainerLowest = Color.White, surfaceContainerHigh = Color(0xFFE5E8F3),
    surfaceContainerHighest = Color(0xFFDEE2EF), outline = Color(0xFF727990),
    outlineVariant = Color(0xFFC3C8DB), surfaceTint = blue,
)
private val night = darkColorScheme(
    primary = Color(0xFFBAC7FF), onPrimary = Color(0xFF112571),
    primaryContainer = Color(0xFF263E9D), onPrimaryContainer = Color(0xFFE3E8FF),
    secondary = Color(0xFFFFB69D), onSecondary = Color(0xFF581F0D),
    secondaryContainer = Color(0xFF70301C), onSecondaryContainer = Color(0xFFFFDBCD),
    tertiary = Color(0xFF9BD9AE), onTertiary = Color(0xFF06391D),
    tertiaryContainer = Color(0xFF224D32), onTertiaryContainer = Color(0xFFD1F4DB),
    background = Color(0xFF10162A), onBackground = Color(0xFFE9EDFF),
    surface = Color(0xFF192137), onSurface = Color(0xFFE9EDFF),
    surfaceVariant = Color(0xFF343E58), onSurfaceVariant = Color(0xFFC3CBE3),
    surfaceContainer = Color(0xFF202940), surfaceContainerLow = Color(0xFF192137),
    surfaceContainerLowest = Color(0xFF0C1122), surfaceContainerHigh = Color(0xFF29334A),
    surfaceContainerHighest = Color(0xFF333D54), outline = Color(0xFF919BB5),
    outlineVariant = Color(0xFF424D67), surfaceTint = Color(0xFFBAC7FF),
)
private val display = FontFamily(Font(R.font.barlow_condensed_bold, FontWeight.Bold))
private val defaults = Typography()
private val type = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = display, fontWeight = FontWeight.Bold),
    displayMedium = defaults.displayMedium.copy(fontFamily = display, fontWeight = FontWeight.Bold),
    displaySmall = defaults.displaySmall.copy(fontFamily = display, fontWeight = FontWeight.Bold),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = display, fontWeight = FontWeight.Bold),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = display, fontWeight = FontWeight.Bold),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = display, fontWeight = FontWeight.Bold),
)
@Composable
fun AesthoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) night else daylight, typography = type,
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(24.dp)), content = content)
}
