package com.lyx.copy.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import com.lyx.copy.R

object ClipboardHelper {

    fun readPrimaryClip(context: Context): ClipboardItem {
        val clipboardManager = context.getSystemService(ClipboardManager::class.java)
            ?: throw ClipboardException(context.getString(R.string.toast_clipboard_service_unavailable))
        val clipData = clipboardManager.primaryClip
            ?: throw ClipboardException(context.getString(R.string.toast_clipboard_empty))
        if (clipData.itemCount == 0) {
            throw ClipboardException(context.getString(R.string.toast_clipboard_empty))
        }

        val item = clipData.getItemAt(0)
        val uri = item.uri
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
                ?: clipData.description.getMimeType(0)
                ?: "image/png"
            if (mimeType.startsWith("image/")) {
                return ClipboardItem.Image(uri, mimeType)
            }
        }

        val text = item.text?.toString().orEmpty().ifBlank {
            item.coerceToText(context)?.toString().orEmpty()
        }
        if (text.isNotBlank()) {
            return ClipboardItem.Text(text)
        }

        throw ClipboardException(context.getString(R.string.toast_clipboard_not_supported))
    }

    fun setPrimaryText(context: Context, text: String) {
        val clipboardManager = context.getSystemService(ClipboardManager::class.java)
            ?: throw ClipboardException(context.getString(R.string.toast_clipboard_service_unavailable))
        clipboardManager.setPrimaryClip(ClipData.newPlainText("云剪切板", text))
    }

    fun setPrimaryImage(context: Context, uri: Uri) {
        val clipboardManager = context.getSystemService(ClipboardManager::class.java)
            ?: throw ClipboardException(context.getString(R.string.toast_clipboard_service_unavailable))
        clipboardManager.setPrimaryClip(
            ClipData.newUri(context.contentResolver, "云剪切板", uri)
        )
    }
}

sealed interface ClipboardItem {
    data class Text(val value: String) : ClipboardItem
    data class Image(val uri: Uri, val mimeType: String) : ClipboardItem
}

class ClipboardException(message: String) : IllegalStateException(message)
