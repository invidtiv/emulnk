package com.emulnk.core

/**
 * Application-wide constants for timeouts, intervals, and thresholds.
 * Centralizes magic numbers for maintainability.
 */

object MemoryConstants {
    /** UDP socket timeout in milliseconds */
    const val SOCKET_TIMEOUT_MS = 500

    /** Memory polling interval in milliseconds */
    const val POLLING_INTERVAL_MS = 200L

    /** Minimum allowed polling interval in milliseconds */
    const val MIN_POLLING_INTERVAL_MS = 50L

    /** Maximum allowed polling interval in milliseconds */
    const val MAX_POLLING_INTERVAL_MS = 5000L

    /** Delay between detection retry attempts in milliseconds */
    const val DETECTION_RETRY_DELAY_MS = 1000L

    /** Delay after successful detection before starting polling in milliseconds */
    const val DETECTION_SUCCESS_DELAY_MS = 3000L

    /** Maximum number of consecutive detection failures before clearing game state */
    const val MAX_DETECTION_FAILURES = 3

    /** Number of consecutive failures before re-identifying the emulator on each port.
     *  Handles emulator switches on the same port (e.g. Dolphin → RetroArch). */
    const val IDENTITY_REFRESH_FAILURES = 10

    /** Virtual address used for file-based serial extraction (all RetroArch cores) */
    const val VIRTUAL_SERIAL_ADDR = 0x00200000L

    /** Maximum single read size in bytes */
    const val MAX_READ_SIZE = 2048

    /** Maximum valid 32-bit address */
    const val MAX_ADDRESS = 0xFFFFFFFFL

    /** Maximum depth for multi-level pointer chains */
    const val MAX_POINTER_CHAIN_DEPTH = 10

    /** EMLK discovery handshake magic bytes ("EMLK") */
    val IDENTIFY_MAGIC = byteArrayOf(0x45, 0x4D, 0x4C, 0x4B)

    /** UDP timeout for EMLK identify requests in milliseconds */
    const val IDENTIFY_TIMEOUT_MS = 300

    /** UDP timeout for virtual serial reads (file I/O on emulator side) in milliseconds */
    const val VIRTUAL_SERIAL_TIMEOUT_MS = 1500
}

object UiConstants {
    /** Time window to press back twice to exit app in milliseconds */
    const val BACK_PRESS_EXIT_DELAY_MS = 2000L

    /** Auto-hide overlay delay in milliseconds */
    const val AUTO_HIDE_OVERLAY_DELAY_MS = 4000L

    /** Delay before retrying theme updateData injection in milliseconds */
    const val THEME_INJECT_RETRY_DELAY_MS = 300L
}

object TelemetryConstants {
    /** Telemetry update interval in milliseconds */
    const val UPDATE_INTERVAL_MS = 5000L

    /** Threshold for converting millidegrees to degrees Celsius */
    const val THERMAL_MILLIDEGREE_THRESHOLD = 1000f
}

object NetworkConstants {
    /** HTTP connection timeout in milliseconds */
    const val CONNECT_TIMEOUT_MS = 2000

    /** HTTP read timeout in milliseconds */
    const val READ_TIMEOUT_MS = 3000
}

object SyncConstants {
    /** Maximum total extracted size in bytes (100 MB) */
    const val MAX_EXTRACT_SIZE_BYTES = 100L * 1024 * 1024

    /** Maximum number of ZIP entries */
    const val MAX_ZIP_ENTRIES = 500

    /** Maximum download size in bytes (50 MB) */
    const val MAX_DOWNLOAD_SIZE_BYTES = 50L * 1024 * 1024

    /** Maximum retry attempts for network requests */
    const val MAX_RETRIES = 3

    /** Initial retry delay in milliseconds */
    const val INITIAL_RETRY_DELAY_MS = 1000L
}

object BridgeConstants {
    /** Minimum interval between vibrate calls in milliseconds */
    const val VIBRATE_MIN_INTERVAL_MS = 50L

    /** Maximum vibrate duration in milliseconds */
    const val VIBRATE_MAX_DURATION_MS = 5000L

    /** Minimum interval between playSound calls in milliseconds */
    const val SOUND_MIN_INTERVAL_MS = 200L

    /** Maximum write calls per second */
    const val WRITE_MAX_PER_SECOND = 30

    /** Valid write sizes */
    val VALID_WRITE_SIZES = setOf(1, 2, 4)

    /** Age threshold for cleaning up orphaned dev sound files in milliseconds */
    const val DEV_SOUND_CLEANUP_AGE_MS = 60_000L
}

object OverlayConstants {
    /** Long-press threshold to enter edit mode in milliseconds */
    const val LONG_PRESS_THRESHOLD_MS = 800L

    /** Notification channel ID for overlay service */
    const val NOTIFICATION_CHANNEL_ID = "emulnk_overlay"

    /** Notification ID for overlay service foreground notification */
    const val NOTIFICATION_ID = 1001

    /** Minimum drag distance to consider a move (pixels) */
    const val DRAG_THRESHOLD_PX = 10

    /** Snap-to-edge threshold in dp */
    const val SNAP_THRESHOLD_DP = 8

    /** Semi-transparent scrim color (80% SurfaceBase) */
    const val EDIT_SCRIM_COLOR = 0xCC0E0C1C

    /** Border corner radius in dp */
    const val EDIT_BORDER_RADIUS_DP = 8

    /** Selected widget border width in dp */
    const val EDIT_BORDER_SELECTED_WIDTH_DP = 2

    /** Unselected widget border width in dp */
    const val EDIT_BORDER_NORMAL_WIDTH_DP = 1

    /** Screen target identifiers for dual-screen overlays */
    const val SCREEN_PRIMARY = "primary"
    const val SCREEN_SECONDARY = "secondary"
}

object MathConstants {
    /** Maximum formula expression length */
    const val MAX_EXPRESSION_LENGTH = 256

    /** Maximum nesting depth for parentheses */
    const val MAX_NESTING_DEPTH = 20
}
