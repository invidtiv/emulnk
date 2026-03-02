package com.emulnk.core

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

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
}
