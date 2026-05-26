package com.volttrack.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insert_returnsRowId_and_flow_emitsSession(): Unit = runBlocking {
        val dao = db.sessionDao()
        val session = ChargingSession(
            startTime = 1000L,
            endTime = 2000L,
            startPct = 10.0,
            endPct = 20.0,
            maxWatts = 8.0,
            chargeCompletedAtMs = null
        )
        val rowId = dao.insert(session)
        assertThat(rowId).isGreaterThan(0L)
        val all = dao.getAll().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].startTime).isEqualTo(1000L)
    }
}
