package com.boatspark.app.core.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.boatspark.app.core.work.RefreshWorker
import java.util.concurrent.TimeUnit

class RefreshScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun schedule() {
        // Only the offline cache uses this data, and the app refreshes when opened, so run
        // hourly and skip low-battery periods. The foreground refresh already runs at sign-in.
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "boatspark-data-refresh"
    }
}

