package com.example.memgallery.lifeagent

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.memgallery.data.local.database.AppDatabase
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room (in memory) under Robolectric, so the DAO SQL is really run. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
abstract class DbTestBase {
    protected lateinit var db: AppDatabase
    protected val dao get() = db.lifeAgentDao()

    @Before fun openDb() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun closeDb() { db.close() }
}
