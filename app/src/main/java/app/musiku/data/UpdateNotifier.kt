package app.musiku.data

import android.Manifest
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
import app.musiku.MainActivity
import app.musiku.R

/**
 * "New version" notification for [UpdateWorker], so a release is not missed on days the app is
 * never opened. The tap is a plain launcher intent: the normal in-app check runs on start and
 * shows the update card, so nothing has to be handed across processes here.
 */
object UpdateNotifier {

    private const val ChannelId = "updates"
    private const val NotificationId = 1001

    private fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(ChannelId) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                "Pembaruan",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Versi baru Musiku tersedia"
            },
        )
    }

    fun show(ctx: Context, info: UpdateInfo) {
        if (!canPost(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = "Ketuk untuk mengunduh dan memasangnya dari dalam aplikasi."
        val notification = NotificationCompat.Builder(ctx, ChannelId)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("Musiku ${info.version} tersedia")
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    info.notes?.takeIf { it.isNotBlank() }?.let { "$body\n\n$it" } ?: body,
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NotificationId, notification) }
    }

    /** The card is on screen now, so the status bar copy has done its job. */
    fun cancel(ctx: Context) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(NotificationId) }
    }
}
