package com.inacap.health.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.inacap.health.HealthApp
import com.inacap.health.data.remote.ErrorNube

/** Envía la bandeja de salida cuando hay internet. WorkManager lo reintenta si falla. */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HealthApp
        return try {
            app.sync.procesar()
            Result.success()
        } catch (e: ErrorNube) {
            if (e.transitorio && runAttemptCount < MAX_REINTENTOS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_REINTENTOS = 8
    }
}
