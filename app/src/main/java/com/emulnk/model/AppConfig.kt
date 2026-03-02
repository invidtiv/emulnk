package com.emulnk.model

/**
 * Global application preferences.
 */
data class AppConfig(
    val autoBoot: Boolean = true,
    val repoUrl: String = "https://github.com/EmuLnk/emulnk-repo/archive/refs/heads/feature/overlay.zip",
    val defaultThemes: Map<String, String> = emptyMap(), // GameID -> ThemeID
    val defaultOverlays: Map<String, String> = emptyMap(), // GameID -> OverlayID
    val devMode: Boolean = false,
    val devUrl: String = ""
)
