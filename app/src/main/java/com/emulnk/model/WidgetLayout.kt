package com.emulnk.model

/**
 * Screen dimensions in dp, used for proportional layout scaling across displays.
 */
data class ScreenDimensions(val widthDp: Int, val heightDp: Int)

/**
 * Persisted layout state for an overlay's widgets.
 */
data class OverlayLayout(
    val widgets: Map<String, WidgetLayoutState> = emptyMap(),
    val screenDimensions: ScreenDimensions? = null
)

/**
 * Per-widget position, size, and visibility state.
 */
data class WidgetLayoutState(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val enabled: Boolean = true,
    val alpha: Float = 1.0f
)
