package com.pixelrender.app.recovery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pixelrender.app.state.BackupStore
import com.pixelrender.app.state.ChangeJournal
import com.pixelrender.app.state.ProfileStore

/**
 * Override resolusi dari IWindowManager bertahan setelah reboot, sedangkan
 * Shizuku mode ADB tidak. Tanpa pengingat ini, user bisa terjebak dengan
 * resolusi rendah tanpa tahu penyebabnya.
 *
 * Receiver ini tidak menulis apa pun ke perangkat. Keputusan tetap di user,
 * dan restore otomatis hanya terjadi di dalam aplikasi untuk perubahan yang
 * memang tidak pernah dikonfirmasi.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (BackupStore(context).pendingDisplay() == null) return
        RecoveryNotifier.notifyAfterBoot(
            context,
            ChangeJournal(context).state,
            ProfileStore(context).active()
        )
    }
}
