package com.droid.dolphy

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color

object MonetHelper {
    // Dynamic Monet primary accent color on API 31+, fallback to Dolphy orange
    @JvmStatic
    fun getMonetPrimaryColor(context: Context): Color {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                val resId = context.resources.getIdentifier("system_accent1_500", "color", "android")
                if (resId != 0) {
                    return Color(context.getColor(resId))
                }
                val resId300 = context.resources.getIdentifier("system_accent1_300", "color", "android")
                if (resId300 != 0) {
                    return Color(context.getColor(resId300))
                }
            } catch (_: Throwable) {
            }
        }
        return Color(4294940672L)
    }

    // Dynamic Monet 3-color palette for gradient preview in AccentColorScreen
    @JvmStatic
    fun getMonetGradient(context: Context): List<Color> {
        val primary = getMonetPrimaryColor(context)
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                val resA2 = context.resources.getIdentifier("system_accent2_500", "color", "android")
                val resA3 = context.resources.getIdentifier("system_accent3_500", "color", "android")
                val c2 = if (resA2 != 0) Color(context.getColor(resA2)) else primary.copy(alpha = 0.7f)
                val c3 = if (resA3 != 0) Color(context.getColor(resA3)) else primary.copy(alpha = 0.9f)
                return listOf(primary, c2, c3)
            } catch (_: Throwable) {
            }
        }
        return listOf(primary, primary.copy(alpha = 0.7f), primary.copy(alpha = 0.9f))
    }
}
