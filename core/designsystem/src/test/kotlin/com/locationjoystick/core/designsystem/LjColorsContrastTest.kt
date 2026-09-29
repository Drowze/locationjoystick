package com.locationjoystick.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class LjColorsContrastTest {
    @Test
    fun darkOutlineVariantIsLighterThanDarkSurfaces() {
        assertTrue(channelSum(LjDarkOutlineVariant) > channelSum(LjSurface))
        assertTrue(channelSum(LjDarkOutlineVariant) > channelSum(LjBg))
    }

    @Test
    fun cardsAndSheetsAreSeparatedFromBackground() {
        listOf(LjDarkColorScheme, LjLightColorScheme).forEach { scheme ->
            assertNotEquals(scheme.background, scheme.surfaceVariant)
            assertNotEquals(scheme.background, scheme.surfaceContainerLow)
            assertNotEquals(scheme.surfaceVariant, scheme.surfaceContainerLow)
        }
    }

    @Test
    fun lightThemeTextContrast() {
        // Light theme text pairs must meet WCAG AA 4.5:1 minimum for body text
        val scheme = LjLightColorScheme
        assertTrue("onPrimaryContainer on primaryContainer", contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
        assertTrue("onSecondaryContainer on secondaryContainer", contrast(scheme.onSecondaryContainer, scheme.secondaryContainer) >= 4.5f)
        assertTrue("onTertiaryContainer on tertiaryContainer", contrast(scheme.onTertiaryContainer, scheme.tertiaryContainer) >= 4.5f)
        assertTrue("primary on surfaceVariant", contrast(scheme.primary, scheme.surfaceVariant) >= 4.5f)
        assertTrue("primary on surfaceContainerHighest", contrast(scheme.primary, scheme.surfaceContainerHighest) >= 4.5f)
        assertTrue("onPrimary on primary", contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
        assertTrue("onErrorContainer on errorContainer", contrast(scheme.onErrorContainer, scheme.errorContainer) >= 4.5f)
        assertTrue("onError on error", contrast(scheme.onError, scheme.error) >= 4.5f)
    }

    @Test
    fun topBarToggleTintsMeetContrastInLightTheme() {
        // Light theme top bar toggle (Start/Stop) tints must meet WCAG AA 4.5:1 on the bar's surface and app background
        val scheme = LjLightColorScheme
        assertTrue("LjLightSuccess on LjLightSurface", contrast(LjLightSuccess, LjLightSurface) >= 4.5f)
        assertTrue("LjLightSuccess on LjLightBg", contrast(LjLightSuccess, LjLightBg) >= 4.5f)
        assertTrue("LjLightError on LjLightSurface", contrast(scheme.error, LjLightSurface) >= 4.5f)
    }

    private fun contrast(
        fg: Color,
        bg: Color,
    ): Float {
        val fgLum = relativeLuminance(fg)
        val bgLum = relativeLuminance(bg)
        val lighter = maxOf(fgLum, bgLum)
        val darker = minOf(fgLum, bgLum)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun relativeLuminance(color: Color): Float {
        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun linearize(channel: Float): Float = if (channel <= 0.03928f) channel / 12.92f else (((channel + 0.055f) / 1.055f).pow(2.4f))

    private fun channelSum(color: Color): Float = color.red + color.green + color.blue
}
