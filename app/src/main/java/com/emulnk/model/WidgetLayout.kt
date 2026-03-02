package com.emulnk.model

/**
 * Persisted layout state for an overlay's widgets.
 */
data class OverlayLayout(
    val widgets: Map<String, WidgetLayoutState> = emptyMap()
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
