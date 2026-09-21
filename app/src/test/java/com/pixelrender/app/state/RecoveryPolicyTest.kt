package com.pixelrender.app.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RecoveryPolicy diekstrak dari logika inline PHASE 6. Uji ini membuktikan
 * keduanya identik untuk SEMUA kombinasi masukan, bukan hanya contoh.
 */
class RecoveryPolicyTest {

    /** Salinan persis ekspresi UiState.recovery pada PHASE 6. */
    private fun phase6Recovery(
        pending: Boolean, confirming: Boolean, applying: Boolean,
        journal: JournalState, rebooted: Boolean, shizukuRunning: Boolean
    ): RecoveryState = when {
        !pending -> RecoveryState.NONE
        confirming || applying -> RecoveryState.NONE
        journal == JournalState.APPLYING ||
                journal == JournalState.AWAITING_CONFIRMATION -> RecoveryState.UNCONFIRMED
        journal == JournalState.CONFIRMED && rebooted -> RecoveryState.REBOOTED
        !shizukuRunning -> RecoveryState.SHIZUKU_LOST
        else -> RecoveryState.NONE
    }

    /** Salinan alur reconcileStoredState() pada PHASE 6, bentuk imperatif aslinya. */
    private fun phase6Reconcile(pending: Boolean, journal: JournalState, active: Boolean): ReconcileAction {
        if (!pending) {
            return if (active || journal != JournalState.NONE) ReconcileAction.CLEAR_STALE_RECORDS
            else ReconcileAction.NOTHING
        }
        return if (journal == JournalState.NONE) ReconcileAction.ADOPT_AS_CONFIRMED
        else ReconcileAction.NOTHING
    }

    private val bools = listOf(false, true)

    @Test
    fun evaluateIsIdenticalToPhase6ForAllInputs() {
        var combos = 0
        for (p in bools) for (c in bools) for (a in bools) for (j in JournalState.entries)
            for (r in bools) for (s in bools) {
                combos++
                assertEquals(
                    "pending=$p confirming=$c applying=$a journal=$j rebooted=$r shizuku=$s",
                    phase6Recovery(p, c, a, j, r, s),
                    RecoveryPolicy.evaluate(p, c, a, j, r, s)
                )
            }
        assertEquals(128, combos)
    }

    @Test
    fun reconcileIsIdenticalToPhase6ForAllInputs() {
        for (p in bools) for (j in JournalState.entries) for (active in bools) {
            assertEquals(
                "pending=$p journal=$j active=$active",
                phase6Reconcile(p, j, active),
                RecoveryPolicy.reconcile(p, j, active)
            )
        }
    }

    @Test
    fun rebootDetection() {
        assertTrue(RecoveryPolicy.rebootedSinceConfirm(JournalState.CONFIRMED, 5, 4))
        assertFalse(RecoveryPolicy.rebootedSinceConfirm(JournalState.CONFIRMED, 4, 4))
        assertFalse("BOOT_COUNT tidak tersedia", RecoveryPolicy.rebootedSinceConfirm(JournalState.CONFIRMED, -1, 4))
        assertFalse("belum pernah di-ack", RecoveryPolicy.rebootedSinceConfirm(JournalState.CONFIRMED, 5, -1))
        assertFalse("belum dikonfirmasi", RecoveryPolicy.rebootedSinceConfirm(JournalState.AWAITING_CONFIRMATION, 5, 4))
    }

    /** Skenario PHASE 6 yang paling penting, ditulis sebagai cerita. */
    @Test
    fun forceCloseDuringCountdownIsRevertedButConfirmedIsNeverTouched() {
        val afterForceClose = RecoveryPolicy.evaluate(
            hasPending = true, confirming = false, applying = false,
            journalState = JournalState.AWAITING_CONFIRMATION,
            rebootedSinceConfirm = false, shizukuRunning = true
        )
        assertEquals(RecoveryState.UNCONFIRMED, afterForceClose)
        assertTrue(RecoveryPolicy.shouldAutoRevert(afterForceClose))
        assertTrue(RecoveryPolicy.blocksNewApply(afterForceClose))

        JournalState.entries.filter { it == JournalState.CONFIRMED }.forEach { j ->
            for (r in bools) for (s in bools) {
                val state = RecoveryPolicy.evaluate(true, false, false, j, r, s)
                assertFalse("CONFIRMED tidak boleh dikembalikan otomatis", RecoveryPolicy.shouldAutoRevert(state))
            }
        }
    }

    /** Hitung mundur di sesi ini tidak boleh dianggap perubahan yatim. */
    @Test
    fun ownCountdownIsNotMistakenForOrphan() {
        val state = RecoveryPolicy.evaluate(
            true, confirming = true, applying = false,
            journalState = JournalState.AWAITING_CONFIRMATION,
            rebootedSinceConfirm = false, shizukuRunning = true
        )
        assertEquals(RecoveryState.NONE, state)
    }
}
