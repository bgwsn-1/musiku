package app.musiku.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background release check. The in-app check only runs while someone opens Musiku, so an update
 * published on a day the app stays closed would go unnoticed; this wakes up on a schedule and
 * posts one status-bar notification per version.
 */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val current = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull().orEmpty()
        if (current.isBlank()) return Result.success()

        val info = runCatching { checkForUpdate(current) }.getOrNull() ?: return Result.success()
        // "Nanti" is for the in-app card; the notification still gets one shot per version.
        if (notifiedUpdateVersion(ctx) == info.version) return Result.success()

        UpdateNotifier.show(ctx, info)
        markUpdateNotified(ctx, info.version)
        return Result.success()
    }

    companion object {
        private const val Name = "musiku-update-check"

        /** Scheduled once per launch; `KEEP` leaves the existing schedule untouched. */
        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            runCatching {
                WorkManager.getInstance(ctx)
                    .enqueueUniquePeriodicWork(Name, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
}
