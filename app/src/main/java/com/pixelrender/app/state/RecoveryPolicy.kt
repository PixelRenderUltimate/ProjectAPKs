package com.pixelrender.app.state

// Murni: tanpa Android SDK. Semua keputusan pemulihan ada di sini supaya bisa
// diuji sebagai unit test JVM, terpisah dari SharedPreferences dan UI.

/**
 * Status perubahan yang tersimpan di disk, bukan di memori.
 *
 * Tanpa ini, janji "tidak dikonfirmasi berarti dikembalikan" hanya berlaku
 * selama aplikasi hidup. Kalau layar jadi sulit dipakai lalu aplikasi
 * di-force-close atau perangkat di-reboot, perubahan yang belum dikonfirmasi
 * akan tertinggal selamanya.
 */
enum class JournalState {
    NONE,

    /** Target restore sudah dicatat, penulisan sedang berjalan. */
    APPLYING,

    /** Tertulis dan terverifikasi, tetapi user belum memilih Pertahankan. */
    AWAITING_CONFIRMATION,

    /** User memilih Pertahankan. Tidak akan pernah dikembalikan otomatis. */
    CONFIRMED
}

enum class RecoveryState {
    NONE,

    /** Ada perubahan yang tidak pernah dikonfirmasi. Dikembalikan otomatis. */
    UNCONFIRMED,

    /** Profil terkonfirmasi, perangkat sudah reboot sejak itu. User memilih. */
    REBOOTED,

    /** Ada perubahan aktif, tetapi Shizuku tidak berjalan. */
    SHIZUKU_LOST
}

enum class OperationKind(val label: String) {
    APPLY("Apply"),
    REVERT("Kembalikan"),
    AUTO_REVERT("Kembalikan otomatis"),
    RESTORE("Restore default")
}

/** Satu baris laporan verifikasi: apa yang diminta dan apa yang benar-benar terbaca. */
data class OperationRecord(
    val kind: OperationKind,
    val label: String,
    val expected: String,
    val actual: String,
    val verified: Boolean,
    val note: String,
    val at: Long
)

/** Tindakan saat aplikasi dibuka dan catatan di disk saling tidak konsisten. */
enum class ReconcileAction {
    NOTHING,

    /** Ada catatan profil/journal tetapi tidak ada target restore: catatan basi. */
    CLEAR_STALE_RECORDS,

    /**
     * Ada target restore tanpa journal: perubahan dari versi sebelum PHASE 6.
     * Versi itu sudah memakai konfirmasi, jadi dianggap terkonfirmasi.
     */
    ADOPT_AS_CONFIRMED
}

object RecoveryPolicy {

    /**
     * Urutan prioritas sengaja: perubahan yang tidak dikonfirmasi lebih
     * mendesak daripada reboot, dan keduanya lebih spesifik daripada sekadar
     * "Shizuku tidak berjalan".
     */
    fun evaluate(
        hasPending: Boolean,
        confirming: Boolean,
        applying: Boolean,
        journalState: JournalState,
        rebootedSinceConfirm: Boolean,
        shizukuRunning: Boolean
    ): RecoveryState = when {
        !hasPending -> RecoveryState.NONE
        // Hitung mundur atau operasi yang sedang berjalan di sesi ini tidak
        // boleh dianggap perlu dipulihkan.
        confirming || applying -> RecoveryState.NONE
        journalState == JournalState.APPLYING ||
                journalState == JournalState.AWAITING_CONFIRMATION -> RecoveryState.UNCONFIRMED
        journalState == JournalState.CONFIRMED && rebootedSinceConfirm -> RecoveryState.REBOOTED
        !shizukuRunning -> RecoveryState.SHIZUKU_LOST
        else -> RecoveryState.NONE
    }

    /** -1 berarti BOOT_COUNT tidak tersedia; dalam hal itu reboot tidak diklaim. */
    fun rebootedSinceConfirm(
        journalState: JournalState,
        currentBoot: Int,
        acknowledgedBoot: Int
    ): Boolean = journalState == JournalState.CONFIRMED &&
            currentBoot >= 0 && acknowledgedBoot >= 0 &&
            currentBoot > acknowledgedBoot

    /** Hanya perubahan yang tidak pernah dikonfirmasi yang boleh ditulis ulang otomatis. */
    fun shouldAutoRevert(recovery: RecoveryState): Boolean =
        recovery == RecoveryState.UNCONFIRMED

    fun blocksNewApply(recovery: RecoveryState): Boolean =
        recovery == RecoveryState.UNCONFIRMED

    fun reconcile(
        hasPending: Boolean,
        journalState: JournalState,
        hasActiveProfile: Boolean
    ): ReconcileAction = when {
        !hasPending && (hasActiveProfile || journalState != JournalState.NONE) ->
            ReconcileAction.CLEAR_STALE_RECORDS
        hasPending && journalState == JournalState.NONE -> ReconcileAction.ADOPT_AS_CONFIRMED
        else -> ReconcileAction.NOTHING
    }
}
