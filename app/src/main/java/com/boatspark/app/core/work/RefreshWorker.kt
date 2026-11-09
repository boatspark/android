package com.boatspark.app.core.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.boatspark.app.BoatSparkApplication
import com.boatspark.app.core.auth.AuthenticationRequiredException
import com.boatspark.app.core.network.ApiFailure
import kotlinx.coroutines.CancellationException

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
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Background refresh failed", error)
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "RefreshWorker"
    }
}
