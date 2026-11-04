package com.example.memgallery.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.memgallery.data.local.converters.TagListConverter
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.dao.TaskDao
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.local.entity.TaskEntity

import com.example.memgallery.data.local.dao.CollectionDao
import com.example.memgallery.data.local.entity.CollectionEntity
import com.example.memgallery.data.local.entity.MemoryCollectionCrossRef

@Database(
    entities = [
        MemoryEntity::class, 
        TaskEntity::class, 
        CollectionEntity::class, 
        MemoryCollectionCrossRef::class,
        com.example.memgallery.data.local.entity.ChatEntity::class,
        com.example.memgallery.data.local.entity.ChatMessageEntity::class,
        com.example.memgallery.data.local.entity.AgentClientEntity::class,
        com.example.memgallery.data.local.entity.AgentAskEntity::class,
        com.example.memgallery.data.local.entity.AgentEventEntity::class,
        com.example.memgallery.data.local.entity.AgentWebhookEntity::class,
        com.example.memgallery.data.local.entity.AgentAuditEntity::class
    ],
    version = 18,
    exportSchema = false
)
@TypeConverters(TagListConverter::class, com.example.memgallery.data.local.converters.ActionListConverter::class, com.example.memgallery.data.local.converters.IntListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun taskDao(): TaskDao
    abstract fun collectionDao(): CollectionDao
    abstract fun chatDao(): com.example.memgallery.data.local.dao.ChatDao
    abstract fun agentClientDao(): com.example.memgallery.data.local.dao.AgentClientDao
    abstract fun agentAskDao(): com.example.memgallery.data.local.dao.AgentAskDao
    abstract fun agentOutboxDao(): com.example.memgallery.data.local.dao.AgentOutboxDao
    abstract fun agentAuditDao(): com.example.memgallery.data.local.dao.AgentAuditDao

    companion object {
        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add imageUris column
                try {
                    database.execSQL("ALTER TABLE memories ADD COLUMN imageUris TEXT")
                    
                    // Migrate existing imageUri to imageUris as a JSON list
                    // We use a simple string concatenation assuming URIs don't contain double quotes
                    database.execSQL("UPDATE memories SET imageUris = '[\"' || imageUri || '\"]' WHERE imageUri IS NOT NULL AND imageUri != ''")
                } catch (e: Exception) {
                    android.util.Log.e("Migration_14_15", "Error migrating imageUris", e)
                }
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE memories ADD COLUMN bookmarkUrl TEXT")
                database.execSQL("ALTER TABLE memories ADD COLUMN bookmarkTitle TEXT")
                database.execSQL("ALTER TABLE memories ADD COLUMN bookmarkDescription TEXT")
                database.execSQL("ALTER TABLE memories ADD COLUMN bookmarkImageUrl TEXT")
                database.execSQL("ALTER TABLE memories ADD COLUMN bookmarkFaviconUrl TEXT")
            }
        }
        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE tasks ADD COLUMN creationTimestamp INTEGER NOT NULL DEFAULT 0")
                } catch (e: android.database.sqlite.SQLiteException) {
                    if (e.message?.contains("duplicate column name") == true) {
                        android.util.Log.w("Migration_9_10", "Column 'creationTimestamp' already exists. Skipping.")
                    } else {
                        throw e
                    }
                }
            }
        }
        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add isApproved column to tasks, default to 1 (true) for existing tasks
                database.execSQL("ALTER TABLE tasks ADD COLUMN isApproved INTEGER NOT NULL DEFAULT 1")
                
                // Create collections table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `collections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `description` TEXT NOT NULL, 
                        `creationTimestamp` INTEGER NOT NULL
                    )
                """)
                
                // Create memory_collection_cross_ref table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `memory_collection_cross_ref` (
                        `memoryId` INTEGER NOT NULL, 
                        `collectionId` INTEGER NOT NULL, 
                        PRIMARY KEY(`memoryId`, `collectionId`),
                        FOREIGN KEY(`memoryId`) REFERENCES `memories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                """)
                
                // Create indices for cross ref
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_collection_cross_ref_memoryId` ON `memory_collection_cross_ref` (`memoryId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_collection_cross_ref_collectionId` ON `memory_collection_cross_ref` (`collectionId`)")
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add type, isRecurring, recurrenceRule to tasks if they don't exist
                // We use try-catch for each column to handle "duplicate column name" error 
                // which happens if user did a fresh install on v11 (which created table with these columns)
                // but DB version was 11, so now going to 12 checks this migration.

                val columns = listOf(
                    "ALTER TABLE tasks ADD COLUMN type TEXT NOT NULL DEFAULT 'TODO'",
                    "ALTER TABLE tasks ADD COLUMN isRecurring INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE tasks ADD COLUMN recurrenceRule TEXT",
                    "ALTER TABLE tasks ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'",
                    "ALTER TABLE tasks ADD COLUMN priority TEXT NOT NULL DEFAULT 'MEDIUM'"
                )

                for (sql in columns) {
                    try {
                        database.execSQL(sql)
                    } catch (e: android.database.sqlite.SQLiteException) {
                        // Check for duplicate column error (message varies by SQLite version/device, but usually contains "duplicate column name")
                        if (e.message?.contains("duplicate column name") == true) {
                            android.util.Log.w("Migration_11_12", "Column already exists, skipping: ${e.message}")
                        } else {
                            // Re-throw other errors
                            throw e
                        }
                    }
                }
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create chats table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chats` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `summary` TEXT, 
                        `timestamp` INTEGER NOT NULL, 
                        `isSavedAsMemory` INTEGER NOT NULL
                    )
                """)

                // Create chat_messages table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `chatId` INTEGER NOT NULL, 
                        `role` TEXT NOT NULL, 
                        `content` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)

                // Create index for chat_messages
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_chatId` ON `chat_messages` (`chatId`)")

                // Add columns to memories table
                try {
                    database.execSQL("ALTER TABLE memories ADD COLUMN type TEXT NOT NULL DEFAULT 'MEMORY'")
                    database.execSQL("ALTER TABLE memories ADD COLUMN chatExport TEXT")
                } catch (e: android.database.sqlite.SQLiteException) {
                    if (e.message?.contains("duplicate column name") == true) {
                        android.util.Log.w("Migration_12_13", "Column already exists, skipping: ${e.message}")
                    } else {
                        throw e
                    }
                }
            }
        }

        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add media columns to chat_messages table
                try {
                    database.execSQL("ALTER TABLE chat_messages ADD COLUMN audioFilePath TEXT")
                    database.execSQL("ALTER TABLE chat_messages ADD COLUMN imageUri TEXT")
                } catch (e: android.database.sqlite.SQLiteException) {
                    if (e.message?.contains("duplicate column name") == true) {
                        android.util.Log.w("Migration_13_14", "Column already exists, skipping: ${e.message}")
                    } else {
                        throw e
                    }
                }
            }
        }

        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add displayedMemoryIds column to chat_messages table
                try {
                    database.execSQL("ALTER TABLE chat_messages ADD COLUMN displayedMemoryIds TEXT")
                } catch (e: android.database.sqlite.SQLiteException) {
                    if (e.message?.contains("duplicate column name") == true) {
                        android.util.Log.w("Migration_15_16", "Column already exists, skipping: ${e.message}")
                    } else {
                        throw e
                    }
                }
            }
        }

        /**
         * v18 — Agent Bridge.
         *
         * Additive only: five new agent tables, project semantics on `collections`, and
         * provenance/sync columns on `tasks`. Nothing existing is rewritten, so a failed
         * upgrade can never lose memories.
         */
        val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                val addColumns = listOf(
                    // tasks: provenance + project link + sync clock
                    "ALTER TABLE tasks ADD COLUMN source TEXT NOT NULL DEFAULT 'USER'",
                    "ALTER TABLE tasks ADD COLUMN externalId TEXT",
                    "ALTER TABLE tasks ADD COLUMN projectId INTEGER",
                    "ALTER TABLE tasks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0",
                    // collections become projects
                    "ALTER TABLE collections ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'",
                    "ALTER TABLE collections ADD COLUMN color INTEGER",
                    "ALTER TABLE collections ADD COLUMN targetDate TEXT",
                    "ALTER TABLE collections ADD COLUMN archived INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE collections ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0"
                )
                for (sql in addColumns) {
                    try {
                        database.execSQL(sql)
                    } catch (e: android.database.sqlite.SQLiteException) {
                        if (e.message?.contains("duplicate column name") == true) {
                            android.util.Log.w("Migration_17_18", "Column already exists, skipping: ${e.message}")
                        } else {
                            throw e
                        }
                    }
                }

                // Existing tasks have never been "updated"; seed the clock from creation so
                // the first mirror sync doesn't treat every row as brand new.
                try {
                    database.execSQL("UPDATE tasks SET updatedAt = creationTimestamp WHERE updatedAt = 0")
                } catch (e: Exception) {
                    android.util.Log.e("Migration_17_18", "Failed to backfill tasks.updatedAt", e)
                }

                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_projectId` ON `tasks` (`projectId`)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `agent_clients` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `tokenHash` TEXT NOT NULL,
                        `tokenSalt` TEXT NOT NULL,
                        `tokenPrefix` TEXT NOT NULL,
                        `scopes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastSeenAt` INTEGER,
                        `revoked` INTEGER NOT NULL,
                        `dailyAiQuota` INTEGER NOT NULL,
                        `aiUsedToday` INTEGER NOT NULL,
                        `quotaDay` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_clients_tokenPrefix` ON `agent_clients` (`tokenPrefix`)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `agent_asks` (
                        `id` TEXT NOT NULL,
                        `clientId` TEXT,
                        `askedBy` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `question` TEXT NOT NULL,
                        `contextText` TEXT,
                        `optionsJson` TEXT,
                        `answer` TEXT,
                        `status` TEXT NOT NULL,
                        `priority` TEXT NOT NULL,
                        `deepLink` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER,
                        `answeredAt` INTEGER,
                        `notificationId` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_asks_status` ON `agent_asks` (`status`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_asks_clientId` ON `agent_asks` (`clientId`)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `agent_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `entityId` TEXT,
                        `payloadJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `deliveredAt` INTEGER,
                        `attempts` INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_events_createdAt` ON `agent_events` (`createdAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_events_deliveredAt` ON `agent_events` (`deliveredAt`)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `agent_webhooks` (
                        `id` TEXT NOT NULL,
                        `clientId` TEXT,
                        `url` TEXT NOT NULL,
                        `eventTypes` TEXT NOT NULL,
                        `secret` TEXT NOT NULL,
                        `active` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastStatus` TEXT,
                        `lastAttemptAt` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_webhooks_clientId` ON `agent_webhooks` (`clientId`)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `agent_audit` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `clientId` TEXT,
                        `clientName` TEXT NOT NULL,
                        `action` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT,
                        `detail` TEXT,
                        `at` INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_audit_at` ON `agent_audit` (`at`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_audit_clientId` ON `agent_audit` (`clientId`)")
            }
        }

        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add originalMediaUri column
                try {
                    database.execSQL("ALTER TABLE memories ADD COLUMN originalMediaUri TEXT")
                } catch (e: android.database.sqlite.SQLiteException) {
                    if (e.message?.contains("duplicate column name") == true) {
                        android.util.Log.w("Migration_16_17", "Column originalMediaUri already exists. Skipping.")
                    } else {
                        throw e
                    }
                }
            }
        }
    }
}
