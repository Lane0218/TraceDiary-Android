package com.tracediary.app.ui.theme

import com.tracediary.app.data.settings.ThemeColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorTest {
    @Test
    fun storageValuesRoundTripAndUnknownValueDefaultsToForest() {
        ThemeColor.entries.forEach { color ->
            assertEquals(color, ThemeColor.fromStorageValue(color.storageValue))
        }
        assertEquals(ThemeColor.Forest, ThemeColor.fromStorageValue(null))
        assertEquals(ThemeColor.Forest, ThemeColor.fromStorageValue("unknown"))
        assertEquals(ThemeColor.MistBlue, ThemeColor.fromStorageValue("mist_blue"))
        assertEquals(ThemeColor.DuskPurple, ThemeColor.fromStorageValue("dusk_purple"))
        assertEquals(ThemeColor.Terracotta, ThemeColor.fromStorageValue("terracotta"))
        assertEquals(ThemeColor.RoseBerry, ThemeColor.fromStorageValue("rose_berry"))
    }

    @Test
    fun everyThemeHasLegibleAccentRoles() {
        assertEquals(ThemeColor.entries.size, ThemeColor.entries.map { colorSchemeFor(it).primary }.distinct().size)
        ThemeColor.entries.forEach { color ->
            val scheme = colorSchemeFor(color)
            assertTrue(color.label, contrast(scheme.primary, scheme.onPrimary) >= 4.5)
            assertTrue(color.label, contrast(scheme.primary, scheme.background) >= 4.5)
            assertTrue(color.label, contrast(scheme.primaryContainer, scheme.onPrimaryContainer) >= 4.5)
            assertEquals(scheme.primary, scheme.surfaceTint)
            assertEquals(scheme.primaryContainer, scheme.tertiaryContainer)
        }
    }

    private fun contrast(first: androidx.compose.ui.graphics.Color, second: androidx.compose.ui.graphics.Color): Double {
        fun luminance(color: androidx.compose.ui.graphics.Color): Double {
            fun channel(value: Float): Double {
                val linear = value.toDouble()
                return if (linear <= 0.04045) linear / 12.92 else Math.pow((linear + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
        }
        val bright = maxOf(luminance(first), luminance(second))
        val dark = minOf(luminance(first), luminance(second))
        return (bright + 0.05) / (dark + 0.05)
    }
}
