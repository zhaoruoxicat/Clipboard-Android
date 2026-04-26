package com.lyx.copy

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.lyx.copy.data.SettingsRepository
import com.lyx.copy.network.ApiClient
import com.lyx.copy.network.ClipboardPayload
import com.lyx.copy.service.FloatingServiceController
import com.lyx.copy.util.ClipboardHelper
import com.lyx.copy.util.ClipboardItem
import com.lyx.copy.util.ErrorMessageResolver
import com.lyx.copy.util.ImageUtils
import com.lyx.copy.ui.theme.CopyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClipboardUploadActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private val apiClient = ApiClient()
    private var started = false
    private var uploadFinished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)
        setContent {
            CopyTheme {
                UploadProxyScreen()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || started) {
            return
        }
        started = true

        val settings = settingsRepository.getSettings()
        if (!settings.isConfigured) {
            Toast.makeText(this, getString(R.string.settings_required), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Toast.makeText(this, getString(R.string.toast_clipboard_prepare), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            delay(250)
            runCatching {
                Toast.makeText(
                    this@ClipboardUploadActivity,
                    getString(R.string.toast_clipboard_uploading),
                    Toast.LENGTH_SHORT
                ).show()
                val clipboardItem = ClipboardHelper.readPrimaryClip(this@ClipboardUploadActivity)
                val payload = when (clipboardItem) {
                    is ClipboardItem.Text -> ClipboardPayload(
                        contentType = "text",
                        content = clipboardItem.value,
                        mimeType = "text/plain"
                    )

                    is ClipboardItem.Image -> {
                        val encodedImage = ImageUtils.readImageAsBase64(
                            context = this@ClipboardUploadActivity,
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
                payload.contentType
            }.onSuccess { type ->
                val typeName = if (type == "image") {
                    getString(R.string.toast_upload_image)
                } else {
                    getString(R.string.toast_upload_text)
                }
                Toast.makeText(
                    this@ClipboardUploadActivity,
                    getString(R.string.toast_upload_success, typeName),
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    this@ClipboardUploadActivity,
                    ErrorMessageResolver.resolve(this@ClipboardUploadActivity, error),
                    Toast.LENGTH_SHORT
                ).show()
            }
            restoreOverlayAndFinish()
        }
    }

    override fun onDestroy() {
        if (!uploadFinished) {
            FloatingServiceController.start(applicationContext)
        }
        super.onDestroy()
    }

    private fun restoreOverlayAndFinish() {
        uploadFinished = true
        FloatingServiceController.start(applicationContext)
        finish()
    }
}

@Composable
private fun UploadProxyScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.toast_clipboard_prepare),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
