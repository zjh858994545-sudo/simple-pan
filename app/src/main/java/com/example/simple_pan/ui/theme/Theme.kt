package com.example.simple_pan.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CloudBlueDark,
    secondary = TransferTealDark,
    tertiary = ActionCoralDark,
    primaryContainer = CloudBlueDarkContainer,
    secondaryContainer = TransferTealDarkContainer,
    tertiaryContainer = ActionCoralDarkContainer,
    background = AppDarkBackground,
    surface = AppDarkSurface,
    surfaceVariant = AppDarkSurfaceVariant,
    onPrimary = Color(0xFF0A1A3A),
    onSecondary = Color(0xFF00201D),
    onTertiary = Color(0xFF3B0905),
    onPrimaryContainer = Color(0xFFE8F0FF),
    onSecondaryContainer = Color(0xFFD8F7F2),
    onTertiaryContainer = Color(0xFFFFDAD4),
    onBackground = Color(0xFFE4E7EB),
    onSurface = Color(0xFFE4E7EB),
    onSurfaceVariant = Color(0xFFC7CED8)
)

private val LightColorScheme = lightColorScheme(
    primary = CloudBlue,
    secondary = TransferTeal,
    tertiary = ActionCoral,
    primaryContainer = CloudBlueContainer,
    secondaryContainer = TransferTealContainer,
    tertiaryContainer = ActionCoralContainer,
    background = AppBackground,
    surface = AppSurface,
    surfaceVariant = AppSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onPrimaryContainer = Color(0xFF0F2A5F),
    onSecondaryContainer = Color(0xFF073B36),
    onTertiaryContainer = Color(0xFF64170F),
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF526070),
    outlineVariant = Color(0xFFD6DEEA)
)

// [设计] 为什么这样写：默认关闭系统动态取色，保证答辩和录屏时不同设备上的主色一致，便于截图和文档对齐。
@Composable
fun SimplepanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
