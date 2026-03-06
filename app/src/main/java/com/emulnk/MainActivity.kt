package com.emulnk

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emulnk.core.DisplayHelper
import com.emulnk.core.OverlayService
import com.emulnk.core.UiConstants
import com.emulnk.model.DisplayInfo
import com.emulnk.model.SafeArea
import com.emulnk.model.SystemInfo
import com.emulnk.model.ThemeConfig
import com.emulnk.model.ThemeType
import com.emulnk.model.resolvedType
import com.emulnk.ui.components.AppSettingsDialog
import com.emulnk.ui.components.SyncProgressDialog
import com.emulnk.ui.navigation.Screen
import com.emulnk.ui.screens.DashboardScreen
import com.emulnk.ui.screens.GalleryScreen
import com.emulnk.ui.screens.LauncherScreen
import com.emulnk.ui.screens.OnboardingScreen
import com.emulnk.ui.theme.EmuLinkTheme
import com.emulnk.ui.theme.SurfaceBase
import com.emulnk.ui.viewmodel.MainViewModel
import com.google.gson.Gson

class MainActivity : ComponentActivity() {
    private val gson = Gson()
    private val vm: MainViewModel by viewModels()

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            val docId = DocumentsContract.getTreeDocumentId(it)
            val split = docId.split(":")
            val type = split[0]
            val path = if (split.size > 1) {
                if ("primary".equals(type, ignoreCase = true)) {
                    Environment.getExternalStorageDirectory().absolutePath + "/" + split[1]
                } else {
                    "/storage/$type/${split[1]}"
                }
            } else {
                it.path
            }

