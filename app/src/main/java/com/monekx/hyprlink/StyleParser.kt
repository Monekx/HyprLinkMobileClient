package com.monekx.hyprlink

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight

object StyleParser {
    fun parseColor(css: String?, property: String, default: Color): Color {
        if (css == null) return default
        val pattern = "$property:\\s*(#[0-9a-fA-F]{3,8}|rgba?\\([^\\)]+\\))".toRegex()
        val match = pattern.find(css)
        return try {
            match?.groupValues?.get(1)?.let { value ->
                if (value.startsWith("#")) {
                    Color(android.graphics.Color.parseColor(value))
                } else if (value.startsWith("rgb")) {
                    parseRgb(value)
                } else default
            } ?: default
        } catch (e: Exception) { default }
    }

    private fun parseRgb(rgb: String): Color {
        val values = rgb.replace(Regex("[rgba()\\s]"), "").split(",")
        return Color(
            values[0].toInt(),
            values[1].toInt(),
            values[2].toInt(),
            values.getOrNull(3)?.toFloat()?.toInt() ?: 255
        )
    }

    fun parseSize(css: String?, property: String, default: Int): Int {
        if (css == null) return default
        val pattern = "$property:\\s*(\\d+)px".toRegex()
        return pattern.find(css)?.groupValues?.get(1)?.toIntOrNull() ?: default
    }

    fun parseFontWeight(css: String?, property: String): FontWeight {
        if (css == null) return FontWeight.Normal
        val pattern = "$property:\\s*(\\d+|bold|normal)".toRegex()
        return when (pattern.find(css)?.groupValues?.get(1)) {
            "bold" -> FontWeight.Bold
            "700", "800", "900" -> FontWeight.W700
            "500", "600" -> FontWeight.Medium
            "300" -> FontWeight.Light
            else -> FontWeight.Normal
        }
    }
}