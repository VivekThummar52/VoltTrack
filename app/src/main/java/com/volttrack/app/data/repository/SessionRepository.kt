package com.volttrack.app.data.repository

import android.content.Context
import com.volttrack.app.data.AppDatabase
import com.volttrack.app.data.ChargingSession
import kotlinx.coroutines.flow.Flow

/**
 * Single place for session persistence reads. (Writes stay in [ChargingSessionRecorder] + Room insert.)
 */
class SessionRepository(context: Context) {
    private val appContext = context.applicationContext

    private val sessionDao get() = AppDatabase.getDatabase(appContext).sessionDao()

    fun observeSessions(): Flow<List<ChargingSession>> = sessionDao.getAll()
}
