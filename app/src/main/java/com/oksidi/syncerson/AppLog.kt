package com.oksidi.syncerson

object AppLog {
    private const val MAX_LINES = 200
    private val buffer = StringBuilder()

    @Synchronized
    fun append(tag: String, level: String, message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val line = "$timestamp $level/$tag: $message"

        if (buffer.isNotEmpty()) buffer.append('\n')
        buffer.append(line)

        // Trim old lines
        val newlineCount = buffer.count { it == '\n' }
        if (newlineCount >= MAX_LINES) {
            // Remove oldest line
            val idx = buffer.indexOf('\n') + 1
            buffer.delete(0, idx)
        }
    }

    @Synchronized
    fun getText(): String = buffer.toString()
}
