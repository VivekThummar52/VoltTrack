package com.volttrack.app.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.volttrack.app.data.ChargingSessionRecorder

class FinalizeSessionWorker(appContext: Context, params: WorkerParameters) : 
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // We explicitly use a coroutine that waits for the DB insert
            ChargingSessionRecorder.finalizeSession(applicationContext, isOrphaned = true)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}