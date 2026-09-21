package com.pixelrender.app.recovery

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pixelrender.app.MainActivity
import com.pixelrender.app.R
import com.pixelrender.app.state.ActiveProfile
import com.pixelrender.app.state.JournalState

/**
 * Satu-satunya notifikasi aplikasi ini: pengingat setelah reboot bahwa
 * resolusi masih diubah. Tidak menulis apa pun; hanya mengajak user membuka
 * aplikasi untuk memutuskan.
 */
object RecoveryNotifier {

    private const val CHANNEL_ID = "pixelrender_recovery"
    private const val NOTIFICATION_ID = 1001

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // dicek lewat canNotify()
    fun notifyAfterBoot(context: Context, journalState: JournalState, active: ActiveProfile?) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val unconfirmed = journalState == JournalState.APPLYING ||
                journalState == JournalState.AWAITING_CONFIRMATION
        val title = if (unconfirmed) "Perubahan resolusi belum dikonfirmasi"
        else "Profil PixelRender masih aktif"
        val text = if (unconfirmed) {
            "Perubahan ini belum dikonfirmasi sebelum perangkat berhenti. Buka PixelRender " +
                    "dengan Shizuku aktif dan perubahan akan dikembalikan otomatis."
        } else {
            "${active?.id?.label ?: "Override resolusi"} (${active?.target ?: "-"}) bertahan " +
                    "setelah reboot. Buka PixelRender untuk mempertahankan atau mengembalikan."
        }

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pixel)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Pemulihan resolusi",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Pengingat setelah reboot kalau resolusi masih diubah PixelRender"
            }
        )
    }
}
