package com.uallsi.medaboutyou.ui.theme

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

// Brand palette (teal/green, echoing the desktop accent) — used when the device
// does not support Material You dynamic colour.
private val BrandPrimary = Color(0xFF1A6F5B)
private val BrandSecondary = Color(0xFF4C9A86)
private val BrandTertiary = Color(0xFF7A5BA8)

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    secondary = BrandSecondary,
    tertiary = BrandTertiary,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD8BC),
    secondary = Color(0xFF9FD3C4),
    tertiary = Color(0xFFC9B3E8),
)

/** Semantic status colours shared across badges, calendar and the dashboard. */
object MedColors {
    val success = Color(0xFF2E7D52)
    val successContainer = Color(0xFFB7E7CC)
    val error = Color(0xFFB3261E)
    val errorContainer = Color(0xFFF6C1BD)
    val warning = Color(0xFFB26A00)
    val warningContainer = Color(0xFFFADFB0)
    val future = Color(0xFF3B6CB3)
    val futureContainer = Color(0xFFC6D9F4)
    val shortage = Color(0xFF6C4AB6)
    val shortageContainer = Color(0xFFD8C9F2)
    val neutralContainer = Color(0xFFE3E5E5)
}

@Composable
fun MedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MedTypography,
        content = content,
    )
}
