package com.pixelrender.app.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.pixelrender.app.BuildConfig
import com.pixelrender.app.graphics.PrivilegedChannel
import com.pixelrender.app.graphics.WriteOutcome
import com.pixelrender.app.logging.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class UserServiceStatus { NOT_BOUND, BINDING, BOUND, FAILED }

data class UserServiceState(
    val status: UserServiceStatus = UserServiceStatus.NOT_BOUND,
    val serviceUid: Int = -1,
    val policyVersion: Int = -1,
    val message: String = ""
) {
    val bound: Boolean get() = status == UserServiceStatus.BOUND
    val runningAsShell: Boolean get() = serviceUid == 2000
}

/**
 * Membungkus Shizuku user service. Service dijalankan di proses terpisah
 * dengan UID shell; kelas ini hanya bertugas bind, unbind, dan meneruskan
 * panggilan. Semua command tetap divalidasi dua kali.
 */
class ShizukuUserServiceClient : PrivilegedChannel {

    private val _state = MutableStateFlow(UserServiceState())
    val state: StateFlow<UserServiceState> = _state.asStateFlow()

    private var service: IPixelRenderService? = null

    private val args by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(
                BuildConfig.APPLICATION_ID,
                PixelRenderUserService::class.java.name
            )
        )
            .daemon(false)
            .processNameSuffix("privileged")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) {
                service = null
                fail("Binder user service tidak valid")
                return
            }
            val remote = IPixelRenderService.Stub.asInterface(binder)
            service = remote

            val uid = runCatching { remote.serviceUid }.getOrDefault(-1)
            val policy = runCatching { remote.policyVersion }.getOrDefault(-1)

            if (policy != CommandPolicy.POLICY_VERSION) {
                Logger.w(
                    "Versi policy tidak sama antar proses",
                    "app=${CommandPolicy.POLICY_VERSION}, service=$policy. " +
                            "Biasanya karena service lama masih hidup. Unbind lalu bind ulang."
                )
            }

            _state.value = UserServiceState(
                status = UserServiceStatus.BOUND,
                serviceUid = uid,
                policyVersion = policy,
                message = if (uid == 2000) "Berjalan sebagai shell"
                else "Berjalan sebagai UID $uid"
            )
            Logger.ok("User service terhubung", "uid=$uid, policy=$policy")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _state.value = UserServiceState(
                status = UserServiceStatus.NOT_BOUND,
                message = "Service terputus"
            )
            Logger.w("User service terputus")
        }
    }

    fun bind(shizuku: ShizukuState) {
        if (_state.value.status == UserServiceStatus.BOUND) {
            Logger.i("User service sudah terhubung")
            return
        }
        if (_state.value.status == UserServiceStatus.BINDING) return
        if (!shizuku.connected) {
            fail("Shizuku belum terhubung atau permission belum diberikan")
            return
        }
        if (shizuku.version in 0..9) {
            fail("Shizuku versi ${shizuku.version} terlalu lama, user service butuh versi 10+")
            return
        }

        _state.value = UserServiceState(status = UserServiceStatus.BINDING, message = "Binding...")
        Logger.i("Binding user service...")
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure { fail(it.message ?: it.javaClass.simpleName) }
    }

    fun unbind() {
        if (_state.value.status == UserServiceStatus.NOT_BOUND) return
        runCatching { Shizuku.unbindUserService(args, connection, true) }
            .onFailure { Logger.w("Gagal unbind user service", it.message ?: "-") }
        service = null
        _state.value = UserServiceState(message = "Tidak terhubung")
        Logger.i("User service di-unbind")
    }

    /**
     * Validasi di sisi aplikasi. Service memvalidasi ulang, jadi ini murni
     * supaya command yang jelas ditolak tidak perlu menyeberangi binder.
     */
    fun exec(command: List<String>, timeoutMs: Int = 5000): CommandResult {
        val printable = command.joinToString(" ")
        val verdict = CommandPolicy.validate(command)
        if (!verdict.allowed) {
            Logger.e("Command ditolak policy", "$printable -> ${verdict.reason}")
            return CommandResult(printable, false, -1, "", verdict.reason)
        }
        val remote = service ?: run {
            Logger.e("User service belum terhubung", printable)
            return CommandResult(printable, false, -1, "", "User service belum terhubung")
        }
        return runCatching {
            CommandResult.parse(printable, remote.execJson(command.toTypedArray(), timeoutMs))
        }.getOrElse {
            Logger.e("Panggilan binder gagal", "$printable -> ${it.message}")
            CommandResult(printable, false, -1, "", it.message ?: it.javaClass.simpleName)
        }
    }

    // ------------------------------------------------ PrivilegedChannel

    override fun readDisplayState(displayId: Int): DisplayState? {
        val remote = service ?: return null
        return DisplayStateParser.parse(runCatching { remote.readDisplayStateJson(displayId) }.getOrNull())
    }

    override fun setDisplaySize(
        displayId: Int,
        width: Int,
        height: Int,
        restoring: Boolean
    ): WriteOutcome = write(
        "IWindowManager.setForcedDisplaySize(${width}x$height${if (restoring) ", restore" else ""})"
    ) { it.setDisplaySizeJson(displayId, width, height, restoring) }

    override fun setDisplayDensity(
        displayId: Int,
        density: Int,
        restoring: Boolean
    ): WriteOutcome = write(
        "IWindowManager.setForcedDisplayDensity($density${if (restoring) ", restore" else ""})"
    ) { it.setDisplayDensityJson(displayId, density, restoring) }

    override fun clearDisplayOverride(
        displayId: Int,
        clearSize: Boolean,
        clearDensity: Boolean
    ): WriteOutcome = write(
        "IWindowManager.clearForcedDisplay(size=$clearSize, density=$clearDensity)"
    ) { it.clearDisplayOverrideJson(displayId, clearSize, clearDensity) }

    /** Setiap tulisan tercatat di Logs beserta state sesudahnya. */
    private fun write(label: String, call: (IPixelRenderService) -> String): WriteOutcome {
        val remote = service ?: run {
            Logger.e(label, "User service belum terhubung")
            return WriteOutcome(false, "User service belum terhubung", null)
        }
        val raw = runCatching { call(remote) }.getOrElse {
            Logger.e(label, "Binder gagal: ${it.message ?: it.javaClass.simpleName}")
            return WriteOutcome(false, it.message ?: it.javaClass.simpleName, null)
        }
        val json = runCatching { org.json.JSONObject(raw) }.getOrNull()
            ?: return WriteOutcome(false, "Respons service tidak bisa diparse", null).also {
                Logger.e(label, "Respons tidak bisa diparse")
            }

        val ok = json.optBoolean("ok", false)
        val error = listOf(json.optString("error"), json.optString("reason"))
            .filter { it.isNotBlank() }
            .joinToString(": ")
        val after = DisplayStateParser.parse(raw)

        if (ok && after != null && after.ok) {
            Logger.ok(label, "sesudah: ${after.current} @${after.baseDensity}dpi")
        } else {
            Logger.e(label, error.ifBlank { "gagal tanpa keterangan" })
        }
        return WriteOutcome(ok, error, after)
    }

    private fun fail(reason: String) {
        _state.value = UserServiceState(status = UserServiceStatus.FAILED, message = reason)
        Logger.e("User service gagal", reason)
    }
}

data class CommandResult(
    val command: String,
    val ok: Boolean,
    val exitCode: Int,
    val output: String,
    val error: String = ""
) {
    companion object {
        fun parse(command: String, json: String): CommandResult = runCatching {
            val o = org.json.JSONObject(json)
            CommandResult(
                command = o.optString("command", command),
                ok = o.optBoolean("ok", false),
                exitCode = o.optInt("exitCode", -1),
                output = o.optString("output", ""),
                error = listOf(o.optString("error"), o.optString("reason"))
                    .filter { it.isNotBlank() }
                    .joinToString(": ")
            )
        }.getOrElse {
            CommandResult(command, false, -1, "", "Respons tidak bisa diparse")
        }
    }
}
