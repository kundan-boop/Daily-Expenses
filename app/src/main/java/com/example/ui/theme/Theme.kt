package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.utils.AppPreferencesManager
import com.example.utils.LocalAppStrings
import com.example.utils.stringsForLanguage

data class LedgerColors(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val cardElevated: Color,
    val surfaceSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val borderStrong: Color,
    val divider: Color,
    val greenPrimary: Color,
    val greenTint: Color,
    val greenBorder: Color,
    val redDebit: Color,
    val redTint: Color,
    val redBorder: Color,
    val blueAccent: Color,
    val blueTint: Color,
    val blueBorder: Color,
    val amberAccent: Color,
    val amberTint: Color,
    val amberBorder: Color,
    val indigoAccent: Color,
    val indigoTint: Color,
    val brandHeader: Color,
    val brandHeaderGradientEnd: Color
)

val LightLedgerColors = LedgerColors(
    isDark = false,
    background = Color(0xFFF8FAFC),
    card = Color(0xFFFFFFFF),
    cardElevated = Color(0xFFF1F5F9),
    surfaceSubtle = Color(0xFFF8FAFC),
    textPrimary = Color(0xFF1F2937),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF6B7280),
    border = Color(0xFFE2E8F0),
    borderStrong = Color(0xFFCBD5E1),
    divider = Color(0xFFF1F5F9),
    greenPrimary = Color(0xFF16A34A),
    greenTint = Color(0xFFDCFCE7),
    greenBorder = Color(0xFFBBF7D0),
    redDebit = Color(0xFFEF4444),
    redTint = Color(0xFFFEE2E2),
    redBorder = Color(0xFFFECACA),
    blueAccent = Color(0xFF2563EB),
    blueTint = Color(0xFFE0F2FE),
    blueBorder = Color(0xFFBFDBFE),
    amberAccent = Color(0xFFD97706),
    amberTint = Color(0xFFFEF3C7),
    amberBorder = Color(0xFFFDE68A),
    indigoAccent = Color(0xFF4F46E5),
    indigoTint = Color(0xFFEEF2FF),
    brandHeader = Color(0xFF16A34A),
    brandHeaderGradientEnd = Color(0xFF15803D)
)

val DarkLedgerColors = LedgerColors(
    isDark = true,
    background = Color(0xFF0B1120),
    card = Color(0xFF161E2E),
    cardElevated = Color(0xFF1F293B),
    surfaceSubtle = Color(0xFF131B2B),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF94A3B8),
    border = Color(0xFF2A3649),
    borderStrong = Color(0xFF475569),
    divider = Color(0xFF233044),
    greenPrimary = Color(0xFF22C55E),
    greenTint = Color(0xFF143624),
    greenBorder = Color(0xFF1E5236),
    redDebit = Color(0xFFF87171),
    redTint = Color(0xFF3B181E),
    redBorder = Color(0xFF6E232A),
    blueAccent = Color(0xFF60A5FA),
    blueTint = Color(0xFF172A46),
    blueBorder = Color(0xFF1E3A63),
    amberAccent = Color(0xFFFBBF24),
    amberTint = Color(0xFF3A2B11),
    amberBorder = Color(0xFF664815),
    indigoAccent = Color(0xFF818CF8),
    indigoTint = Color(0xFF1F2147),
    brandHeader = Color(0xFF14532D),
    brandHeaderGradientEnd = Color(0xFF0F3D24)
)

val LocalLedgerColors = staticCompositionLocalOf { LightLedgerColors }

object LedgerTheme {
    val colors: LedgerColors
        @Composable
        get() = LocalLedgerColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF143624),
    onPrimaryContainer = Color(0xFFDCFCE7),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF172A46),
    onSecondaryContainer = Color(0xFFDBEAFE),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFF87171),
    onError = Color.White,
    errorContainer = Color(0xFF3B181E),
    onErrorContainer = Color(0xFFFECACA)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryEmerald,
    onPrimary = Color.White,
    primaryContainer = GreenLight,
    onPrimaryContainer = PrimaryEmeraldDark,
    secondary = SecondaryBlue,
    onSecondary = Color.White,
    secondaryContainer = OceanBlueLight,
    onSecondaryContainer = Color(0xFF1E3A8A),
    background = SurfaceBackground,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    outline = Slate300,
    outlineVariant = Slate200,
    error = PaidAmount,
    onError = Color.White,
    errorContainer = RedLight,
    onErrorContainer = ErrorRed
)

@Composable
fun DailyExpenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val ledgerColors = if (darkTheme) DarkLedgerColors else LightLedgerColors
    val currentLanguage by AppPreferencesManager.languageFlow.collectAsState()
    val appStrings = stringsForLanguage(currentLanguage)

    CompositionLocalProvider(
        LocalLedgerColors provides ledgerColors,
        LocalAppStrings provides appStrings
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
