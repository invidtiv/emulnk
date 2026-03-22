package com.emulnk.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.emulnk.BuildConfig
import com.emulnk.model.AppConfig
import com.emulnk.model.ScreenTarget
import java.io.File

class MigrationManager(
    private val context: Context,
    private val configManager: ConfigManager
) {
    companion object {
        private const val TAG = "MigrationManager"
        private const val CURRENT_MIGRATION_VERSION = 2

        val RENAME_MAP = mapOf(
            "WindWakerMinimap" to "GZLNavigator",
            "WindWakerWidgets" to "GZLSeaOverlay",
            "EmeraldPartyHUD" to "BPEPartyHUD",
            "FF7SenseHUD" to "FF7BattleHUD",
            "MetroidVisor" to "SMVisor",
            "MetroidAutomap" to "M1Automap",
            "CrystalHUD" to "PMCCompanion",
            "CrystalWidgets" to "PMCBattleOverlay",
            "GoldenEyeHUD" to "GEMissionHUD",
            "HeartGoldWidgets" to "IPKETypeBadges",
            "HeartGoldMoveEff" to "IPKEMoveEff"
        )

        // V2→V3: composite theme folder naming (profileId_themeId)
        val COMPOSITE_RENAME_MAP = mapOf(
            "GZLNavigator" to "GZL_GZLNavigator",
            "GZLSeaOverlay" to "GZL_GZLSeaOverlay",
            "PMCCompanion" to "PMC_PMCCompanion",
            "PMCBattleOverlay" to "PMC_PMCBattleOverlay",
            "BPEPartyHUD" to "BPE_BPEPartyHUD",
            "IPKETypeBadges" to "IPKE_IPKETypeBadges",
            "IPKEMoveEff" to "IPKE_IPKEMoveEff",
            "MH4UMonsterIntel" to "MH4U_MH4UMonsterIntel",
            "MHFUHunterHUD" to "MHFU_MHFUHunterHUD",
            "FF7BattleHUD" to "FF7_FF7BattleHUD",
            "SMVisor" to "SM_SMVisor",
            "M1Automap" to "M1_M1Automap",
            "GEMissionHUD" to "GE_GEMissionHUD",
            "PSIVComboCodex" to "PSIV_PSIVComboCodex",
            "EmuDex" to "BPRE_EmuDex",
        )
    }

    private val prefs = context.getSharedPreferences("emulink_prefs", Context.MODE_PRIVATE)

    fun runIfNeeded() {
        val currentVersion = prefs.getInt("migration_version", 0)
        if (currentVersion >= CURRENT_MIGRATION_VERSION) return

        if (currentVersion < 1) {
            migrateV1ToV2()
        }
        if (currentVersion < 2) {
            migrateV2CompositeIds()
        }

        prefs.edit { putInt("migration_version", CURRENT_MIGRATION_VERSION) }
    }

    /**
     * V1→V2: repo renamed theme IDs to use profile-based naming; rename local folders and update AppConfig references.
     * No rollback: renames are idempotent (skip if target exists), so partial failures are safe to re-run.
     */
    private fun migrateV1ToV2() {
        val themesDir = configManager.getThemesDir()
        val savesDir = configManager.getSavesDir()

        if (!themesDir.exists()) return

        var renamedCount = 0

        for ((oldId, newId) in RENAME_MAP) {
            // Rename theme folder
            val oldFolder = File(themesDir, oldId)
            val newFolder = File(themesDir, newId)
            if (oldFolder.exists() && !newFolder.exists()) {
                if (oldFolder.renameTo(newFolder)) {
                    renamedCount++
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Renamed theme folder: $oldId -> $newId")
                    }
                } else {
                    Log.w(TAG, "Failed to rename theme folder: $oldId -> $newId")
                }
            }

            // Rename save files
            if (savesDir.exists()) {
                renameSaveFile(savesDir, "$oldId.json", "$newId.json")
                renameSaveFile(savesDir, "${oldId}_layout.json", "${newId}_layout.json")
                // Screen-specific layout files
                for (screen in ScreenTarget.entries) {
                    val screenName = screen.name.lowercase()
                    renameSaveFile(savesDir, "${oldId}_layout_${screenName}.json", "${newId}_layout_${screenName}.json")
                }
            }
        }

        // Migrate AppConfig references
        migrateAppConfig()

        if (renamedCount > 0) {
            Log.i(TAG, "Migration v1->v2: renamed $renamedCount theme folders")
        }
    }

    /**
     * V2→V3: composite theme folder naming. Local folders become {profileId}_{themeId}
     * so themes with the same ID under different profiles don't collide.
     */
    private fun migrateV2CompositeIds() {
        val themesDir = configManager.getThemesDir()
        val savesDir = configManager.getSavesDir()

        if (!themesDir.exists()) return

        var renamedCount = 0

        for ((oldId, newId) in COMPOSITE_RENAME_MAP) {
            val oldFolder = File(themesDir, oldId)
            val newFolder = File(themesDir, newId)
            if (oldFolder.exists() && !newFolder.exists()) {
                if (oldFolder.renameTo(newFolder)) {
                    renamedCount++
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Composite rename: $oldId -> $newId")
                    }
                } else {
                    Log.w(TAG, "Failed composite rename: $oldId -> $newId")
                }
            }

            if (savesDir.exists()) {
                renameSaveFile(savesDir, "$oldId.json", "$newId.json")
                renameSaveFile(savesDir, "${oldId}_layout.json", "${newId}_layout.json")
                for (screen in ScreenTarget.entries) {
                    val screenName = screen.name.lowercase()
                    renameSaveFile(savesDir, "${oldId}_layout_${screenName}.json", "${newId}_layout_${screenName}.json")
                }
            }
        }

        migrateAppConfig()

        if (renamedCount > 0) {
            Log.i(TAG, "Migration v2->v3: renamed $renamedCount theme folders to composite IDs")
        }
    }

    private fun renameSaveFile(dir: File, oldName: String, newName: String) {
        val oldFile = File(dir, oldName)
        val newFile = File(dir, newName)
        if (oldFile.exists() && !newFile.exists()) {
            if (oldFile.renameTo(newFile)) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Renamed save file: $oldName -> $newName")
                }
            } else {
                Log.w(TAG, "Failed to rename save file: $oldName -> $newName")
            }
        }
    }

    private fun migrateAppConfig() {
        val allRenames = RENAME_MAP + COMPOSITE_RENAME_MAP
        val appConfig = configManager.getAppConfig()
        var changed = false

        val newDefaultThemes = appConfig.defaultThemes.toMutableMap()
        for ((gameId, themeId) in appConfig.defaultThemes) {
            allRenames[themeId]?.let { newId ->
                newDefaultThemes[gameId] = newId
                changed = true
            }
        }

        val newDefaultOverlays = appConfig.defaultOverlays.toMutableMap()
        for ((gameId, overlayId) in appConfig.defaultOverlays) {
            allRenames[overlayId]?.let { newId ->
                newDefaultOverlays[gameId] = newId
                changed = true
            }
        }

        val newDefaultBundles = appConfig.defaultBundles.toMutableMap()
        for ((gameId, bundle) in appConfig.defaultBundles) {
            val newPrimary = bundle.primaryOverlayId?.let { allRenames[it] }
            val newSecondary = bundle.secondaryOverlayId?.let { allRenames[it] }
            if (newPrimary != null || newSecondary != null) {
                newDefaultBundles[gameId] = bundle.copy(
                    primaryOverlayId = newPrimary ?: bundle.primaryOverlayId,
                    secondaryOverlayId = newSecondary ?: bundle.secondaryOverlayId
                )
                changed = true
            }
        }

        if (changed) {
            val migrated = appConfig.copy(
                defaultThemes = newDefaultThemes,
                defaultOverlays = newDefaultOverlays,
                defaultBundles = newDefaultBundles
            )
            configManager.saveAppConfig(migrated)
            Log.i(TAG, "AppConfig references migrated")
        }
    }
}
