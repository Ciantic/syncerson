package com.oksidi.syncerson

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

object AppLog {
    private const val MAX_LINES = 500
    private val buffer = ConcurrentLinkedQueue<String>()
    private var logFile: File? = null

    fun init(context: Context) {
        if (logFile != null) return // already initialized
        logFile = File(context.filesDir, "syncerson.log")
        loadFromFile()
    }

    private fun loadFromFile() {
        val file = logFile ?: return
        if (!file.exists()) return
        val lines = try {
            file.readLines().takeLast(MAX_LINES)
        } catch (_: Exception) {
            return
        }
        buffer.clear()
        buffer.addAll(lines)
    }

    @Synchronized
    fun append(tag: String, level: String, message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val line = "$timestamp $level/$tag: $message"

        buffer.add(line)
        if (buffer.size > MAX_LINES) {
            buffer.poll() // remove oldest
        }

        // Append to file and trim
        try {
            val file = logFile ?: return
            file.appendText(line + "\n")

            // Trim file every 50 writes to avoid growing unbounded
            if (buffer.size % 50 == 0) {
                val trimmed = file.readLines().takeLast(MAX_LINES)
                file.writeText(trimmed.joinToString("\n") + "\n")
            }
        } catch (_: Exception) {
            // silently ignore file write errors
        }
    }

    @Synchronized
    fun getText(): String = buffer.joinToString("\n")
}
