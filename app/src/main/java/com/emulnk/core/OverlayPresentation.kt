package com.emulnk.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.WindowManager
import android.widget.FrameLayout
import com.emulnk.BuildConfig
import com.emulnk.bridge.OverlayBridge
import com.emulnk.model.GameData
import com.emulnk.model.WidgetConfig
import com.emulnk.model.WidgetLayoutState
import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream

/** Secondary display overlay — WindowManager above ExternalPresentation, pass-through in normal mode. */
class OverlayPresentation(
    serviceContext: Context,
    private val display: Display,
    private val themeId: String,
    private val themesRootDir: File,
    private val devMode: Boolean = false,
    private val devUrl: String = ""
) {

    companion object {
        private const val TAG = "OverlayPresentation"
        private val ALPHA_TAG_KEY = "alpha_tag".hashCode()
    }

    private val overlayContext: Context = serviceContext.createWindowContext(
        display,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        null
    )
    private val windowManager: WindowManager =
        overlayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val gson = Gson()
    private lateinit var rootLayout: FrameLayout
    private val widgetWebViews = mutableMapOf<String, WebView>()
    private val widgetContainers = mutableMapOf<String, SecondaryWidgetContainer>()
    private val widgetConfigs = mutableMapOf<String, WidgetConfig>()

    private var latestGameData: GameData? = null
    private var editMode = false
    private var selectedWidgetId: String? = null
    private var scrimView: View? = null
    private var onWidgetSelected: ((String) -> Unit)? = null

    fun show() {
        rootLayout = FrameLayout(overlayContext).apply {
            setBackgroundColor(0x00000000)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            alpha = 1.0f
        }
        windowManager.addView(rootLayout, params)
    }

    fun dismiss() {
        if (::rootLayout.isInitialized) {
            try { windowManager.removeView(rootLayout) } catch (_: Exception) {}
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun addWidget(config: WidgetConfig, layoutState: WidgetLayoutState?) {
        val ctx = overlayContext
        val density = ctx.resources.displayMetrics.density
        val x = layoutState?.x ?: config.defaultX
        val y = layoutState?.y ?: config.defaultY
        val width = layoutState?.width ?: config.defaultWidth
        val height = layoutState?.height ?: config.defaultHeight
        val enabled = layoutState?.enabled ?: true
        val alpha = layoutState?.alpha ?: 1.0f

        val widthPx = (width * density).toInt()
        val heightPx = (height * density).toInt()
        val xPx = (x * density).toInt()
        val yPx = (y * density).toInt()

        val webView = WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            setBackgroundColor(0x00000000)

            if (BuildConfig.DEBUG) {
                WebView.setWebContentsDebuggingEnabled(true)
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    if (BuildConfig.DEBUG) {
                        consoleMessage?.let {
                            Log.d(TAG, "[Secondary:${config.id}] ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                        }
                    }
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    latestGameData?.let { pushDataToSingleWidget(view, it) }
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    if (url.startsWith("https://app.emulink/")) {
                        val fileName = url.replace("https://app.emulink/", "")
                        val themeDir = File(themesRootDir, themeId)
                        val requestedFile = if (fileName.isEmpty() || fileName == "/") "index.html" else fileName
                        val file = File(themeDir, requestedFile).canonicalFile

                        if (!file.canonicalPath.startsWith(themeDir.canonicalPath + File.separator) &&
                            file.canonicalPath != themeDir.canonicalPath
                        ) {
                            if (BuildConfig.DEBUG) {
                                Log.w(TAG, "Path traversal blocked: $fileName")
                            }
                            return null
                        }

                        if (file.exists()) {
                            val mimeType = when (file.extension.lowercase()) {
                                "html" -> "text/html"; "css" -> "text/css"; "js" -> "application/javascript"
                                "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"
                                "svg" -> "image/svg+xml"; "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"
                                else -> "application/octet-stream"
                            }
                            return try {
                                val inputStream = FileInputStream(file)
                                try {
                                    WebResourceResponse(mimeType, "UTF-8", inputStream)
                                } catch (e: Exception) {
                                    inputStream.close()
                                    throw e
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Interception failed for $url", e)
                                null
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }

            loadUrl("https://app.emulink/${config.src}")
        }

        val container = SecondaryWidgetContainer(ctx).apply {
            addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        val params = FrameLayout.LayoutParams(widthPx, heightPx).apply {
            leftMargin = xPx
            topMargin = yPx
        }

        container.tag = enabled
        container.setTag(ALPHA_TAG_KEY, alpha)

        if (!enabled) {
            container.visibility = View.GONE
        } else {
            container.alpha = alpha
        }

        rootLayout.addView(container, params)
        widgetWebViews[config.id] = webView
        widgetContainers[config.id] = container
        widgetConfigs[config.id] = config
    }

    fun addBridge(bridge: OverlayBridge) {
        for ((_, webView) in widgetWebViews) {
            webView.addJavascriptInterface(bridge, "emulink")
        }
    }

    fun removeWidget(id: String) {
        widgetWebViews.remove(id)?.let { webView ->
            webView.stopLoading()
            webView.destroy()
        }
        widgetContainers.remove(id)?.let { container ->
            rootLayout.removeView(container)
        }
        widgetConfigs.remove(id)
    }

    fun pushDataToWidgets(gameData: GameData) {
        latestGameData = gameData
        val jsonData = gson.toJson(gameData)
        val encodedData = android.util.Base64.encodeToString(
            jsonData.toByteArray(),
            android.util.Base64.NO_WRAP
        )
        val js = "if(typeof updateData !== 'undefined') updateData('$encodedData', true)"
        for ((id, webView) in widgetWebViews) {
            val container = widgetContainers[id]
            if (container != null && container.visibility != View.GONE) {
                try {
                    webView.evaluateJavascript(js, null)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Failed to push data to secondary widget: ${e.message}")
                    }
                }
            }
        }
    }

    private fun pushDataToSingleWidget(view: WebView?, gameData: GameData) {
        view ?: return
        val jsonData = gson.toJson(gameData)
        val encodedData = android.util.Base64.encodeToString(
            jsonData.toByteArray(), android.util.Base64.NO_WRAP
        )
        val js = "if(typeof updateData !== 'undefined') updateData('$encodedData', true)"
        try { view.evaluateJavascript(js, null) } catch (_: Exception) {}
    }

    // --- Edit mode ---

    fun enterEditMode(onSelected: (String) -> Unit) {
        if (editMode) return
        editMode = true
        onWidgetSelected = onSelected

        // Remove FLAG_NOT_TOUCHABLE so we can receive touches; add FLAG_NOT_TOUCH_MODAL
        val lp = rootLayout.layoutParams as WindowManager.LayoutParams
        lp.flags = (lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()) or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        windowManager.updateViewLayout(rootLayout, lp)

        // Add semi-transparent scrim behind widgets
        val scrim = View(overlayContext).apply {
            setBackgroundColor(OverlayConstants.EDIT_SCRIM_COLOR.toInt())
        }
        rootLayout.addView(scrim, 0, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        scrimView = scrim

        // Enable touch interception and borders on all containers
        for ((id, container) in widgetContainers) {
            container.interceptAllTouches = true

            // Make disabled widgets visible but dimmed
            if (container.visibility == View.GONE) {
                container.visibility = View.VISIBLE
                container.alpha = 0.3f
            }

            updateContainerEditVisual(id, id == selectedWidgetId)
            setupDragHandling(id, container)
        }
    }

    fun exitEditMode() {
        if (!editMode) return
        editMode = false
        onWidgetSelected = null
        selectedWidgetId = null

        // Restore FLAG_NOT_TOUCHABLE, remove FLAG_NOT_TOUCH_MODAL
        val lp = rootLayout.layoutParams as WindowManager.LayoutParams
        lp.flags = (lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) and
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        windowManager.updateViewLayout(rootLayout, lp)

        // Remove scrim
        scrimView?.let { rootLayout.removeView(it) }
        scrimView = null

        // Disable touch interception and remove borders
        for ((id, container) in widgetContainers) {
            container.interceptAllTouches = false
            container.background = null
            container.setOnTouchListener(null)

            // Restore visibility based on enabled state
            val isEnabled = container.tag as? Boolean ?: true
            if (!isEnabled) {
                container.visibility = View.GONE
            } else {
                val savedAlpha = container.getTag(ALPHA_TAG_KEY) as? Float ?: 1.0f
                container.alpha = savedAlpha
            }
        }
    }

    fun selectWidget(id: String) {
        val prevId = selectedWidgetId
        selectedWidgetId = if (id.isEmpty()) null else id
        if (prevId != null) updateContainerEditVisual(prevId, false)
        if (id.isNotEmpty()) updateContainerEditVisual(id, true)
    }

    fun toggleWidget(id: String) {
        val container = widgetContainers[id] ?: return
        val isEnabled = container.tag as? Boolean ?: true
        val newEnabled = !isEnabled
        container.tag = newEnabled

        if (newEnabled) {
            val savedAlpha = container.getTag(ALPHA_TAG_KEY) as? Float ?: 1.0f
            container.alpha = savedAlpha
        } else {
            container.alpha = 0.3f
        }
        updateContainerEditVisual(id, id == selectedWidgetId)
    }

    fun setWidgetAlpha(id: String, alpha: Float) {
        val container = widgetContainers[id] ?: return
        container.setTag(ALPHA_TAG_KEY, alpha)
        val isEnabled = container.tag as? Boolean ?: true
        if (isEnabled) container.alpha = alpha
    }

    fun getWidgetStates(density: Float): Map<String, WidgetLayoutState> {
        val states = mutableMapOf<String, WidgetLayoutState>()
        for ((id, container) in widgetContainers) {
            val lp = container.layoutParams as? FrameLayout.LayoutParams ?: continue
            val isEnabled = container.tag as? Boolean ?: true
            val alpha = container.getTag(ALPHA_TAG_KEY) as? Float ?: 1.0f
            states[id] = WidgetLayoutState(
                x = (lp.leftMargin / density).toInt(),
                y = (lp.topMargin / density).toInt(),
                width = (lp.width / density).toInt(),
                height = (lp.height / density).toInt(),
                enabled = isEnabled,
                alpha = alpha
            )
        }
        return states
    }

    fun resetWidgets() {
        val density = overlayContext.resources.displayMetrics.density
        for ((id, container) in widgetContainers) {
            val config = widgetConfigs[id] ?: continue
            val lp = container.layoutParams as? FrameLayout.LayoutParams ?: continue
            lp.leftMargin = (config.defaultX * density).toInt()
            lp.topMargin = (config.defaultY * density).toInt()
            lp.width = (config.defaultWidth * density).toInt()
            lp.height = (config.defaultHeight * density).toInt()
            container.layoutParams = lp

            container.tag = true
            container.setTag(ALPHA_TAG_KEY, 1.0f)
            container.alpha = 1.0f
            container.visibility = View.VISIBLE
        }

        selectedWidgetId = widgetContainers.keys.firstOrNull()
        for ((id, _) in widgetContainers) {
            updateContainerEditVisual(id, id == selectedWidgetId)
        }
    }

    // --- Internals ---

    private fun updateContainerEditVisual(id: String, isSelected: Boolean) {
        val container = widgetContainers[id] ?: return
        val density = overlayContext.resources.displayMetrics.density
        val radiusPx = OverlayConstants.EDIT_BORDER_RADIUS_DP * density
        val isEnabled = container.tag as? Boolean ?: true

        val border = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(0x00000000)
            cornerRadius = radiusPx

            if (!isEnabled) {
                setStroke((OverlayConstants.EDIT_BORDER_NORMAL_WIDTH_DP * density).toInt(), 0xFFFF5252.toInt())
            } else if (isSelected) {
                setStroke((OverlayConstants.EDIT_BORDER_SELECTED_WIDTH_DP * density).toInt(), 0xFF00E5FF.toInt())
            } else {
                setStroke((OverlayConstants.EDIT_BORDER_NORMAL_WIDTH_DP * density).toInt(), 0xFF2A2650.toInt())
            }
        }
        container.background = border
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragHandling(id: String, container: SecondaryWidgetContainer) {
        val config = widgetConfigs[id] ?: return
        val density = overlayContext.resources.displayMetrics.density
        val minWidthPx = (config.minWidth * density).toInt()
        val minHeightPx = (config.minHeight * density).toInt()

        val displayMetrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        var initialLeftMargin = 0
        var initialTopMargin = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        val scaleDetector = ScaleGestureDetector(overlayContext, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!config.resizable) return false
                val factor = detector.scaleFactor
                val lp = container.layoutParams as FrameLayout.LayoutParams
                lp.width = (lp.width * factor).toInt().coerceIn(minWidthPx, screenWidth)
                lp.height = (lp.height * factor).toInt().coerceIn(minHeightPx, screenHeight)
                // Clamp position
                lp.leftMargin = lp.leftMargin.coerceIn(0, maxOf(0, screenWidth - lp.width))
                lp.topMargin = lp.topMargin.coerceIn(0, maxOf(0, screenHeight - lp.height))
                container.layoutParams = lp
                return true
            }
        })

        container.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (scaleDetector.isInProgress) return@setOnTouchListener true

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val lp = container.layoutParams as FrameLayout.LayoutParams
                    initialLeftMargin = lp.leftMargin
                    initialTopMargin = lp.topMargin
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount > 1) return@setOnTouchListener true
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (kotlin.math.abs(dx) > OverlayConstants.DRAG_THRESHOLD_PX || kotlin.math.abs(dy) > OverlayConstants.DRAG_THRESHOLD_PX)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val lp = container.layoutParams as FrameLayout.LayoutParams
                        lp.leftMargin = (initialLeftMargin + dx.toInt()).coerceIn(0, maxOf(0, screenWidth - lp.width))
                        lp.topMargin = (initialTopMargin + dy.toInt()).coerceIn(0, maxOf(0, screenHeight - lp.height))
                        container.layoutParams = lp
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val wasAlreadySelected = (selectedWidgetId == id)

                    // Update selection
                    val prevId = selectedWidgetId
                    selectedWidgetId = id
                    if (prevId != null && prevId != id) {
                        updateContainerEditVisual(prevId, false)
                    }
                    updateContainerEditVisual(id, true)

                    // Notify service of selection
                    onWidgetSelected?.invoke(id)

                    // Double-tap on selected: toggle enabled
                    if (!isDragging && wasAlreadySelected) {
                        toggleWidget(id)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private var destroyed = false

    fun destroyAll() {
        if (destroyed) return
        destroyed = true
        editMode = false
        onWidgetSelected = null
        selectedWidgetId = null
        scrimView = null
        for ((_, webView) in widgetWebViews) {
            webView.stopLoading()
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        rootLayout.removeAllViews()
        widgetWebViews.clear()
        widgetContainers.clear()
        widgetConfigs.clear()
    }

    private class SecondaryWidgetContainer(context: Context) : FrameLayout(context) {
        var interceptAllTouches = false
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            return interceptAllTouches || super.onInterceptTouchEvent(ev)
        }
    }

}
