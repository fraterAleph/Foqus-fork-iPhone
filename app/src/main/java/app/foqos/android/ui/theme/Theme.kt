package app.foqos.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val FoqosYellow = Color(0xFFF5C542)
val FoqosInk = Color(0xFF0E1116)
val FoqosSurface = Color(0xFF161B22)
val FoqosSurfaceHigh = Color(0xFF1E242D)

private val DarkColors = darkColorScheme(
    primary = FoqosYellow,
    onPrimary = FoqosInk,
    primaryContainer = Color(0xFF3D330F),
    onPrimaryContainer = FoqosYellow,
    secondary = Color(0xFF4C8DFF),
    onSecondary = Color.White,
    background = FoqosInk,
    onBackground = Color(0xFFE9EDF2),
    surface = FoqosSurface,
    onSurface = Color(0xFFE9EDF2),
    surfaceVariant = FoqosSurfaceHigh,
    onSurfaceVariant = Color(0xFFA6B0BD),
    outline = Color(0xFF2C333D),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A6A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE9A8),
    onPrimaryContainer = Color(0xFF2B2100),
    secondary = Color(0xFF2E62C4),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF11151A),
    surface = Color.White,
    onSurface = Color(0xFF11151A),
    surfaceVariant = Color(0xFFEDF0F4),
    onSurfaceVariant = Color(0xFF5A646F),
    outline = Color(0xFFD5DAE1),
    error = Color(0xFFBA1A1A),
)

private val FoqosTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun FoqosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        val activity = LocalContext.current as? Activity
        SideEffect {
            activity?.window?.let { window ->
                window.statusBarColor = colors.background.toArgb()
                window.navigationBarColor = colors.background.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = FoqosTypography,
        content = content,
    )
}
