package com.example.memgallery.lifeagent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LifeAgentDao {

    // connections
    @Query("SELECT * FROM life_connections")
    abstract fun observeConnections(): Flow<List<LifeConnectionEntity>>

    @Query("SELECT * FROM life_connections")
    abstract suspend fun getConnections(): List<LifeConnectionEntity>

    @Query("SELECT * FROM life_connections WHERE id = :id")
    abstract suspend fun getConnection(id: String): LifeConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertConnection(c: LifeConnectionEntity)

    // items
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertItems(items: List<LifeItemEntity>): List<Long>

    @Query("SELECT * FROM life_items WHERE app = :app AND sourceId = :sourceId LIMIT 1")
    abstract suspend fun findItem(app: String, sourceId: String): LifeItemEntity?

    @Query("SELECT fingerprint FROM life_items WHERE fingerprint IN (:fps)")
    abstract suspend fun knownItemFingerprints(fps: List<String>): List<String>

    @Query("SELECT fingerprint FROM life_proposals WHERE fingerprint IN (:fps)")
    abstract suspend fun knownProposalFingerprints(fps: List<String>): List<String>

    // proposals
    @Query("SELECT * FROM life_proposals WHERE status = 'proposed' ORDER BY collectedAt DESC, confidence DESC")
    abstract fun observeProposed(): Flow<List<LifeProposalEntity>>

    @Query("SELECT * FROM life_proposals ORDER BY collectedAt DESC")
    abstract fun observeAllProposals(): Flow<List<LifeProposalEntity>>

    @Query("SELECT * FROM life_proposals WHERE status = 'kept' ORDER BY collectedAt DESC")
    abstract fun observeKept(): Flow<List<LifeProposalEntity>>

    @Query("SELECT COUNT(*) FROM life_proposals WHERE status = 'proposed'")
    abstract fun observeProposedCount(): Flow<Int>

    @Query("SELECT * FROM life_proposals WHERE id = :id")
    abstract suspend fun getProposal(id: String): LifeProposalEntity?

    @Query("SELECT * FROM life_proposals WHERE status = 'proposed'")
    abstract suspend fun getProposed(): List<LifeProposalEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertProposals(list: List<LifeProposalEntity>): List<Long>

    @Query("UPDATE life_proposals SET status = :status, decidedAt = :at WHERE id = :id")
    abstract suspend fun setProposalStatus(id: String, status: String, at: Long)

    @Query("SELECT * FROM life_proposals WHERE status = 'kept' AND personName = :name COLLATE NOCASE ORDER BY collectedAt DESC")
    abstract fun observeKeptForPerson(name: String): Flow<List<LifeProposalEntity>>

    // people
    @Query("SELECT * FROM life_people ORDER BY lastSeenAt DESC")
    abstract fun observePeople(): Flow<List<LifePersonEntity>>

    @Query("SELECT * FROM life_people WHERE id = :id")
    abstract fun observePerson(id: String): Flow<LifePersonEntity?>

    @Query("SELECT * FROM life_people WHERE id = :id")
    abstract suspend fun getPerson(id: String): LifePersonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPerson(p: LifePersonEntity)

    // tasks
    @Query("SELECT * FROM life_tasks ORDER BY CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt")
    abstract fun observeTasks(): Flow<List<LifeTaskEntity>>

    @Query("SELECT * FROM life_tasks")
    abstract suspend fun getTasks(): List<LifeTaskEntity>

    @Query("SELECT * FROM life_tasks WHERE id = :id")
    abstract suspend fun getTask(id: String): LifeTaskEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTask(t: LifeTaskEntity): Long

    @Query("UPDATE life_tasks SET listName = :listName, title = :title, dueAt = :dueAt, status = :status, url = :url WHERE id = :id")
    abstract suspend fun updateTaskRemote(id: String, listName: String?, title: String, dueAt: Long?, status: String, url: String?)

    /** Insert, or refresh the fields the source owns while keeping the local done and snooze state. */
    @Transaction
    open suspend fun upsertRemoteTask(t: LifeTaskEntity) {
        if (insertTask(t) == -1L) updateTaskRemote(t.id, t.listName, t.title, t.dueAt, t.status, t.url)
    }

    @Query("UPDATE life_tasks SET doneLocal = :done WHERE id = :id")
    abstract suspend fun setTaskDone(id: String, done: Boolean)

    @Query("UPDATE life_tasks SET snoozedUntil = :until WHERE id = :id")
    abstract suspend fun setTaskSnooze(id: String, until: Long?)

    // meetings
    @Query("SELECT * FROM life_meetings WHERE endAt >= :from ORDER BY startAt")
    abstract fun observeMeetingsFrom(from: Long): Flow<List<LifeMeetingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMeetings(list: List<LifeMeetingEntity>)

    @Query("DELETE FROM life_meetings WHERE endAt < :before")
    abstract suspend fun deleteMeetingsEndedBefore(before: Long)

    // runs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRun(r: LifeRunEntity)

    @Query("SELECT * FROM life_runs ORDER BY startedAt DESC LIMIT 1")
    abstract fun observeLatestRun(): Flow<LifeRunEntity?>

    @Query("SELECT COUNT(*) FROM life_runs WHERE status = 'running' AND startedAt > :since")
    abstract suspend fun countRunningSince(since: Long): Int

    @Query("DELETE FROM life_meetings WHERE endAt >= :from")
    abstract suspend fun deleteMeetingsFrom(from: Long)

    // learning
    @Query("SELECT * FROM life_learning")
    abstract suspend fun getLearning(): List<LifeLearningEntity>

    @Insert
    abstract suspend fun insertLearning(l: LifeLearningEntity)

    @Query("SELECT COUNT(*) FROM life_learning WHERE decision = 'drop' AND app = :app AND senderKey = :senderKey")
    abstract suspend fun countSenderDrops(app: String, senderKey: String): Int

    @Query("SELECT COUNT(*) FROM life_learning WHERE decision = 'keep' AND app = :app AND senderKey = :senderKey")
    abstract suspend fun countSenderKeeps(app: String, senderKey: String): Int

    @Query("SELECT COUNT(*) FROM life_learning WHERE decision = 'drop' AND kind = :kind")
    abstract suspend fun countKindDrops(kind: String): Int

    @Query("SELECT COUNT(*) FROM life_learning WHERE decision = 'keep' AND kind = :kind")
    abstract suspend fun countKindKeeps(kind: String): Int

    @Query("SELECT COUNT(*) FROM life_learning WHERE decision = 'drop'")
    abstract fun observeDropCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM life_learning WHERE decision = 'keep'")
    abstract fun observeKeepCount(): Flow<Int>
}
