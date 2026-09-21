package com.pixelrender.app.shizuku

import android.content.Context
import android.graphics.Point
import android.os.IBinder
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import kotlin.system.exitProcess

/**
 * Berjalan di proses terpisah yang dijalankan Shizuku dengan UID shell (2000).
 * Kelas ini TIDAK didaftarkan di AndroidManifest; Shizuku memuatnya lewat nama
 * kelas, jadi harus dipertahankan dari obfuscation (lihat proguard-rules.pro).
 *
 * Jalur tulis hanya ada tiga method bertipe (ukuran, density, clear), bukan
 * command shell bebas. Setiap tulisan:
 *  1. dicek fasenya,
 *  2. membaca state sebelum,
 *  3. divalidasi ulang oleh [DisplayWritePolicy] terhadap ukuran panel,
 *  4. dieksekusi lewat binder IWindowManager,
 *  5. membaca state sesudah, dan mengembalikan keduanya.
 */
class PixelRenderUserService() : IPixelRenderService.Stub() {

    @Suppress("unused")
    constructor(context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun getServiceUid(): Int = android.os.Process.myUid()

    override fun getPolicyVersion(): Int = CommandPolicy.POLICY_VERSION

    // ---------------------------------------------------------------- baca

    override fun execJson(command: Array<String>?, timeoutMs: Int): String {
        val argv = command?.toList().orEmpty()
        val printable = argv.joinToString(" ")

        val verdict = CommandPolicy.validate(argv)
        if (!verdict.allowed) {
            return JSONObject()
                .put("ok", false)
                .put("error", "REJECTED")
                .put("reason", verdict.reason)
                .put("command", printable)
                .toString()
        }
        return try {
            runProcess(argv, timeoutMs.coerceIn(500, 15_000), printable)
        } catch (t: Throwable) {
            errorJson(t).put("command", printable).toString()
        }
    }

    override fun readDisplayStateJson(displayId: Int): String =
        try {
            val (wm, iwm) = windowManager()
            readState(wm, iwm, displayId).toString()
        } catch (t: Throwable) {
            errorJson(t).put("displayId", displayId).toString()
        }

    // ---------------------------------------------------------------- tulis

    override fun setDisplaySizeJson(
        displayId: Int,
        width: Int,
        height: Int,
        restoring: Boolean
    ): String = guardedWrite("setForcedDisplaySize", displayId) { wm, iwm, before ->
        val reason = DisplayWritePolicy.checkSize(
            before.optInt("initialWidth"),
            before.optInt("initialHeight"),
            width,
            height,
            restoring
        )
        if (reason == null) {
            iwm.getMethod("setForcedDisplaySize", INT, INT, INT)
                .invoke(wm, displayId, width, height)
        }
        reason
    }

    override fun setDisplayDensityJson(
        displayId: Int,
        density: Int,
        restoring: Boolean
    ): String = guardedWrite("setForcedDisplayDensity", displayId) { wm, iwm, before ->
        val reason = DisplayWritePolicy.checkDensity(
            before.optInt("initialDensity"),
            density,
            restoring
        )
        if (reason == null) {
            val forUser = runCatching {
                iwm.getMethod("setForcedDisplayDensityForUser", INT, INT, INT)
            }.getOrNull()
            if (forUser != null) {
                forUser.invoke(wm, displayId, density, USER_CURRENT)
            } else {
                iwm.getMethod("setForcedDisplayDensity", INT, INT)
                    .invoke(wm, displayId, density)
            }
        }
        reason
    }

    override fun clearDisplayOverrideJson(
        displayId: Int,
        clearSize: Boolean,
        clearDensity: Boolean
    ): String = guardedWrite("clearForcedDisplay", displayId) { wm, iwm, _ ->
        if (clearSize) {
            iwm.getMethod("clearForcedDisplaySize", INT).invoke(wm, displayId)
        }
        if (clearDensity) {
            val forUser = runCatching {
                iwm.getMethod("clearForcedDisplayDensityForUser", INT, INT)
            }.getOrNull()
            if (forUser != null) {
                forUser.invoke(wm, displayId, USER_CURRENT)
            } else {
                iwm.getMethod("clearForcedDisplayDensity", INT).invoke(wm, displayId)
            }
        }
        null
    }

    /**
     * @param block mengembalikan null kalau tulisan dijalankan, atau alasan penolakan.
     */
    private fun guardedWrite(
        op: String,
        displayId: Int,
        block: (wm: Any, iwm: Class<*>, before: JSONObject) -> String?
    ): String {
        if (CommandPolicy.ACTIVE_PHASE < CommandPolicy.PHASE_WRITES_ALLOWED) {
            return JSONObject()
                .put("ok", false)
                .put("op", op)
                .put("error", "REJECTED")
                .put("reason", "Penulisan belum diizinkan pada PHASE ${CommandPolicy.ACTIVE_PHASE}")
                .toString()
        }
        return try {
            val (wm, iwm) = windowManager()
            val before = readState(wm, iwm, displayId)
            if (!before.optBoolean("ok")) {
                return before.put("op", op).toString()
            }
            val rejection = block(wm, iwm, before)
            if (rejection != null) {
                return JSONObject()
                    .put("ok", false)
                    .put("op", op)
                    .put("error", "REJECTED")
                    .put("reason", rejection)
                    .toString()
            }
            readState(wm, iwm, displayId)
                .put("op", op)
                .put("before", before)
                .toString()
        } catch (t: Throwable) {
            errorJson(t).put("op", op).toString()
        }
    }

    // ---------------------------------------------------------------- helper

    private fun windowManager(): Pair<Any, Class<*>> {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "window") as? IBinder
            ?: throw IllegalStateException("ServiceManager tidak mengembalikan binder 'window'")
        val wm = Class.forName("android.view.IWindowManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)
            ?: throw IllegalStateException("IWindowManager.Stub.asInterface mengembalikan null")
        return wm to Class.forName("android.view.IWindowManager")
    }

    private fun readState(wm: Any, iwm: Class<*>, displayId: Int): JSONObject {
        val initial = Point()
        iwm.getMethod("getInitialDisplaySize", INT, Point::class.java)
            .invoke(wm, displayId, initial)
        val base = Point()
        iwm.getMethod("getBaseDisplaySize", INT, Point::class.java)
            .invoke(wm, displayId, base)
        val initialDensity = iwm.getMethod("getInitialDisplayDensity", INT)
            .invoke(wm, displayId) as Int
        val baseDensity = iwm.getMethod("getBaseDisplayDensity", INT)
            .invoke(wm, displayId) as Int

        return JSONObject()
            .put("ok", true)
            .put("displayId", displayId)
            .put("initialWidth", initial.x)
            .put("initialHeight", initial.y)
            .put("baseWidth", base.x)
            .put("baseHeight", base.y)
            .put("initialDensity", initialDensity)
            .put("baseDensity", baseDensity)
            .put("sizeOverridden", initial.x != base.x || initial.y != base.y)
            .put("densityOverridden", initialDensity != baseDensity)
    }

    /** Refleksi membungkus exception asli; yang dilaporkan harus penyebab sebenarnya. */
    private fun errorJson(t: Throwable): JSONObject {
        val cause = (t as? InvocationTargetException)?.targetException ?: t
        return JSONObject()
            .put("ok", false)
            .put("error", cause.javaClass.simpleName)
            .put("reason", cause.message ?: "-")
    }

    private fun runProcess(argv: List<String>, timeoutMs: Int, printable: String): String {
        val process = ProcessBuilder(argv)
            .redirectErrorStream(true)
            .start()

        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(output) {
                        if (output.length < 16_384) output.append(line).append('\n')
                    }
                }
            }
        }
        reader.isDaemon = true
        reader.start()

        if (!awaitExit(process, timeoutMs)) {
            process.destroy()
            reader.join(300)
            return JSONObject()
                .put("ok", false)
                .put("error", "TIMEOUT")
                .put("reason", "Tidak selesai dalam ${timeoutMs}ms")
                .put("command", printable)
                .toString()
        }
        reader.join(500)

        val exit = process.exitValue()
        val text = synchronized(output) { output.toString().trim() }
        return JSONObject()
            .put("ok", exit == 0)
            .put("exitCode", exit)
            .put("output", text)
            .put("command", printable)
            .toString()
    }

    /** Process.waitFor(timeout) baru ada di API 26, jadi dibuat manual. */
    private fun awaitExit(process: Process, timeoutMs: Int): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                process.exitValue()
                return true
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(20)
            }
        }
        return false
    }

    private companion object {
        val INT: Class<Int>? = Int::class.javaPrimitiveType

        /** UserHandle.USER_CURRENT, nilai yang sama dengan yang dipakai 'wm density'. */
        const val USER_CURRENT = -2
    }
}
