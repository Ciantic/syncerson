package com.oksidi.syncerson

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodic trigger: when the timer fires, enqueues the real one-shot
 * SyncWorker via [enqueueSyncWorker] so it shares the same namespace
 * as manual "Run now" and power-connected triggers.
 */
class PeriodicWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        enqueueSyncWorker(applicationContext)
        return Result.success()
    }
}
