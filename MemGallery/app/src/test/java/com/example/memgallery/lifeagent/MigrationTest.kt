package com.example.memgallery.lifeagent

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.memgallery.data.local.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The migration SQL must produce exactly the tables Room expects, or the app crashes on launch for existing users. */
class MigrationTest : DbTestBase() {
    private val tables = listOf("life_connections", "life_items", "life_proposals", "life_people", "life_tasks", "life_meetings", "life_runs", "life_learning")

    private fun describe(d: SupportSQLiteDatabase, table: String): Pair<Set<List<String>>, Set<Pair<String, Int>>> {
        val cols = mutableSetOf<List<String>>()
        d.query("PRAGMA table_info(`$table`)").use { c ->
            while (c.moveToNext()) cols += listOf(c.getString(1), c.getString(2).uppercase(), c.getInt(3).toString(), c.getInt(5).toString(), c.getString(4) ?: "-")
        }
        val idx = mutableSetOf<Pair<String, Int>>()
        d.query("PRAGMA index_list(`$table`)").use { c ->
            while (c.moveToNext()) { val n = c.getString(1); if (!n.startsWith("sqlite_autoindex")) idx += n to c.getInt(2) }
        }
        return cols to idx
    }

    @Test fun migrationSqlMatchesWhatRoomCreatesForTheEntities() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, o: Int, n: Int) {}
                }).build(),
        )
        val migrated = helper.writableDatabase
        AppDatabase.MIGRATION_18_19.migrate(migrated)
        val room = db.openHelper.writableDatabase
        for (t in tables) {
            val (mc, mi) = describe(migrated, t)
            val (rc, ri) = describe(room, t)
            assertTrue("$t has columns", mc.isNotEmpty())
            assertEquals("$t columns", rc, mc)
            assertEquals("$t indices", ri, mi)
        }
        helper.close()
    }

    @Test fun migrationIsSafeToRunTwiceAndTouchesNothingElse() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(d: SupportSQLiteDatabase) { d.execSQL("CREATE TABLE memories (id INTEGER PRIMARY KEY, x TEXT)"); d.execSQL("INSERT INTO memories VALUES (1, 'keep me')") }
                    override fun onUpgrade(d: SupportSQLiteDatabase, o: Int, n: Int) {}
                }).build(),
        )
        val d = helper.writableDatabase
        AppDatabase.MIGRATION_18_19.migrate(d); AppDatabase.MIGRATION_18_19.migrate(d)
        d.query("SELECT x FROM memories").use { c -> assertTrue(c.moveToFirst()); assertEquals("keep me", c.getString(0)) }
        helper.close()
    }
}
