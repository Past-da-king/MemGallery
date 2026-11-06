package com.example.memgallery.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Life Agent v1 catalog cache: the toolkit list that Connections opens from, so it works offline. */
val MIGRATION_19_20: Migration = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `life_toolkits` (" +
                "`slug` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `logoUrl` TEXT, " +
                "`categories` TEXT, `authSchemes` TEXT, `noAuth` INTEGER NOT NULL DEFAULT 0, " +
                "`toolkitType` TEXT, `readTools` TEXT, `fetchedAt` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`slug`))"
        )
    }
}
