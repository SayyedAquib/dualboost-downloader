package com.dualboost.downloader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF64D2FF),
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004D68),
    onPrimaryContainer = Color(0xFFBEE9FF),

    secondary = Color(0xFFB0C6FF),
    onSecondary = Color(0xFF1B2F60),
    secondaryContainer = Color(0xFF334678),
    onSecondaryContainer = Color(0xFFD9E2FF),

    tertiary = Color(0xFFD0BCFF),
    onTertiary = Color(0xFF381E72),
    tertiaryContainer = Color(0xFF4F378B),
    onTertiaryContainer = Color(0xFFEADDFF),

    background = Color(0xFF111416),
    onBackground = Color(0xFFE1E2E5),
    surface = Color(0xFF111416),
    onSurface = Color(0xFFE1E2E5),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC0C8CD),
    surfaceContainerLowest = Color(0xFF0C0E10),
    surfaceContainerLow = Color(0xFF191C1E),
    surfaceContainer = Color(0xFF1E2124),
    surfaceContainerHigh = Color(0xFF282B2E),
    surfaceContainerHighest = Color(0xFF33363A),
    surfaceBright = Color(0xFF363A3D),
    surfaceDim = Color(0xFF0E1113),
    outline = Color(0xFF8A9297),
    outlineVariant = Color(0xFF41484D),

    primaryFixed = Color(0xFFBEE9FF),
    primaryFixedDim = Color(0xFF64D2FF),
    onPrimaryFixed = Color(0xFF001F2C),
    onPrimaryFixedVariant = Color(0xFF004D68),
    secondaryFixed = Color(0xFFD9E2FF),
    secondaryFixedDim = Color(0xFFB0C6FF),
    onSecondaryFixed = Color(0xFF041A49),
    onSecondaryFixedVariant = Color(0xFF334678),
    tertiaryFixed = Color(0xFFEADDFF),
    tertiaryFixedDim = Color(0xFFD0BCFF),
    onTertiaryFixed = Color(0xFF22005D),
    onTertiaryFixedVariant = Color(0xFF4F378B),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00668B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBEE9FF),
    onPrimaryContainer = Color(0xFF001F2C),

    secondary = Color(0xFF4B5E91),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E2FF),
    onSecondaryContainer = Color(0xFF041A49),

    tertiary = Color(0xFF6750A4),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEADDFF),
    onTertiaryContainer = Color(0xFF22005D),

    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F8),
    surfaceContainer = Color(0xFFEBEEF2),
    surfaceContainerHigh = Color(0xFFE5E8EC),
    surfaceContainerHighest = Color(0xFFDFE2E7),
    surfaceBright = Color(0xFFF7F9FC),
    surfaceDim = Color(0xFFD9DCE0),
    outline = Color(0xFF71787D),
    outlineVariant = Color(0xFFC0C8CD),

    primaryFixed = Color(0xFFBEE9FF),
    primaryFixedDim = Color(0xFF64D2FF),
    onPrimaryFixed = Color(0xFF001F2C),
    onPrimaryFixedVariant = Color(0xFF004D68),
    secondaryFixed = Color(0xFFD9E2FF),
    secondaryFixedDim = Color(0xFFB0C6FF),
    onSecondaryFixed = Color(0xFF041A49),
    onSecondaryFixedVariant = Color(0xFF334678),
    tertiaryFixed = Color(0xFFEADDFF),
    tertiaryFixedDim = Color(0xFFD0BCFF),
    onTertiaryFixed = Color(0xFF22005D),
    onTertiaryFixedVariant = Color(0xFF4F378B),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

internal val DualBoostShapes = Shapes(
    extraSmall = ShapeDefaults.ExtraSmall,
    small = ShapeDefaults.Small,
    medium = ShapeDefaults.Medium,
    large = ShapeDefaults.Large,
    largeIncreased = ShapeDefaults.LargeIncreased,
    extraLarge = ShapeDefaults.ExtraLarge,
    extraLargeIncreased = ShapeDefaults.ExtraLargeIncreased,
    extraExtraLarge = ShapeDefaults.ExtraExtraLarge
)

internal val DualBoostTypography = Typography(
    fontFamily = FontFamily.SansSerif
)

/**
 * Wraps an expressive shape-morphing [IconButton] with a Material 3 1.5.0 anchored [PlainTooltip].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveTooltipIconButton(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier,
            content = content
        )
    }
}

@Composable
fun DualBoostTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = DualBoostShapes,
        typography = DualBoostTypography,
        content = content
    )
}
