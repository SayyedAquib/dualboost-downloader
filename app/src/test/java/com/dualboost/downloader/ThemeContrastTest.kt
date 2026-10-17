package com.dualboost.downloader

import androidx.compose.ui.graphics.Color
import com.dualboost.downloader.ui.theme.DarkColorScheme
import com.dualboost.downloader.ui.theme.LightColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ThemeContrastTest {

    private fun calculateLuminance(color: Color): Double {
        val r = sRgbChannel(color.red.toDouble())
        val g = sRgbChannel(color.green.toDouble())
        val b = sRgbChannel(color.blue.toDouble())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun sRgbChannel(value: Double): Double {
        return if (value <= 0.04045) {
            value / 12.92
        } else {
            ((value + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun contrastRatio(c1: Color, c2: Color): Double {
        val l1 = calculateLuminance(c1)
        val l2 = calculateLuminance(c2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun testLightColorScheme_AllStandardPairingsExceedWcagAA() {
        val scheme = LightColorScheme
        // Surface vs OnSurface
        val surfaceContrast = contrastRatio(scheme.surface, scheme.onSurface)
        assertTrue("Light surface contrast should exceed 4.5:1, was $surfaceContrast", surfaceContrast >= 4.5)

        // Primary vs OnPrimary
        val primaryContrast = contrastRatio(scheme.primary, scheme.onPrimary)
        assertTrue("Light primary contrast should exceed 4.5:1, was $primaryContrast", primaryContrast >= 4.5)

        // PrimaryContainer vs OnPrimaryContainer
        val primaryContainerContrast = contrastRatio(scheme.primaryContainer, scheme.onPrimaryContainer)
        assertTrue("Light primaryContainer contrast should exceed 4.5:1, was $primaryContainerContrast", primaryContainerContrast >= 4.5)

        // SecondaryContainer vs OnSecondaryContainer
        val secondaryContainerContrast = contrastRatio(scheme.secondaryContainer, scheme.onSecondaryContainer)
        assertTrue("Light secondaryContainer contrast should exceed 4.5:1, was $secondaryContainerContrast", secondaryContainerContrast >= 4.5)

        // TertiaryContainer vs OnTertiaryContainer
        val tertiaryContainerContrast = contrastRatio(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        assertTrue("Light tertiaryContainer contrast should exceed 4.5:1, was $tertiaryContainerContrast", tertiaryContainerContrast >= 4.5)

        // ErrorContainer vs OnErrorContainer
        val errorContainerContrast = contrastRatio(scheme.errorContainer, scheme.onErrorContainer)
        assertTrue("Light errorContainer contrast should exceed 4.5:1, was $errorContainerContrast", errorContainerContrast >= 4.5)
    }

    @Test
    fun testDarkColorScheme_AllStandardPairingsExceedWcagAA() {
        val scheme = DarkColorScheme
        // Surface vs OnSurface
        val surfaceContrast = contrastRatio(scheme.surface, scheme.onSurface)
        assertTrue("Dark surface contrast should exceed 4.5:1, was $surfaceContrast", surfaceContrast >= 4.5)

        // Primary vs OnPrimary
        val primaryContrast = contrastRatio(scheme.primary, scheme.onPrimary)
        assertTrue("Dark primary contrast should exceed 4.5:1, was $primaryContrast", primaryContrast >= 4.5)

        // PrimaryContainer vs OnPrimaryContainer
        val primaryContainerContrast = contrastRatio(scheme.primaryContainer, scheme.onPrimaryContainer)
        assertTrue("Dark primaryContainer contrast should exceed 4.5:1, was $primaryContainerContrast", primaryContainerContrast >= 4.5)

        // SecondaryContainer vs OnSecondaryContainer
        val secondaryContainerContrast = contrastRatio(scheme.secondaryContainer, scheme.onSecondaryContainer)
        assertTrue("Dark secondaryContainer contrast should exceed 4.5:1, was $secondaryContainerContrast", secondaryContainerContrast >= 4.5)

        // TertiaryContainer vs OnTertiaryContainer
        val tertiaryContainerContrast = contrastRatio(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        assertTrue("Dark tertiaryContainer contrast should exceed 4.5:1, was $tertiaryContainerContrast", tertiaryContainerContrast >= 4.5)

        // ErrorContainer vs OnErrorContainer
        val errorContainerContrast = contrastRatio(scheme.errorContainer, scheme.onErrorContainer)
        assertTrue("Dark errorContainer contrast should exceed 4.5:1, was $errorContainerContrast", errorContainerContrast >= 4.5)
    }

    @Test
    fun testTranslucentBrandTintPattern_ExceedsHighContrastInBothModes() {
        // Universal translucent tint pattern: color.copy(alpha = 0.14f) against surface with solid color foreground
        // Light mode: solid primary (dark teal) on light surface tinted with 14% primary
        val lightPrimary = LightColorScheme.primary
        val lightSurface = LightColorScheme.surface
        val lightTintLuminance = calculateLuminance(lightPrimary) * 0.14 + calculateLuminance(lightSurface) * 0.86
        val lightPrimaryLuminance = calculateLuminance(lightPrimary)
        val lightRatio = (max(lightTintLuminance, lightPrimaryLuminance) + 0.05) / (min(lightTintLuminance, lightPrimaryLuminance) + 0.05)
        assertTrue("Light translucent brand tint pattern contrast should exceed 3.0:1 (icon threshold), was $lightRatio", lightRatio >= 3.0)

        // Dark mode: solid primary (light cyan) on dark surface tinted with 14% primary
        val darkPrimary = DarkColorScheme.primary
        val darkSurface = DarkColorScheme.surface
        val darkTintLuminance = calculateLuminance(darkPrimary) * 0.14 + calculateLuminance(darkSurface) * 0.86
        val darkPrimaryLuminance = calculateLuminance(darkPrimary)
        val darkRatio = (max(darkTintLuminance, darkPrimaryLuminance) + 0.05) / (min(darkTintLuminance, darkPrimaryLuminance) + 0.05)
        assertTrue("Dark translucent brand tint pattern contrast should exceed 3.0:1 (icon threshold), was $darkRatio", darkRatio >= 3.0)
    }
}

