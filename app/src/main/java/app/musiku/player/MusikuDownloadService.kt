package app.musiku.player

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import app.musiku.MusikuApp
import app.musiku.R

@UnstableApi
class MusikuDownloadService : DownloadService(
    NOTIFICATION_ID,
    1000L,
    CHANNEL_ID,
    R.string.downloading,
    0,
) {
    private val downloads get() = (application as MusikuApp).downloads

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ALL) {
            downloadManager.currentDownloads.forEach { d ->
                downloadManager.removeDownload(d.request.id)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun getDownloadManager(): DownloadManager = downloads.downloadManager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        val label = if (downloads.size == 1) {
            Util.fromUtf8Bytes(downloads[0].request.data).ifBlank { getString(R.string.downloading) }
        } else {
            "${downloads.size} lagu"
        }
        val base = this.downloads.notificationHelper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            label,
            downloads,
            notMetRequirements,
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return base
        val cancelIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MusikuDownloadService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder.recoverBuilder(this, base)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    getString(android.R.string.cancel),
                    cancelIntent,
                ).build(),
            )
            .build()
    }

    companion object {
        const val CHANNEL_ID = "musiku_downloads"
        private const val NOTIFICATION_ID = 4201
        private const val JOB_ID = 4202
        private const val ACTION_CANCEL_ALL = "app.musiku.action.CANCEL_ALL_DOWNLOADS"
    }
}
