package com.lyx.copy.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import com.lyx.copy.R
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageUtils {

    fun readImageAsBase64(
        context: Context,
        uri: Uri,
        fallbackMimeType: String = "image/png"
    ): EncodedImage {
        val mimeType = context.contentResolver.getType(uri) ?: fallbackMimeType
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: throw IOException(context.getString(R.string.toast_image_read_failed))

        return EncodedImage(
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            mimeType = mimeType
        )
    }

    fun decodeBase64(content: String): ByteArray {
        return Base64.decode(content, Base64.DEFAULT)
    }

    fun saveImageToGallery(
        context: Context,
        imageBytes: ByteArray,
        mimeType: String
    ): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val extension = when (mimeType.lowercase(Locale.US)) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "cloud_clipboard_$timestamp.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/ClipboardSync"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException(context.getString(R.string.toast_image_create_failed))

        try {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(imageBytes)
            } ?: throw IOException(context.getString(R.string.toast_image_output_failed))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            return uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}

data class EncodedImage(
    val base64: String,
    val mimeType: String
)
