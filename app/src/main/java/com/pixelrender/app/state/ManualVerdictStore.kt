package com.pixelrender.app.state

import android.content.Context
import com.pixelrender.app.testing.TestVerdict

/** Satu-satunya masukan tangan di rencana uji: penilaian untuk uji MANUAL. */
class ManualVerdictStore(context: Context) {

    private val prefs = context.getSharedPreferences("pixelrender_tests", Context.MODE_PRIVATE)

    fun all(): Map<String, TestVerdict> = prefs.all.mapNotNull { (id, value) ->
        val verdict = (value as? String)
            ?.let { name -> TestVerdict.entries.firstOrNull { it.name == name } }
        verdict?.let { id to it }
    }.toMap()

    fun set(id: String, verdict: TestVerdict?) {
        val editor = prefs.edit()
        if (verdict == null) editor.remove(id) else editor.putString(id, verdict.name)
        editor.apply()
    }
}
