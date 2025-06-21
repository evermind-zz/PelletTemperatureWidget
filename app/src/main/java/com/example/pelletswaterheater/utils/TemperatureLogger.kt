package com.example.pelletswaterheater.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Interface for logging temperature data to CSV files.
 */
interface TemperatureLogger {
    /**
     * Logs a map of converted values to a CSV file.
     *
     * @param values Map of field keys to converted values.
     */
    fun logValues(values: Map<String, ConvertedValue>)

    /**
     * Retrieves the URI of the latest log file for sharing.
     *
     * @param context The application context.
     * @return The URI of the latest log file or null if none exists.
     */
    fun getLatestLogUri(context: Context): Uri?
}

/**
 * Implementation of [TemperatureLogger] that logs to CSV files with FileProvider support.
 */
class TemperatureLoggerImpl(
    private val contextProvider: ContextProvider,
    private val logger: ILogger = Logger
) : TemperatureLogger {
    private val TAG = TemperatureLoggerImpl::class.java.simpleName
    private val maxFileSizeBytes = 5 * 1024 * 1024 // 5MB
    private val csvHeader = "timestamp;section;key;logical;name;unit\n"

    override fun logValues(values: Map<String, ConvertedValue>) {
        try {
            val context = contextProvider.getContext()
            val logDir = File(context.filesDir, "logs")
            if (!logDir.exists()) logDir.mkdirs()

            val logFile = getCurrentLogFile(logDir)
            if (!logFile.exists()) {
                logFile.writeText(csvHeader, Charsets.UTF_8)
            }

            // Check file size and rotate if needed
            if (logFile.length() >= maxFileSizeBytes) {
                rotateLogFile(logDir, logFile)
            }

            // Write values
            val timestamp = isoTimestamp()
            val csvLines = values.entries.joinToString("") { (key, value) ->
                val section = key.substringBeforeLast(".")
                val fieldKey = key.substringAfterLast(".")
                val unit = value.unit?.replace(";", ",") ?: "" // Escape semicolon
                "$timestamp;$section;$fieldKey;${value.logical};${value.name};$unit\n"
            }
            logFile.appendText(csvLines, Charsets.UTF_8)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to log values: $e", e)
        }
    }

    fun isoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    override fun getLatestLogUri(context: Context): Uri? {
        return try {
            val logDir = File(context.filesDir, "logs")
            val latestFile = logDir.listFiles()?.filter { it.name.endsWith(".csv") }
                ?.maxByOrNull { it.lastModified() }
            if (latestFile != null && latestFile.exists()) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    latestFile
                )
            } else {
                null
            }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to get latest log URI: $e", e)
            null
        }
    }

    private fun getCurrentLogFile(logDir: File): File {
        val timestamp = isoTimestamp()
        return File(logDir, "temp_log_$timestamp.csv")
    }

    private fun rotateLogFile(logDir: File, currentFile: File) {
        try {
            val index = logDir.listFiles()
                ?.filter { it.name.startsWith("temp_log_") && it.name.endsWith(".csv") }
                ?.map { it.name.substringAfterLast("_").substringBefore(".csv").toIntOrNull() ?: 0 }
                ?.maxOrNull() ?: 0
            val newFile = File(logDir, "temp_log_${index + 1}.csv")
            currentFile.renameTo(newFile)
            currentFile.writeText(csvHeader, Charsets.UTF_8)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to rotate log file: $e", e)
        }
    }
}