package com.lyx.copy.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object FloatingServiceController {

    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, FloatingService::class.java)
        )
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, FloatingService::class.java))
    }

    fun restart(context: Context) {
        stop(context)
        start(context)
    }

    @Suppress("DEPRECATION")
    fun isRunning(context: Context): Boolean {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return false
        return activityManager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == FloatingService::class.java.name }
    }
}
