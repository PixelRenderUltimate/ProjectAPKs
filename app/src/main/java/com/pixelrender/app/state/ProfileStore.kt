package com.pixelrender.app.state

import android.content.Context
import com.pixelrender.app.graphics.DisplayPlanner
import com.pixelrender.app.graphics.ProfileId
import com.pixelrender.app.shizuku.DisplayState
import org.json.JSONObject

/** Profil yang benar-benar sedang aktif di perangkat, beserta target yang ditulis. */
data class ActiveProfile(
    val id: ProfileId,
    val width: Int,
    val height: Int,
    val density: Int,
    val appliedAt: Long
) {
    val target: String get() = "${width}x${height} @${density}dpi"

    /** Status profil diverifikasi dari state nyata, bukan dari catatan saja. */
    fun matches(state: DisplayState?): Boolean =
        state != null && state.ok &&
                state.baseWidth == width &&
                state.baseHeight == height &&
                state.baseDensity == density

    fun toJson(): String = JSONObject()
        .put("id", id.name)
        .put("w", width)
        .put("h", height)
        .put("d", density)
        .put("at", appliedAt)
        .toString()

    companion object {
        fun fromJson(raw: String?): ActiveProfile? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(raw)
                val id = ProfileId.entries.firstOrNull { it.name == o.getString("id") }
                    ?: return null
                ActiveProfile(id, o.getInt("w"), o.getInt("h"), o.getInt("d"), o.optLong("at"))
            }.getOrNull()
        }
    }
}

class ProfileStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("pixelrender_profiles", Context.MODE_PRIVATE)

    var selectedProfile: ProfileId
        get() = prefs.getString(KEY_SELECTED, null)
            ?.let { name -> ProfileId.entries.firstOrNull { it.name == name } }
            ?: ProfileId.PIXEL_MEDIUM
        set(value) {
            prefs.edit().putString(KEY_SELECTED, value.name).apply()
        }

    /** Hanya nilai dari langkah tetap yang diterima; nilai lain dianggap rusak. */
    var customScale: Float
        get() = prefs.getFloat(KEY_CUSTOM_SCALE, DEFAULT_CUSTOM)
            .takeIf { it in DisplayPlanner.SCALE_STEPS } ?: DEFAULT_CUSTOM
        set(value) {
            prefs.edit().putFloat(KEY_CUSTOM_SCALE, value).apply()
        }

    var selectedGame: String?
        get() = prefs.getString(KEY_GAME, null)
        set(value) {
            prefs.edit().putString(KEY_GAME, value).apply()
        }

    fun active(): ActiveProfile? {
        val name = prefs.getString(KEY_ACTIVE_ID, null) ?: return null
        val id = ProfileId.entries.firstOrNull { it.name == name } ?: return null
        return ActiveProfile(
            id = id,
            width = prefs.getInt(KEY_ACTIVE_W, 0),
            height = prefs.getInt(KEY_ACTIVE_H, 0),
            density = prefs.getInt(KEY_ACTIVE_D, 0),
            appliedAt = prefs.getLong(KEY_ACTIVE_AT, 0L)
        )
    }

    fun setActive(profile: ActiveProfile) {
        prefs.edit()
            .putString(KEY_ACTIVE_ID, profile.id.name)
            .putInt(KEY_ACTIVE_W, profile.width)
            .putInt(KEY_ACTIVE_H, profile.height)
            .putInt(KEY_ACTIVE_D, profile.density)
            .putLong(KEY_ACTIVE_AT, profile.appliedAt)
            .commit()
    }

    fun clearActive() {
        prefs.edit()
            .remove(KEY_ACTIVE_ID)
            .remove(KEY_ACTIVE_W)
            .remove(KEY_ACTIVE_H)
            .remove(KEY_ACTIVE_D)
            .remove(KEY_ACTIVE_AT)
            .commit()
    }

    private companion object {
        const val KEY_SELECTED = "selected_profile"
        const val KEY_CUSTOM_SCALE = "custom_scale"
        const val KEY_GAME = "selected_game"
        const val KEY_ACTIVE_ID = "active.id"
        const val KEY_ACTIVE_W = "active.w"
        const val KEY_ACTIVE_H = "active.h"
        const val KEY_ACTIVE_D = "active.d"
        const val KEY_ACTIVE_AT = "active.at"
        const val DEFAULT_CUSTOM = 0.66f
    }
}
