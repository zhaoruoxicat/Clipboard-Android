package com.lyx.copy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.lyx.copy.ClipboardUploadActivity
import com.lyx.copy.MainActivity
import com.lyx.copy.R
import com.lyx.copy.data.SettingsRepository
import com.lyx.copy.network.ApiClient
import com.lyx.copy.network.ClipboardPayload
import com.lyx.copy.util.ClipboardHelper
import com.lyx.copy.util.ClipboardItem
import com.lyx.copy.util.ErrorMessageResolver
import com.lyx.copy.util.ImageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val apiClient = ApiClient()

    private lateinit var windowManager: WindowManager
    private lateinit var settingsRepository: SettingsRepository
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var suppressClipboardChangesUntil = 0L
    private var lastUploadedSignature: String? = null
    private var uploadJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerClipboardListenerIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = settingsRepository.getSettings()
        if (!settings.isConfigured) {
            showToast(getString(R.string.settings_required))
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            showToast(getString(R.string.status_overlay_missing))
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlayView == null) {
            attachOverlay()
        }
        registerClipboardListenerIfNeeded()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterClipboardListener()
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun attachOverlay() {
        val position = settingsRepository.getOverlayPosition()
        val cardWidth = dp(148)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.x
            y = position.y
        }

        val dragStrip = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                cardWidth - dp(20),
                dp(18)
            )
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.argb(80, 255, 255, 255))
            }
            setOnTouchListener(DragTouchListener(params))
        }

        val topRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                cardWidth - dp(20),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(createButton("\u2699", "设置", small = true) { openMainUi() })
            addView(createGap(horizontal = true, size = 6))
            addView(createButton("\u2715", "关闭", small = true) { stopSelf() })
        }

        val bottomRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                cardWidth - dp(20),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(createButton("\uD83D\uDCE4", "上传") { launchClipboardUpload() })
            addView(createGap(horizontal = true, size = 8))
            addView(createButton("\uD83D\uDCE5", "下载") { syncClipboardFromCloud() })
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            minimumWidth = cardWidth
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.argb(210, 18, 24, 33))
                setStroke(dp(1), Color.argb(76, 255, 255, 255))
            }
            addView(dragStrip)
            addView(createGap(horizontal = false, size = 8))
            addView(topRow)
            addView(createGap(horizontal = false, size = 10))
            addView(bottomRow)
        }

        windowManager.addView(container, params)
        overlayView = container
        overlayParams = params
    }

    private fun createGap(horizontal: Boolean, size: Int): View {
        return View(this).apply {
            layoutParams = if (horizontal) {
                LinearLayout.LayoutParams(dp(size), 1)
            } else {
                LinearLayout.LayoutParams(1, dp(size))
            }
        }
    }

    private fun createButton(
        symbol: String,
        description: String,
        small: Boolean = false,
        onClick: () -> Unit
    ): View {
        val buttonSize = if (small) 32 else 52
        val textSize = if (small) 16f else 20f
        val radius = if (small) 10 else 14
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(buttonSize), dp(buttonSize))
            gravity = Gravity.CENTER
            text = symbol
            this.textSize = textSize
            setTextColor(Color.WHITE)
            contentDescription = description
            background = GradientDrawable().apply {
                cornerRadius = dp(radius).toFloat()
                setColor(Color.argb(255, 33, 44, 60))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun launchClipboardUpload() {
        removeOverlay()
        val intent = Intent(this, ClipboardUploadActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching {
                windowManager.removeView(view)
            }
            overlayView = null
            overlayParams = null
        }
    }

    private fun syncClipboardToCloud(manual: Boolean) {
        if (uploadJob?.isActive == true) {
            return
        }
        val settings = settingsRepository.getSettings()
        if (!settings.isConfigured) {
            showToast(getString(R.string.settings_required))
            return
        }

        uploadJob = scope.launch {
            runCatching {
                val clipboardItem = ClipboardHelper.readPrimaryClip(this@FloatingService)
                val signature = clipboardItem.signature()
                if (!manual && signature == lastUploadedSignature) {
                    return@runCatching null
                }

                val payload = when (clipboardItem) {
                    is ClipboardItem.Text -> ClipboardPayload(
                        contentType = "text",
                        content = clipboardItem.value,
                        mimeType = "text/plain"
                    )

                    is ClipboardItem.Image -> {
                        val encodedImage = ImageUtils.readImageAsBase64(
                            context = this@FloatingService,
                            uri = clipboardItem.uri,
                            fallbackMimeType = clipboardItem.mimeType
                        )
                        ClipboardPayload(
                            contentType = "image",
                            content = encodedImage.base64,
                            mimeType = encodedImage.mimeType
                        )
                    }
                }

                apiClient.uploadClipboard(
                    serverUrl = settings.serverUrl,
                    apiToken = settings.apiToken,
                    payload = payload
                )
                lastUploadedSignature = signature
                payload.contentType
            }.onSuccess { contentType ->
                if (manual && contentType != null) {
                    val typeName = if (contentType == "image") {
                        getString(R.string.toast_upload_image)
                    } else {
                        getString(R.string.toast_upload_text)
                    }
                    showToast(getString(R.string.toast_upload_success, typeName))
                }
            }.onFailure { error ->
                showToast(ErrorMessageResolver.resolve(this@FloatingService, error))
            }
        }
    }

    private fun syncClipboardFromCloud() {
        val settings = settingsRepository.getSettings()
        if (!settings.isConfigured) {
            showToast(getString(R.string.settings_required))
            return
        }

        scope.launch {
            runCatching {
                val payload = apiClient.fetchClipboard(
                    serverUrl = settings.serverUrl,
                    apiToken = settings.apiToken
                )
                suppressClipboardChangesUntil = SystemClock.elapsedRealtime() + 1_500L
                when (payload.contentType) {
                    "image" -> {
                        val bytes = ImageUtils.decodeBase64(payload.content)
                        val uri = ImageUtils.saveImageToGallery(
                            context = this@FloatingService,
                            imageBytes = bytes,
                            mimeType = payload.mimeType ?: "image/png"
                        )
                        ClipboardHelper.setPrimaryImage(this@FloatingService, uri)
                        lastUploadedSignature = "image:${payload.content.hashCode()}"
                        getString(R.string.toast_download_image_success)
                    }

                    else -> {
                        ClipboardHelper.setPrimaryText(this@FloatingService, payload.content)
                        lastUploadedSignature = "text:${payload.content.hashCode()}"
                        getString(R.string.toast_download_text_success)
                    }
                }
            }.onSuccess { message ->
                showToast(message)
            }.onFailure { error ->
                showToast(ErrorMessageResolver.resolve(this@FloatingService, error))
            }
        }
    }

    private fun openMainUi() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_MAIN_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun registerClipboardListenerIfNeeded() {
        if (clipboardListener != null) {
            return
        }
        val clipboardManager = getSystemService(ClipboardManager::class.java) ?: return
        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener listener@{
            if (!settingsRepository.getSettings().autoSync) {
                return@listener
            }
            if (SystemClock.elapsedRealtime() < suppressClipboardChangesUntil) {
                return@listener
            }
            syncClipboardToCloud(manual = false)
        }
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }

    private fun unregisterClipboardListener() {
        val clipboardManager = getSystemService(ClipboardManager::class.java) ?: return
        clipboardListener?.let { clipboardManager.removePrimaryClipChangedListener(it) }
        clipboardListener = null
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_MAIN_UI, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.service_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }

    private fun ClipboardItem.signature(): String {
        return when (this) {
            is ClipboardItem.Text -> "text:${value.hashCode()}"
            is ClipboardItem.Image -> "image:$uri"
        }
    }

    private inner class DragTouchListener(
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var downRawX = 0f
        private var downRawY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downRawX).toInt()
                    params.y = startY + (event.rawY - downRawY).toInt()
                    overlayParams = params
                    overlayView?.let { windowManager.updateViewLayout(it, params) }
                    settingsRepository.saveOverlayPosition(params.x, params.y)
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val CHANNEL_ID = "clipboard_sync_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
