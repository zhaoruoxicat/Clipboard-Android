package com.lyx.copy.data

import android.content.Context

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSettings(): AppSettings {
        val serverUrl = prefs.getString(KEY_SERVER_URL, "").orEmpty()
        val apiToken = prefs.getString(KEY_API_TOKEN, "").orEmpty()
        return AppSettings(
            serverUrl = serverUrl,
            apiToken = apiToken,
            autoSync = prefs.getBoolean(KEY_AUTO_SYNC, false)
        )
    }

    fun saveSettings(serverUrl: String, apiToken: String, autoSync: Boolean) {
        prefs.edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_API_TOKEN, apiToken)
            .putBoolean(KEY_AUTO_SYNC, autoSync)
            .apply()
    }

    fun isConfigured(): Boolean {
        return getSettings().isConfigured
    }

    fun saveOverlayPosition(x: Int, y: Int) {
        prefs.edit()
            .putInt(KEY_OVERLAY_X, x)
            .putInt(KEY_OVERLAY_Y, y)
            .apply()
    }

    fun resetOverlayPosition() {
        val defaultPosition = defaultOverlayPosition()
        saveOverlayPosition(defaultPosition.x, defaultPosition.y)
    }

    fun getOverlayPosition(): OverlayPosition {
        val defaultPosition = defaultOverlayPosition()
        return OverlayPosition(
            x = prefs.getInt(KEY_OVERLAY_X, defaultPosition.x),
            y = prefs.getInt(KEY_OVERLAY_Y, defaultPosition.y)
        )
    }

    private fun defaultOverlayPosition(): OverlayPosition {
        return OverlayPosition(
            x = DEFAULT_OVERLAY_X,
            y = DEFAULT_OVERLAY_Y
        )
    }

    companion object {
        private const val PREFS_NAME = "copy_settings"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_TOKEN = "api_token"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_OVERLAY_X = "overlay_x"
        private const val KEY_OVERLAY_Y = "overlay_y"
        private const val DEFAULT_OVERLAY_X = 24
        private const val DEFAULT_OVERLAY_Y = 160
    }
}

data class AppSettings(
    val serverUrl: String,
    val apiToken: String,
    val autoSync: Boolean
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && apiToken.isNotBlank()
}

data class OverlayPosition(
    val x: Int,
    val y: Int
)
