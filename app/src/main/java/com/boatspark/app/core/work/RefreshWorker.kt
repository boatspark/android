package com.boatspark.app.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.boatspark.app.BoatSparkApplication
import com.boatspark.app.core.auth.AuthenticationRequiredException
import com.boatspark.app.core.network.ApiFailure

class RefreshWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as BoatSparkApplication).container
        return try {
            container.boatRepository.refreshAll()
            Result.success()
        } catch (_: AuthenticationRequiredException) {
            Result.failure()
        } catch (_: ApiFailure.Authentication) {
            Result.failure()
        } catch (_: ApiFailure.Authorization) {
            Result.failure()
        } catch (_: ApiFailure.Validation) {
            Result.failure()
        } catch (_: ApiFailure.InvalidResponse) {
            Result.failure()
        } catch (_: ApiFailure.Connectivity) {
            Result.retry()
        } catch (_: ApiFailure.Server) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

