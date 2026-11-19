package com.chencohensolutions.knesset.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.pow

/** WCAG contrast, used by tests and by the header scrim (docs 2.1 "Contrast"). */
object Contrast {
    private fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    fun luminance(color: Color): Double = 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    fun ratio(a: Color, b: Color): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /**
     * Dark veil over header gradients so white titles reach 3:1 on the light ends (cyan, orange, green,
     * amber). The gradient keeps its hue.
     */
    val headerScrim = Color.Black.copy(alpha = 0.24f)

    fun underScrim(c: Color): Color = headerScrim.compositeOver(c)
}
