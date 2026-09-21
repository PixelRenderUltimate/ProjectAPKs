package com.pixelrender.app.state

import android.content.Context
import com.pixelrender.app.graphics.CLEAR_VALUE
import com.pixelrender.app.graphics.GraphicsParameter
import com.pixelrender.app.graphics.ParameterChange
import com.pixelrender.app.logging.Logger
import com.pixelrender.app.shizuku.DisplayState
import org.json.JSONObject

/**
 * State display tepat sebelum PixelRender menulis untuk pertama kali.
 * Inilah target restore, termasuk kalau user sudah punya override sendiri.
 */
data class DisplaySnapshot(
    val width: Int,
    val height: Int,
    val sizeOverridden: Boolean,
    val density: Int,
    val densityOverridden: Boolean,
    val recordedAt: Long
) {
    /**
     * Kalau sebelumnya tidak ada override, restore = clear. Men-set ulang ke
     * ukuran panel tidak sama dengan clear: override tetap tercatat di sistem.
     */
    fun toRestoreChanges(): List<ParameterChange> = listOf(
        ParameterChange(
            GraphicsParameter.DISPLAY_RESOLUTION,
            if (sizeOverridden) "${width}x${height}" else CLEAR_VALUE
        ),
        ParameterChange(
            GraphicsParameter.DISPLAY_DENSITY,
            if (densityOverridden) density.toString() else CLEAR_VALUE
        )
    )

    fun describe(): String = buildString {
        append(if (sizeOverridden) "${width}x${height} (override lama)" else "ukuran bawaan panel")
        append(", ")
        append(if (densityOverridden) "${density}dpi (override lama)" else "density bawaan panel")
    }

    fun toJson(): String = JSONObject()
        .put("width", width)
        .put("height", height)
        .put("sizeOverridden", sizeOverridden)
        .put("density", density)
        .put("densityOverridden", densityOverridden)
        .put("recordedAt", recordedAt)
        .toString()

    companion object {
        fun from(state: DisplayState) = DisplaySnapshot(
            width = state.baseWidth,
            height = state.baseHeight,
            sizeOverridden = state.sizeOverridden,
            density = state.baseDensity,
            densityOverridden = state.densityOverridden,
            recordedAt = System.currentTimeMillis()
        )

        fun fromJson(raw: String?): DisplaySnapshot? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(raw)
                DisplaySnapshot(
                    width = o.getInt("width"),
                    height = o.getInt("height"),
                    sizeOverridden = o.getBoolean("sizeOverridden"),
                    density = o.getInt("density"),
                    densityOverridden = o.getBoolean("densityOverridden"),
                    recordedAt = o.optLong("recordedAt")
                )
            }.getOrNull()
        }
    }
}

/**
 * Penyimpanan nilai asli.
 *
 * Aturan write-ahead: target restore ditulis ke disk SEBELUM perangkat diubah,
 * dengan commit() yang sinkron. Kalau proses mati di tengah penulisan, target
 * restore sudah ada dan akan ditawarkan saat aplikasi dibuka lagi.
 */
class BackupStore(context: Context) {

    data class Entry(
        val key: String,
        val originalValue: String,
        val recordedAt: Long
    )

    private val prefs =
        context.getSharedPreferences("pixelrender_backup", Context.MODE_PRIVATE)

    // --------------------------------------------------- baseline (informasi)

    fun recordBaseline(key: String, value: String) {
        if (prefs.contains(baselineKey(key))) return // baseline pertama tidak ditimpa
        prefs.edit()
            .putString(baselineKey(key), value)
            .putLong(baselineTimeKey(key), System.currentTimeMillis())
            .apply()
        Logger.i("Baseline disimpan: $key", value)
    }

    fun baselines(): List<Entry> = prefs.all.keys
        .filter { it.startsWith(BASELINE_PREFIX) && !it.endsWith(TIME_SUFFIX) }
        .map { it.removePrefix(BASELINE_PREFIX) }
        .sorted()
        .mapNotNull { key ->
            val value = prefs.getString(baselineKey(key), null) ?: return@mapNotNull null
            Entry(key, value, prefs.getLong(baselineTimeKey(key), 0L))
        }

    /** Hanya baseline informasi. Target restore yang tertunda TIDAK ikut terhapus. */
    fun clearBaselines() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(BASELINE_PREFIX) }.forEach { editor.remove(it) }
        editor.apply()
        Logger.w("Baseline informasi dihapus", "Target restore yang tertunda tetap disimpan")
    }

    // --------------------------------------------------- target restore

    fun pendingDisplay(): DisplaySnapshot? =
        DisplaySnapshot.fromJson(prefs.getString(PENDING_DISPLAY_KEY, null))

    /** Sinkron. Harus sudah di disk sebelum IWindowManager disentuh. */
    fun writePendingDisplay(snapshot: DisplaySnapshot): Boolean {
        val ok = prefs.edit().putString(PENDING_DISPLAY_KEY, snapshot.toJson()).commit()
        if (ok) Logger.i("Target restore dicatat (write-ahead)", snapshot.describe())
        else Logger.e("Gagal mencatat target restore ke disk")
        return ok
    }

    fun clearPendingDisplay() {
        prefs.edit().remove(PENDING_DISPLAY_KEY).commit()
        Logger.i("Target restore dihapus, tidak ada perubahan tertunda")
    }

    fun pendingChanges(): List<Entry> =
        listOfNotNull(pendingDisplay()?.let { Entry("display", it.describe(), it.recordedAt) })

    private fun baselineKey(key: String) = "$BASELINE_PREFIX$key"
    private fun baselineTimeKey(key: String) = "$BASELINE_PREFIX$key$TIME_SUFFIX"

    private companion object {
        const val BASELINE_PREFIX = "baseline."
        const val TIME_SUFFIX = ".at"
        const val PENDING_DISPLAY_KEY = "pending.display"
    }
}
