package com.emulnk.core

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.emulnk.model.ScreenDimensions

/**
 * Utility for detecting multiple physical displays (dual-screen handhelds like AYN THOR).
 */
object DisplayHelper {
    /**
     * Returns true if the device has more than one physical display.
     */
    fun isDualScreen(context: Context): Boolean {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            ?: return false
        val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        return displays.isNotEmpty()
    }

    /**
     * Returns the secondary presentation display, or null if none.
     */
    fun getSecondaryDisplay(context: Context): Display? {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            ?: return null
        val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        return displays.firstOrNull()
    }

    /**
     * Extracts dp dimensions from a display using the context's density.
     */
    fun getDisplayDimensions(context: Context, display: Display): ScreenDimensions {
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        val widthDp = (metrics.widthPixels / metrics.density).toInt()
        val heightDp = (metrics.heightPixels / metrics.density).toInt()
        return ScreenDimensions(widthDp, heightDp)
    }

    /**
     * Returns dp dimensions of the secondary display, or null if no secondary display.
     */
    fun getSecondaryDimensions(context: Context): ScreenDimensions? {
        val display = getSecondaryDisplay(context) ?: return null
        return getDisplayDimensions(context, display)
    }

    /**
     * Returns the density of a specific display.
     */
    fun getDisplayDensity(context: Context, display: Display): Float {
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return metrics.density
    }

    /**
     * Returns the density of the secondary display, or null if no secondary display.
     */
    fun getSecondaryDensity(context: Context): Float? {
        val display = getSecondaryDisplay(context) ?: return null
        return getDisplayDensity(context, display)
    }
}
