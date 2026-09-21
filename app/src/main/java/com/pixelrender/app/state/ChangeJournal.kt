package com.pixelrender.app.state

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/** Settings.Global.BOOT_COUNT dinaikkan sistem setiap boot. -1 kalau tidak tersedia. */
fun currentBootCount(context: Context): Int = runCatching {
    Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
}.getOrDefault(-1)

class ChangeJournal(context: Context) {

    private val prefs =
        context.getSharedPreferences("pixelrender_journal", Context.MODE_PRIVATE)

    var state: JournalState
        get() = prefs.getString(KEY_STATE, null)
            ?.let { name -> JournalState.entries.firstOrNull { it.name == name } }
            ?: JournalState.NONE
        set(value) {
            prefs.edit().putString(KEY_STATE, value.name).commit()
        }

    /** Boot terakhir yang sudah "disetujui" user untuk profil yang aktif. */
    var acknowledgedBootCount: Int
        get() = prefs.getInt(KEY_BOOT, -1)
        set(value) {
            prefs.edit().putInt(KEY_BOOT, value).commit()
        }

    /**
     * Profil terkonfirmasi yang aktif sebelum apply terakhir. Kalau apply
     * terakhir tidak dikonfirmasi, perangkat kembali ke sini, bukan langsung
     * ke kondisi asli.
     */
    var previous: ActiveProfile?
        get() = ActiveProfile.fromJson(prefs.getString(KEY_PREVIOUS, null))
        set(value) {
            prefs.edit().putString(KEY_PREVIOUS, value?.toJson()).commit()
        }

    /** Satu commit atomik: state dan titik kembali dicatat bersamaan. */
    fun beginApply(previous: ActiveProfile?) {
        prefs.edit()
            .putString(KEY_STATE, JournalState.APPLYING.name)
            .putString(KEY_PREVIOUS, previous?.toJson())
            .commit()
    }

    fun history(): List<OperationRecord> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val kind = OperationKind.entries.firstOrNull { it.name == o.optString("kind") }
                    ?: return@mapNotNull null
                OperationRecord(
                    kind = kind,
                    label = o.optString("label"),
                    expected = o.optString("expected"),
                    actual = o.optString("actual"),
                    verified = o.optBoolean("verified"),
                    note = o.optString("note"),
                    at = o.optLong("at")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun record(record: OperationRecord) {
        val next = (listOf(record) + history()).take(MAX_HISTORY)
        val array = JSONArray()
        next.forEach { r ->
            array.put(
                JSONObject()
                    .put("kind", r.kind.name)
                    .put("label", r.label)
                    .put("expected", r.expected)
                    .put("actual", r.actual)
                    .put("verified", r.verified)
                    .put("note", r.note)
                    .put("at", r.at)
            )
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_BOOT = "ack_boot_count"
        const val KEY_PREVIOUS = "previous_profile"
        const val KEY_HISTORY = "history"
        const val MAX_HISTORY = 20
    }
}
