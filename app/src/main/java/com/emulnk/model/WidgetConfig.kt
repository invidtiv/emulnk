package com.emulnk.model

/**
 * Defines a single widget in an overlay theme.
 * Mirrors the widget entries in theme.json.
 */
data class WidgetConfig(
    val id: String,
    val label: String,
    val src: String,
    val defaultWidth: Int,
    val defaultHeight: Int,
    val defaultX: Int = 0,
    val defaultY: Int = 0,
    val resizable: Boolean = true,
    val transparent: Boolean = true,
    val minWidth: Int = 60,
    val minHeight: Int = 60
)
