package com.emulnk.bridge

import com.emulnk.BuildConfig
import com.emulnk.core.BridgeConstants

/**
 * Shared rate limiter for all bridge instances (EmuLinkBridge + OverlayBridge).
 * Prevents bundle themes from doubling the effective write/vibrate/sound rate.
 */
object BridgeRateLimiter {

    private var writeCount = 0
    private var writeWindowStart = 0L
    private var lastVibrateTime = 0L
    private var lastSoundTime = 0L

    /**
     * Returns true if under write rate limit (30/sec sliding window), false if rate-limited.
     */
    @Synchronized
    fun checkWriteLimit(): Boolean {
        val now = System.currentTimeMillis()
        if (now - writeWindowStart > 1000L) {
            writeCount = 0
            writeWindowStart = now
        }
        if (writeCount >= BridgeConstants.WRITE_MAX_PER_SECOND) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d("BridgeRateLimiter", "write rate limited ($writeCount calls in window)")
            }
            return false
        }
        writeCount++
        return true
    }

    /**
     * Returns true if vibrate cooldown has elapsed, false if too soon.
     */
    @Synchronized
    fun checkVibrateLimit(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastVibrateTime < BridgeConstants.VIBRATE_MIN_INTERVAL_MS) return false
        lastVibrateTime = now
        return true
    }

    /**
     * Returns true if sound cooldown has elapsed, false if too soon.
     */
    @Synchronized
    fun checkSoundLimit(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastSoundTime < BridgeConstants.SOUND_MIN_INTERVAL_MS) return false
        lastSoundTime = now
        return true
    }
}
