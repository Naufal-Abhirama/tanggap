package id.tanggap.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Green300,           // tombol, accent
    secondary = Green300,
    tertiary = OrangeWarning,
    background = Green900,        // background utama
    surface = Green800,           // topbar, bottombar
    surfaceVariant = Green700,    // bubble AI
    onPrimary = Green900,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextPrimary,
)

@Composable
fun Gemma4Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
