package com.pixelrender.app.graphics

import android.content.Context
import com.pixelrender.app.device.DeviceInfo
import com.pixelrender.app.shizuku.DisplayState
import com.pixelrender.app.shizuku.ProbeReport
import com.pixelrender.app.shizuku.ShizukuState
import com.pixelrender.app.shizuku.UserServiceState


data class WriteOutcome(
    val ok: Boolean,
    val error: String,
    /** State setelah operasi, dibaca ulang oleh service. */
    val after: DisplayState?
)

/**
 * Jalur privileged yang dipakai backend untuk menulis. Diimplementasikan oleh
 * ShizukuUserServiceClient. Backend tidak pernah memegang binder langsung.
 */
interface PrivilegedChannel {
    fun readDisplayState(displayId: Int): DisplayState?
    fun setDisplaySize(displayId: Int, width: Int, height: Int, restoring: Boolean): WriteOutcome
    fun setDisplayDensity(displayId: Int, density: Int, restoring: Boolean): WriteOutcome
    fun clearDisplayOverride(displayId: Int, clearSize: Boolean, clearDensity: Boolean): WriteOutcome
}

data class BackendContext(
    val app: Context,
    val device: DeviceInfo,
    val gl: GlInfo,
    val vulkan: VulkanInfo,
    val shizuku: ShizukuState,
    val userService: UserServiceState,
    val probe: ProbeReport?,
    /** null kalau user service belum terhubung. */
    val privileged: PrivilegedChannel? = null
)

/**
 * Kontrak semua backend.
 *
 * Aturan yang tidak boleh dilanggar implementasi mana pun: sebuah backend
 * hanya boleh melaporkan externalControl = SUPPORTED kalau backend itu
 * benar-benar punya jalur API untuk mengubah parameter tersebut pada aplikasi
 * lain, dan jalur itu sudah terverifikasi pada perangkat yang sedang berjalan.
 * Kalau belum diverifikasi, jawabannya UNKNOWN, bukan SUPPORTED.
 */
interface GraphicsBackend {
    val id: BackendId
    val role: BackendRole

    /** Backend dengan angka lebih besar dipilih lebih dulu oleh mode Auto. */
    val priority: Int

    fun availability(ctx: BackendContext): BackendAvailability

    fun capabilities(ctx: BackendContext): List<BackendCapability>

    fun settingsShortcuts(ctx: BackendContext): List<SettingsShortcut> = emptyList()

    /**
     * Menerapkan perubahan lalu WAJIB membaca ulang hasilnya. Mengembalikan
     * Applied hanya kalau nilai yang terbaca sesudahnya sama dengan yang diminta.
     */
    fun apply(ctx: BackendContext, changes: List<ParameterChange>): BackendResult =
        BackendResult.Rejected(
            "Backend ${id.displayName} tidak punya jalur untuk mengubah parameter ini"
        )

    /**
     * Mengembalikan ke [original]. Nilai [CLEAR_VALUE] berarti tidak ada
     * override sebelumnya, jadi override harus dibersihkan, bukan di-set ulang.
     */
    fun restore(ctx: BackendContext, original: List<ParameterChange>): BackendResult =
        BackendResult.Rejected(
            "Backend ${id.displayName} tidak pernah mengubah apa pun, " +
                    "jadi tidak ada yang perlu dikembalikan"
        )
}
