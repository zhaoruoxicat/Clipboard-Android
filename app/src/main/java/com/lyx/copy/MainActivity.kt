package com.lyx.copy

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lyx.copy.data.SettingsRepository
import com.lyx.copy.service.FloatingServiceController
import com.lyx.copy.ui.theme.CopyTheme

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private var uiState by mutableStateOf(MainUiState())

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshUiState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)
        refreshUiState()

        if (shouldLaunchOverlayOnly()) {
            FloatingServiceController.start(applicationContext)
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            CopyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        uiState = uiState,
                        onGrantOverlay = { openOverlayPermissionScreen() },
                        onGrantNotifications = { requestNotificationPermissionIfNeeded() },
                        onStartFloatingWindow = { FloatingServiceController.start(applicationContext) },
                        onRestartFloatingWindow = { restartFloatingWindow() },
                        onResetFloatingWindowPosition = { resetFloatingWindowPosition() },
                        onStopFloatingWindow = { FloatingServiceController.stop(applicationContext) },
                        onOpenSettings = { openSettings() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
        if (!settingsRepository.isConfigured()) {
            openSettings()
            return
        }
        if (uiState.overlayGranted) {
            FloatingServiceController.start(applicationContext)
        }
    }

    private fun shouldLaunchOverlayOnly(): Boolean {
        return settingsRepository.isConfigured() &&
            Settings.canDrawOverlays(this) &&
            !intent.getBooleanExtra(EXTRA_OPEN_MAIN_UI, false)
    }

    private fun refreshUiState() {
        val settings = settingsRepository.getSettings()
        uiState = MainUiState(
            configured = settings.isConfigured,
            serverUrl = settings.serverUrl,
            autoSync = settings.autoSync,
            overlayGranted = Settings.canDrawOverlays(this),
            notificationGranted = isNotificationPermissionGranted()
        )
    }

    private fun openOverlayPermissionScreen() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun restartFloatingWindow() {
        FloatingServiceController.restart(applicationContext)
    }

    private fun resetFloatingWindowPosition() {
        settingsRepository.resetOverlayPosition()
        val toastMessage = if (FloatingServiceController.isRunning(applicationContext)) {
            restartFloatingWindow()
            getString(R.string.toast_overlay_position_reset)
        } else {
            getString(R.string.toast_overlay_position_reset_pending)
        }
        Toast.makeText(applicationContext, toastMessage, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_OPEN_MAIN_UI = "open_main_ui"
    }
}

data class MainUiState(
    val configured: Boolean = false,
    val serverUrl: String = "",
    val autoSync: Boolean = false,
    val overlayGranted: Boolean = false,
    val notificationGranted: Boolean = false
)

@Composable
private fun MainScreen(
    uiState: MainUiState,
    onGrantOverlay: () -> Unit,
    onGrantNotifications: () -> Unit,
    onStartFloatingWindow: () -> Unit,
    onRestartFloatingWindow: () -> Unit,
    onResetFloatingWindowPosition: () -> Unit,
    onStopFloatingWindow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.main_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.main_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            StatusCard(
                title = stringResource(
                    if (uiState.configured) R.string.status_configured
                    else R.string.status_missing
                ),
                body = if (uiState.configured) {
                    buildString {
                        append(stringResource(R.string.configured_server))
                        append(": ")
                        append(uiState.serverUrl)
                    }
                } else {
                    stringResource(R.string.settings_required)
                }
            )

            StatusCard(
                title = stringResource(
                    if (uiState.overlayGranted) R.string.status_overlay_granted
                    else R.string.status_overlay_missing
                ),
                body = stringResource(
                    if (uiState.notificationGranted) R.string.status_notification_granted
                    else R.string.status_notification_missing
                )
            )

            StatusCard(
                title = stringResource(R.string.auto_sync),
                body = stringResource(
                    if (uiState.autoSync) R.string.auto_sync_enabled
                    else R.string.auto_sync_disabled
                )
            )

            StatusCard(
                title = stringResource(R.string.open_settings),
                body = stringResource(R.string.advanced_settings_hint)
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!uiState.overlayGranted) {
                    Button(
                        onClick = onGrantOverlay,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.grant_overlay_permission))
                    }
                }

                if (!uiState.notificationGranted) {
                    OutlinedButton(
                        onClick = onGrantNotifications,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.grant_notification_permission))
                    }
                }

                Button(
                    onClick = onStartFloatingWindow,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.configured && uiState.overlayGranted
                ) {
                    Text(stringResource(R.string.start_floating_window))
                }

                OutlinedButton(
                    onClick = onRestartFloatingWindow,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.configured && uiState.overlayGranted
                ) {
                    Text(stringResource(R.string.restart_floating_window))
                }

                OutlinedButton(
                    onClick = onResetFloatingWindowPosition,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.reset_floating_window_position))
                }

                OutlinedButton(
                    onClick = onStopFloatingWindow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.stop_floating_window))
                }

                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.open_settings))
                }
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    CopyTheme {
        MainScreen(
            uiState = MainUiState(
                configured = true,
                serverUrl = "https://example.com",
                autoSync = true,
                overlayGranted = true,
                notificationGranted = true
            ),
            onGrantOverlay = {},
            onGrantNotifications = {},
            onStartFloatingWindow = {},
            onRestartFloatingWindow = {},
            onResetFloatingWindowPosition = {},
            onStopFloatingWindow = {},
            onOpenSettings = {}
        )
    }
}
