package com.pixelrender.app.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log terpusat. Semua deteksi dan (nanti) semua perubahan harus lewat sini,
 * supaya user bisa melihat persis apa yang dilakukan aplikasi.
 */
object Logger {

    enum class Level { INFO, OK, WARN, ERROR }

    data class Entry(
        val time: String,
        val level: Level,
        val message: String,
        val detail: String? = null
    )

    private const val TAG = "PixelRender"
    private const val MAX_ENTRIES = 800
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private fun add(level: Level, message: String, detail: String? = null) {
        // SimpleDateFormat tidak thread-safe, dan Logger dipanggil dari thread GL,
        // IO, Default, dan main sekaligus.
        val time = synchronized(formatter) { formatter.format(Date()) }
        val entry = Entry(time, level, message, detail)
        _entries.update { current ->
            val next = current + entry
            if (next.size > MAX_ENTRIES) next.takeLast(MAX_ENTRIES) else next
        }
        when (level) {
            Level.ERROR -> Log.e(TAG, message)
            Level.WARN -> Log.w(TAG, message)
            else -> Log.i(TAG, message)
        }
    }

    fun i(message: String, detail: String? = null) = add(Level.INFO, message, detail)
    fun ok(message: String, detail: String? = null) = add(Level.OK, message, detail)
    fun w(message: String, detail: String? = null) = add(Level.WARN, message, detail)
    fun e(message: String, detail: String? = null) = add(Level.ERROR, message, detail)

    fun clear() {
        _entries.value = emptyList()
    }

    fun dump(): String = _entries.value.joinToString("\n") { entry ->
        buildString {
            append("[").append(entry.time).append("] ")
            append(entry.level.name).append(": ")
            append(entry.message)
            entry.detail?.let { append(" -> ").append(it) }
        }
    }
}
