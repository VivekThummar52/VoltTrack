package com.codecraft.volttrack.ui.theme

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
import com.codecraft.volttrack.data.preferences.AppThemeColor

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

// Blue Scheme
private val BlueLight = lightColorScheme(primary = Color(0xFF0061A4), onPrimary = Color.White, primaryContainer = Color(0xFFD1E4FF))
private val BlueDark = darkColorScheme(primary = Color(0xFF9ECAFF), onPrimary = Color(0xFF003258), primaryContainer = Color(0xFF00497D))

// Green Scheme
private val GreenLight = lightColorScheme(primary = Color(0xFF006D3B), onPrimary = Color.White, primaryContainer = Color(0xFF99F6B5))
private val GreenDark = darkColorScheme(primary = Color(0xFF7ED99B), onPrimary = Color(0xFF00391C), primaryContainer = Color(0xFF00522E))

// Orange Scheme
private val OrangeLight = lightColorScheme(primary = Color(0xFF8B5000), onPrimary = Color.White, primaryContainer = Color(0xFFFFDCC1))
private val OrangeDark = darkColorScheme(primary = Color(0xFFFFB77C), onPrimary = Color(0xFF4A2800), primaryContainer = Color(0xFF6A3B00))

// Rose Scheme
private val RoseLight = lightColorScheme(primary = Color(0xFF984061), onPrimary = Color.White, primaryContainer = Color(0xFFFFD9E2))
private val RoseDark = darkColorScheme(primary = Color(0xFFFFB1C8), onPrimary = Color(0xFF5E1133), primaryContainer = Color(0xFF7B2949))

@Composable
fun VoltTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    themeColor: AppThemeColor = AppThemeColor.DYNAMIC,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        themeColor == AppThemeColor.DYNAMIC && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> {
            when (themeColor) {
                AppThemeColor.PURPLE, AppThemeColor.DYNAMIC -> if (darkTheme) DarkColorScheme else LightColorScheme
                AppThemeColor.BLUE -> if (darkTheme) BlueDark else BlueLight
                AppThemeColor.GREEN -> if (darkTheme) GreenDark else GreenLight
                AppThemeColor.ORANGE -> if (darkTheme) OrangeDark else OrangeLight
                AppThemeColor.ROSE -> if (darkTheme) RoseDark else RoseLight
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}