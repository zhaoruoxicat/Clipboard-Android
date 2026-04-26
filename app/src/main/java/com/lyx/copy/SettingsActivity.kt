package com.lyx.copy

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.lyx.copy.data.SettingsRepository
import com.lyx.copy.service.FloatingServiceController
import com.lyx.copy.ui.theme.CopyTheme

class SettingsActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)
        enableEdgeToEdge()

        val currentSettings = settingsRepository.getSettings()

        setContent {
            CopyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        initialServerUrl = currentSettings.serverUrl,
                        initialToken = currentSettings.apiToken,
                        initialAutoSync = currentSettings.autoSync,
                        onSave = { serverUrl, token, autoSync ->
                            val trimmedUrl = serverUrl.trim().trimEnd('/')
                            val trimmedToken = token.trim()
                            when {
                                trimmedUrl.isBlank() || trimmedToken.isBlank() -> {
                                    Toast.makeText(
                                        this,
                                        getString(R.string.field_required),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                trimmedUrl.toHttpUrlOrNull() == null -> {
                                    Toast.makeText(
                                        this,
                                        getString(R.string.invalid_server_url),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                else -> {
                                    settingsRepository.saveSettings(trimmedUrl, trimmedToken, autoSync)
                                    FloatingServiceController.start(applicationContext)
                                    Toast.makeText(
                                        this,
                                        getString(R.string.settings_saved),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    finish()
                                }
                            }
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    initialServerUrl: String,
    initialToken: String,
    initialAutoSync: Boolean,
    onSave: (String, String, Boolean) -> Unit,
    onClose: () -> Unit
) {
    var serverUrl by rememberSaveable { mutableStateOf(initialServerUrl) }
    var apiToken by rememberSaveable { mutableStateOf(initialToken) }
    var autoSync by rememberSaveable { mutableStateOf(initialAutoSync) }

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
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.server_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            OutlinedTextField(
                value = apiToken,
                onValueChange = { apiToken = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.api_token)) },
                singleLine = true
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.auto_sync),
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = autoSync,
                    onCheckedChange = { autoSync = it }
                )
            }

            Button(
                onClick = { onSave(serverUrl, apiToken, autoSync) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save_and_start))
            }

            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.close))
            }
        }
    }
}
