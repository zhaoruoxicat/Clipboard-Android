package com.lyx.copy.util

import android.content.Context
import com.lyx.copy.R
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorMessageResolver {

    fun resolve(context: Context, throwable: Throwable): String {
        val message = throwable.message.orEmpty()
        return when (throwable) {
            is UnknownHostException, is ConnectException -> {
                context.getString(R.string.toast_network_unavailable)
            }

            is SocketTimeoutException -> {
                context.getString(R.string.toast_request_timeout)
            }

            else -> {
                if (message.isBlank()) {
                    context.getString(R.string.toast_server_error)
                } else {
                    message
                }
            }
        }
    }
}
