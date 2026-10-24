package com.pavo.amberoid.data.model

import androidx.compose.ui.graphics.Color

data class CoverPalette(
    val primary: Color,
    val secondary: Color,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val isDark: Boolean,
    val allSwatches: List<Color>
)