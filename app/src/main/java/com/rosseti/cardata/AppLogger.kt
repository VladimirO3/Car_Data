package com.rosseti.cardata

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Система логирования, которая пишет данные в файл и отправляет в Firebase.
 */
object AppLogger {
    private const val TAG = "TrackLitLog"
    private const val LOG_FILE_NAME = "app_logs.txt"

    fun d(context: Context, message: String) {
        val formattedMessage = formatMessage("DEBUG", message)
        Log.d(TAG, formattedMessage)
        writeToInternalFile(context, formattedMessage)
        // FirebaseCrashlytics.getInstance().log(formattedMessage) // Optional: avoid logging every GPS point to Firebase
    }

    fun e(context: Context, message: String, throwable: Throwable? = null) {
        val formattedMessage = formatMessage("ERROR", message + (throwable?.let { " | ${it.message}" } ?: ""))
        Log.e(TAG, formattedMessage, throwable)
        writeToInternalFile(context, formattedMessage)
        
        throwable?.let {
            FirebaseCrashlytics.getInstance().recordException(it)
        }
        FirebaseCrashlytics.getInstance().log(formattedMessage)
    }

    private fun formatMessage(level: String, message: String): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        return "[$timestamp] [$level] $message\n"
    }

    private fun writeToInternalFile(context: Context, message: String) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            file.appendText(message)
            
            // Ограничиваем размер файла 1МБ, чтобы не забивать память
            if (file.length() > 1 * 1024 * 1024) {
                val lines = file.readLines()
                if (lines.size > 500) {
                    file.writeText(lines.takeLast(500).joinToString("\n") + "\n")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to file", e)
        }
    }

    fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }
}
