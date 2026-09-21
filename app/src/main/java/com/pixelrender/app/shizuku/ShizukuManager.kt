package com.pixelrender.app.shizuku

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import com.pixelrender.app.logging.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    NOT_INSTALLED,
    INSTALLED_NOT_RUNNING,
    RUNNING_NO_PERMISSION,
    PERMISSION_DENIED,
    CONNECTED
}

data class ShizukuState(
    val status: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    val version: Int = -1,
    val uid: Int = -1,
    val preV11: Boolean = false,
    val suiDetected: Boolean = false
) {
    val connected: Boolean get() = status == ShizukuStatus.CONNECTED
    val running: Boolean
        get() = status == ShizukuStatus.CONNECTED ||
                status == ShizukuStatus.RUNNING_NO_PERMISSION ||
                status == ShizukuStatus.PERMISSION_DENIED

    /**
     * Shizuku berjalan sebagai UID shell (2000) melalui ADB, atau UID 0 jika
     * dijalankan dari root. Kita hanya menargetkan mode shell.
     */
    val isShellUid: Boolean get() = uid == 2000
}

/**
 * PHASE 1: hanya deteksi + permission. Tidak ada satu pun perintah yang
 * dijalankan lewat Shizuku di tahap ini.
 */
class ShizukuManager(private val context: Context) {

    companion object {
        const val PERMISSION = "moe.shizuku.manager.permission.API_V23"
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE = 4711
    }

    private val _state = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val onBinderReceived = Shizuku.OnBinderReceivedListener {
        Logger.ok("Shizuku binder diterima")
        refresh()
    }
    private val onBinderDead = Shizuku.OnBinderDeadListener {
        Logger.w("Shizuku binder mati (service berhenti)")
        refresh()
    }
    private val onPermissionResult =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Logger.ok("Shizuku permission granted")
                } else {
                    Logger.w("Shizuku permission ditolak oleh user")
                }
            }
            refresh()
        }

    private var registered = false

    fun register() {
        if (registered) return
        // Sui (varian Shizuku berbasis Zygisk) di-init lewat refleksi agar
        // tidak menjadi dependency wajib saat kompilasi.
        runCatching {
            Class.forName("rikka.sui.Sui")
                .getMethod("init", String::class.java)
                .invoke(null, context.packageName)
        }.onSuccess {
            if (it == true) Logger.i("Sui terdeteksi dan diinisialisasi")
        }

        Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
        Shizuku.addBinderDeadListener(onBinderDead)
        Shizuku.addRequestPermissionResultListener(onPermissionResult)
        registered = true
        refresh()
    }

    fun unregister() {
        if (!registered) return
        runCatching { Shizuku.removeBinderReceivedListener(onBinderReceived) }
        runCatching { Shizuku.removeBinderDeadListener(onBinderDead) }
        runCatching { Shizuku.removeRequestPermissionResultListener(onPermissionResult) }
        registered = false
    }

    fun isShizukuInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun refresh(): ShizukuState {
        val installed = isShizukuInstalled()
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

        val next = when {
            !alive && !installed ->
                ShizukuState(status = ShizukuStatus.NOT_INSTALLED)

            !alive ->
                ShizukuState(status = ShizukuStatus.INSTALLED_NOT_RUNNING)

            else -> {
                val version = runCatching { Shizuku.getVersion() }.getOrDefault(-1)
                val uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
                val preV11 = runCatching { Shizuku.isPreV11() }.getOrDefault(false)
                val granted = runCatching {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                }.getOrDefault(false)
                val denied = !granted && runCatching {
                    Shizuku.shouldShowRequestPermissionRationale()
                }.getOrDefault(false)

                ShizukuState(
                    status = when {
                        granted -> ShizukuStatus.CONNECTED
                        denied -> ShizukuStatus.PERMISSION_DENIED
                        else -> ShizukuStatus.RUNNING_NO_PERMISSION
                    },
                    version = version,
                    uid = uid,
                    preV11 = preV11,
                    suiDetected = alive && !installed
                )
            }
        }

        if (next != _state.value) {
            _state.value = next
            Logger.i("Shizuku status: ${next.status}", describe(next))
        }
        return next
    }

    fun requestPermission(activity: Activity?) {
        val current = refresh()
        when (current.status) {
            ShizukuStatus.NOT_INSTALLED -> {
                Logger.e(
                    "Shizuku belum terpasang",
                    "Aplikasi tetap berjalan dengan backend Standard."
                )
                return
            }
            ShizukuStatus.INSTALLED_NOT_RUNNING -> {
                Logger.e(
                    "Shizuku terpasang tetapi service tidak berjalan",
                    "Jalankan service dari aplikasi Shizuku (ADB / Wireless debugging)."
                )
                return
            }
            ShizukuStatus.CONNECTED -> {
                Logger.ok("Shizuku permission sudah diberikan")
                return
            }
            else -> Unit
        }

        runCatching {
            if (current.preV11) {
                activity?.requestPermissions(arrayOf(PERMISSION), REQUEST_CODE)
                    ?: Logger.e("Butuh Activity untuk meminta permission Shizuku pre-v11")
            } else {
                Logger.i("Meminta Shizuku permission...")
                Shizuku.requestPermission(REQUEST_CODE)
            }
        }.onFailure {
            Logger.e("Gagal meminta Shizuku permission", it.message ?: it.javaClass.simpleName)
        }
    }

    private fun describe(state: ShizukuState): String = buildString {
        if (state.version >= 0) append("version=").append(state.version).append(", ")
        if (state.uid >= 0) append("uid=").append(state.uid)
            .append(if (state.isShellUid) " (shell)" else " (non-shell)")
        if (state.suiDetected) append(", via Sui")
        if (isEmpty()) append("-")
    }
}