            if (path != null) {
                vm.updateRootDirectory(path)
                vm.addDebugLog("Folder verified: $path")
            }
        }
    }

    private val themeImporter = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.importTheme(it) }
    }

    private var pendingOverlayTheme: ThemeConfig? = null
    private var pendingPairedOverlay = false
    private var onOverlayStarted: (() -> Unit)? = null

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) {
            pendingOverlayTheme?.let {
                if (pendingPairedOverlay) startPairedOverlayService(it) else startOverlayService(it)
            }
        } else {
            Toast.makeText(this, getString(R.string.overlay_permission_required), Toast.LENGTH_SHORT).show()
            if (!pendingPairedOverlay) {
                vm.selectTheme(null as ThemeConfig?)
            }
        }
        pendingOverlayTheme = null
        pendingPairedOverlay = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EmuLinkTheme {
                val uiState by vm.uiState.collectAsState()
                val detectedGameId by vm.detectedGameId.collectAsState()
                val availableThemes by vm.availableThemes.collectAsState()
                val selectedTheme by vm.selectedTheme.collectAsState()
                val debugLogs by vm.debugLogs.collectAsState()
                val appConfig by vm.appConfig.collectAsState()
                val rootPath by vm.rootPath.collectAsState()
                val onboardingCompleted by vm.onboardingCompleted.collectAsState()
                val pairedOverlay by vm.pairedOverlay.collectAsState()
                val pairedSecondaryOverlay by vm.pairedSecondaryOverlay.collectAsState()
                val isDualScreen by vm.isDualScreen.collectAsState()
                val isSyncing by vm.isSyncing.collectAsState()
                val repoIndex by vm.repoIndex.collectAsState()
                val syncMessage by vm.syncMessage.collectAsState()
                val allInstalledThemes by vm.allInstalledThemes.collectAsState()
                val isRootPathSet by vm.isRootPathSet.collectAsState()

                var currentScreen by remember { mutableStateOf<Screen>(Screen.Onboarding) }
                onOverlayStarted = { currentScreen = Screen.Overlay }
                var showAppSettings by remember { mutableStateOf(false) }

                LaunchedEffect(onboardingCompleted, selectedTheme, pairedOverlay, pairedSecondaryOverlay) {
                    when {
                        !onboardingCompleted -> currentScreen = Screen.Onboarding
                        selectedTheme != null -> {
                            // Overlay bundle (dual-screen overlay-overlay pairing)
                            if (pairedSecondaryOverlay != null && selectedTheme!!.resolvedType == ThemeType.OVERLAY) {
                                if (OverlayService.isRunning()) {
                                    currentScreen = Screen.Overlay
                                } else {
                                    launchOverlayBundle(selectedTheme!!, pairedSecondaryOverlay)
                                }
                            } else when (selectedTheme!!.resolvedType) {
                                ThemeType.BUNDLE -> {
                                    currentScreen = Screen.Dashboard
                                    if (selectedTheme!!.widgets != null && !OverlayService.isRunning()) {
                                        launchPairedOverlay(selectedTheme!!)
                                    }
                                }
                                ThemeType.OVERLAY -> {
                                    if (OverlayService.isRunning()) {
                                        currentScreen = Screen.Overlay
                                    } else {
                                        launchOverlay(selectedTheme!!)
                                    }
                                }
                                else -> {
                                    currentScreen = Screen.Dashboard
                                    if (pairedOverlay != null && !OverlayService.isRunning()) {
                                        launchPairedOverlay(pairedOverlay!!)
                                    }
                                }
                            }
                        }
                        currentScreen == Screen.Onboarding -> currentScreen = Screen.Home
                        currentScreen == Screen.Dashboard || currentScreen == Screen.Overlay -> currentScreen = Screen.Home
                    }
                }

                val density = androidx.compose.ui.platform.LocalDensity.current
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val windowInsets = WindowInsets.systemBars

                val scale = density.density
                val topDp = (windowInsets.getTop(density) / scale).toInt()
                val bottomDp = (windowInsets.getBottom(density) / scale).toInt()
                val leftDp = (windowInsets.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr) / scale).toInt()
                val rightDp = (windowInsets.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr) / scale).toInt()
                val buttonSpaceDp = 56
                val orientation = if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 90 else 0

                LaunchedEffect(topDp, bottomDp, leftDp, rightDp, orientation) {
                    val secDims = DisplayHelper.getSecondaryDimensions(this@MainActivity)
                    vm.updateSystemInfo(
                        SystemInfo(
                            safeArea = SafeArea(
                                top = topDp + buttonSpaceDp,
                                bottom = bottomDp,
                                left = leftDp,
                                right = rightDp
                            ),
                            display = DisplayInfo(
                                width = configuration.screenWidthDp,
                                height = configuration.screenHeightDp,
                                orientation = orientation,
                                isDualScreen = DisplayHelper.isDualScreen(this@MainActivity),
                                secondaryWidth = secDims?.widthDp ?: 0,
                                secondaryHeight = secDims?.heightDp ?: 0
                            )
                        )
                    )
                }

                Surface(modifier = Modifier.fillMaxSize(), color = SurfaceBase) {
                    when (currentScreen) {
                        is Screen.Onboarding -> OnboardingScreen(
                            rootPath = rootPath,
                            isRootPathSet = isRootPathSet,
                            appConfig = appConfig,
                            onGrantPermission = { requestStoragePermission() },
                            onSelectFolder = { folderPicker.launch(null) },
                            onGrantOverlayPermission = { requestOverlayPermission() },
                            onSetAutoBoot = { vm.setAutoBoot(it) },
                            onSetRepoUrl = { vm.setRepoUrl(it) },
                            onResetRepoUrl = { vm.resetRepoUrl() },
                            onCompleteOnboarding = { vm.completeOnboarding() }
                        )
                        is Screen.Home -> {
                            var backPressedOnce by remember { mutableStateOf(false) }

                            LaunchedEffect(backPressedOnce) {
                                if (backPressedOnce) {
                                    kotlinx.coroutines.delay(UiConstants.BACK_PRESS_EXIT_DELAY_MS)
                                    backPressedOnce = false
                                }
                            }

                            BackHandler {
                                if (backPressedOnce) {
                                    (this@MainActivity).finishAffinity()
                                } else {
                                    backPressedOnce = true
                                    Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                                }
                            }

                            LauncherScreen(
                                detectedGameId = detectedGameId,
                                themes = availableThemes,
                                isSyncing = isSyncing,
                                appConfig = appConfig,
                                rootPath = rootPath,
                                isDualScreen = isDualScreen,
                                onSelectTheme = { vm.selectTheme(it) },
                                onSelectPair = { theme, overlay, setDefault ->
                                    vm.selectPair(theme, overlay)
                                    if (setDefault && detectedGameId != null) {
                                        vm.setDefaultPairForGame(detectedGameId!!, theme?.id, overlay?.id)
                                    }
                                },
                                onSelectOverlayBundle = { primary, secondary, setDefault ->
                                    vm.selectOverlayBundle(primary, secondary)
                                    if (setDefault && detectedGameId != null) {
                                        vm.setDefaultBundleForGame(detectedGameId!!, primary?.id, secondary?.id)
                                    }
                                },
                                onSetDefaultTheme = { gameId, themeId -> vm.setDefaultThemeForGame(gameId, themeId) },
                                onOpenGallery = {
                                    vm.fetchGallery()
                                    currentScreen = Screen.Gallery
                                },
                                onOpenSettings = { showAppSettings = true },
                                onSync = { vm.syncRepository() }
                            )
                        }
                        is Screen.Gallery -> {
                            BackHandler { currentScreen = Screen.Home }

                            GalleryScreen(
                                repoIndex = repoIndex,
                                isSyncing = isSyncing,
                                allInstalledThemes = allInstalledThemes,
                                appVersionCode = vm.getAppVersionCode(),
                                isDualScreen = isDualScreen,
                                onBack = { currentScreen = Screen.Home },
                                onImportTheme = { themeImporter.launch("application/zip") },
                                onSelectTheme = { vm.selectTheme(it) },
                                onDownloadTheme = { vm.downloadTheme(it) },
                                onDeleteTheme = { vm.deleteTheme(it) }
                            )
                        }
                        is Screen.Dashboard -> {
                            BackHandler {
                                if (OverlayService.isRunning()) {
                                    stopOverlayService()
                                }
                                vm.selectTheme(null as ThemeConfig?)
                            }

                            selectedTheme?.let { theme ->
                                DashboardScreen(
                                    vm = vm,
                                    gson = gson,
                                    theme = theme,
                                    uiState = uiState,
                                    debugLogs = debugLogs,
                                    onExitTheme = {
                                        if (OverlayService.isRunning()) {
                                            stopOverlayService()
                                        }
                                        vm.selectTheme(null as ThemeConfig?)
                                    }
                                )
                            }
                        }

                        is Screen.Overlay -> {
                            BackHandler {
                                stopOverlayService()
                                vm.selectTheme(null as ThemeConfig?)
                            }

                            OverlayActiveScreen(
                                themeName = selectedTheme?.meta?.name ?: "Overlay",
                                onExit = {
                                    stopOverlayService()
                                    vm.selectTheme(null as ThemeConfig?)
                                }
                            )
                        }
                    }

                    if (isSyncing) {
                        SyncProgressDialog(message = syncMessage)
                    }

                    if (showAppSettings) {
                        AppSettingsDialog(
                            appConfig = appConfig,
                            rootPath = rootPath,
                            appVersionCode = vm.getAppVersionCode(),
                            onDismiss = { showAppSettings = false },
                            onSetAutoBoot = { vm.setAutoBoot(it) },
                            onSetRepoUrl = { vm.setRepoUrl(it) },
                            onResetRepoUrl = { vm.resetRepoUrl() },
                            onChangeRootFolder = {
                                showAppSettings = false
                                folderPicker.launch(null)
                            },
                            onSetDevMode = { vm.setDevMode(it) },
                            onSetDevUrl = { vm.setDevUrl(it) }
                        )
                    }
                }
            }
        }
    }

    private fun launchOverlay(theme: ThemeConfig) {
        if (!Settings.canDrawOverlays(this)) {
            pendingOverlayTheme = theme
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            overlayPermissionLauncher.launch(intent)
            return
        }
        startOverlayService(theme)
    }

    private fun startOverlayService(theme: ThemeConfig) {
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_THEME_JSON, gson.toJson(theme))
        }
        startForegroundService(intent)
        onOverlayStarted?.invoke()
        moveTaskToBack(true)
    }

    private fun launchPairedOverlay(theme: ThemeConfig) {
        if (!Settings.canDrawOverlays(this)) {
            pendingOverlayTheme = theme
            pendingPairedOverlay = true
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            overlayPermissionLauncher.launch(intent)
            return
        }
        startPairedOverlayService(theme)
    }

    private fun startPairedOverlayService(theme: ThemeConfig) {
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_THEME_JSON, gson.toJson(theme))
        }
        startForegroundService(intent)
        // Don't moveTaskToBack — stay on dashboard
    }

    private fun launchOverlayBundle(primary: ThemeConfig, secondary: ThemeConfig?) {
        if (!Settings.canDrawOverlays(this)) {
            pendingOverlayTheme = primary
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            overlayPermissionLauncher.launch(intent)
            return
        }
        startOverlayBundleService(primary, secondary)
    }

    private fun startOverlayBundleService(primary: ThemeConfig, secondary: ThemeConfig?) {
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_THEME_JSON, gson.toJson(primary))
            if (secondary != null) {
                putExtra(OverlayService.EXTRA_SECONDARY_THEME_JSON, gson.toJson(secondary))
            }
        }
        startForegroundService(intent)
        onOverlayStarted?.invoke()
        moveTaskToBack(true)
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(intent)
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivity(intent)
        }
    }

    private fun requestStoragePermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = "package:$packageName".toUri()
            }
            startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivity(intent)
        }
    }
}

@Composable
private fun OverlayActiveScreen(
    themeName: String,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(com.emulnk.ui.theme.EmuLnkDimens.spacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.overlay_active),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = com.emulnk.ui.theme.BrandCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = themeName,
            fontSize = 16.sp,
            color = com.emulnk.ui.theme.TextPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.overlay_instructions),
            fontSize = 13.sp,
            color = com.emulnk.ui.theme.TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onExit,
            colors = ButtonDefaults.buttonColors(containerColor = com.emulnk.ui.theme.BrandPurple)
        ) {
            Text(stringResource(R.string.overlay_stop), color = com.emulnk.ui.theme.TextPrimary)
        }
    }
}
