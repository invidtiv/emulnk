package com.emulnk.model

/**
 * User-created overlay pairing for dual-screen devices.
 */
data class OverlayBundle(
    val primaryOverlayId: String?,
    val secondaryOverlayId: String?,
    val screenSwapped: Boolean = false
)

/**
 * Global application preferences.
 */
data class AppConfig(
    val autoBoot: Boolean = true,
    val repoUrl: String = "https://github.com/EmuLnk/emulnk-repo/archive/refs/heads/main.zip",
    val defaultThemes: Map<String, String> = emptyMap(), // GameID -> ThemeID
    val defaultOverlays: Map<String, String> = emptyMap(), // GameID -> OverlayID
    val defaultBundles: Map<String, OverlayBundle> = emptyMap(), // GameID -> OverlayBundle
    val devMode: Boolean = false,
    val devUrl: String = ""
) {
    val repoUrlShim get() = if (devMode) devUrl else repoUrl
}
