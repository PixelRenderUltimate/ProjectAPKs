package com.pixelrender.app.graphics

import com.pixelrender.app.logging.Logger
import com.pixelrender.app.state.ActiveProfile
import com.pixelrender.app.state.BackupStore
import com.pixelrender.app.state.DisplaySnapshot

/**
 * Urutan aman untuk resolusi dan density display.
 *
 * Ada dua jenis "kembali" yang sengaja dibedakan:
 *  - [revert]: membatalkan apply terakhir. Kembali ke profil terkonfirmasi
 *    sebelumnya kalau ada, persis seperti dialog resolusi di Windows.
 *  - [restore]: RESTORE DEFAULT. Selalu ke kondisi sebelum PixelRender
 *    pernah menulis.
 */
class DisplayOverrideController(private val store: BackupStore) {

    enum class Landing { NEW_TARGET, PREVIOUS_PROFILE, ORIGINAL }

    sealed interface Outcome {
        data class Done(val description: String, val landedOn: Landing) : Outcome

        /** [landedOn] null berarti perangkat tidak berhasil dikembalikan. */
        data class Failed(val reason: String, val landedOn: Landing?) : Outcome

        data object NothingToRestore : Outcome
    }

    fun apply(
        ctx: BackendContext,
        backend: GraphicsBackend,
        plan: DisplayPlanner.Plan,
        previous: ActiveProfile?
    ): Outcome {
        if (!plan.allowed) return Outcome.Failed(plan.reason, landedOn = null)
        val channel = ctx.privileged
            ?: return Outcome.Failed("User service belum terhubung", landedOn = null)

        val current = channel.readDisplayState(0)
        if (current == null || !current.ok) {
            return Outcome.Failed(
                "State display tidak bisa dibaca sebelum menulis: ${current?.error ?: "tidak ada respons"}",
                landedOn = null
            )
        }

        // Target restore lama dipertahankan kalau sudah ada. Mengambil state
        // saat ini berarti menyimpan hasil ubahan sendiri sebagai "asli".
        val existing = store.pendingDisplay()
        if (existing == null) {
            if (!store.writePendingDisplay(DisplaySnapshot.from(current))) {
                return Outcome.Failed(
                    "Target restore gagal disimpan, penulisan dibatalkan",
                    landedOn = null
                )
            }
        } else {
            Logger.i("Target restore lama dipertahankan", existing.describe())
        }

        Logger.i("Menerapkan ${plan.percent}%", "${plan.label} lewat ${backend.id.displayName}")
        return when (val result = backend.apply(ctx, targetChanges(plan.width, plan.height, plan.density))) {
            is BackendResult.Applied -> {
                Logger.ok("Resolusi diterapkan", result.verification)
                Outcome.Done(result.verification, Landing.NEW_TARGET)
            }
            is BackendResult.Rejected -> {
                Logger.e("Penerapan gagal, membatalkan", result.reason)
                val back = revert(ctx, backend, previous)
                Outcome.Failed(result.reason, (back as? Outcome.Done)?.landedOn)
            }
            is BackendResult.NotImplemented -> Outcome.Failed(
                "Backend belum mendukung penulisan (fase ${result.availableFromPhase})",
                landedOn = null
            )
        }
    }

    fun revert(ctx: BackendContext, backend: GraphicsBackend, previous: ActiveProfile?): Outcome {
        if (previous == null) return restore(ctx, backend)

        Logger.i("Kembali ke profil sebelumnya", "${previous.id.label} ${previous.target}")
        return when (
            val result = backend.apply(ctx, targetChanges(previous.width, previous.height, previous.density))
        ) {
            is BackendResult.Applied -> Outcome.Done(
                "Kembali ke ${previous.id.label}. ${result.verification}",
                Landing.PREVIOUS_PROFILE
            )
            is BackendResult.Rejected -> {
                // Profil sebelumnya tidak bisa diterapkan ulang: pilihan aman
                // berikutnya adalah kondisi asli, bukan membiarkan state campuran.
                Logger.e(
                    "Kembali ke ${previous.id.label} gagal, mengembalikan ke kondisi asli",
                    result.reason
                )
                restore(ctx, backend)
            }
            is BackendResult.NotImplemented -> restore(ctx, backend)
        }
    }

    fun restore(ctx: BackendContext, backend: GraphicsBackend): Outcome {
        val snapshot = store.pendingDisplay() ?: return Outcome.NothingToRestore
        Logger.i("Restore dimulai", "target: ${snapshot.describe()}")

        return when (val result = backend.restore(ctx, snapshot.toRestoreChanges())) {
            is BackendResult.Applied -> {
                store.clearPendingDisplay()
                Logger.ok("Restore terverifikasi", result.verification)
                Outcome.Done(result.verification, Landing.ORIGINAL)
            }
            is BackendResult.Rejected -> {
                // Target restore sengaja TIDAK dihapus, supaya bisa dicoba lagi.
                Logger.e(
                    "Restore gagal, target restore tetap disimpan",
                    "${result.reason}. Jalur darurat: adb shell wm size reset && adb shell wm density reset"
                )
                Outcome.Failed(result.reason, landedOn = null)
            }
            is BackendResult.NotImplemented -> Outcome.Failed(
                "Backend belum mendukung restore", landedOn = null
            )
        }
    }

    private fun targetChanges(width: Int, height: Int, density: Int) = listOf(
        ParameterChange(GraphicsParameter.DISPLAY_RESOLUTION, "${width}x$height"),
        ParameterChange(GraphicsParameter.DISPLAY_DENSITY, density.toString())
    )
}
