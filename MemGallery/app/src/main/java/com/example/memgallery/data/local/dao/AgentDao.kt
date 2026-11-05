package com.example.memgallery.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.memgallery.data.local.entity.AgentAskEntity
import com.example.memgallery.data.local.entity.AgentAuditEntity
import com.example.memgallery.data.local.entity.AgentClientEntity
import com.example.memgallery.data.local.entity.AgentEventEntity
import com.example.memgallery.data.local.entity.AgentWebhookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentClientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(client: AgentClientEntity)

    @Update
    suspend fun update(client: AgentClientEntity)

    @Query("SELECT * FROM agent_clients ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AgentClientEntity>>

    @Query("SELECT * FROM agent_clients ORDER BY createdAt DESC")
    suspend fun getAll(): List<AgentClientEntity>

    @Query("SELECT * FROM agent_clients WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AgentClientEntity?

    /**
     * Token lookup is by prefix, then the candidates are verified in constant time by
     * hashing the presented token against each row's salt. Never match on the hash directly.
     */
    @Query("SELECT * FROM agent_clients WHERE tokenPrefix = :prefix AND revoked = 0")
    suspend fun findByPrefix(prefix: String): List<AgentClientEntity>

    @Query("SELECT COUNT(*) FROM agent_clients WHERE revoked = 0")
    suspend fun countActive(): Int

    @Query("UPDATE agent_clients SET lastSeenAt = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)

    @Query("UPDATE agent_clients SET revoked = 1 WHERE id = :id")
    suspend fun revoke(id: String)

    @Query("UPDATE agent_clients SET aiUsedToday = :used, quotaDay = :day WHERE id = :id")
    suspend fun setQuotaUsage(id: String, used: Int, day: String)

    @Query("DELETE FROM agent_clients WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AgentAskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ask: AgentAskEntity)

    @Query("SELECT * FROM agent_asks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AgentAskEntity?

    @Query("SELECT * FROM agent_asks WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<AgentAskEntity?>

    @Query("SELECT * FROM agent_asks WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun observePending(): Flow<List<AgentAskEntity>>

    @Query("SELECT COUNT(*) FROM agent_asks WHERE status = 'PENDING'")
    suspend fun countPending(): Int

    @Query("SELECT * FROM agent_asks ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AgentAskEntity>>

    @Query("UPDATE agent_asks SET answer = :answer, status = 'ANSWERED', answeredAt = :at WHERE id = :id AND status = 'PENDING'")
    suspend fun answer(id: String, answer: String, at: Long): Int

    @Query("UPDATE agent_asks SET status = 'EXPIRED' WHERE status = 'PENDING' AND expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun expireOverdue(now: Long): Int

    @Query("UPDATE agent_asks SET status = 'DISMISSED' WHERE id = :id AND status = 'PENDING'")
    suspend fun dismiss(id: String)

    @Query("DELETE FROM agent_asks WHERE status != 'PENDING' AND createdAt < :before")
    suspend fun pruneBefore(before: Long)
}

/** Events plus their delivery targets — both are the same concern: getting a change out of the device. */
@Dao
interface AgentOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: AgentEventEntity): Long

    @Query("SELECT * FROM agent_events WHERE id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun eventsAfter(afterId: Long, limit: Int): List<AgentEventEntity>

    @Query("SELECT * FROM agent_events WHERE deliveredAt IS NULL ORDER BY id ASC LIMIT :limit")
    suspend fun undelivered(limit: Int): List<AgentEventEntity>

    @Query("UPDATE agent_events SET deliveredAt = :at WHERE id IN (:ids)")
    suspend fun markDelivered(ids: List<Long>, at: Long)

    @Query("UPDATE agent_events SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bumpAttempts(id: Long)

    @Query("DELETE FROM agent_events WHERE deliveredAt IS NOT NULL AND createdAt < :before")
    suspend fun pruneDeliveredBefore(before: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWebhook(hook: AgentWebhookEntity)

    @Query("SELECT * FROM agent_webhooks WHERE active = 1")
    suspend fun activeWebhooks(): List<AgentWebhookEntity>

    @Query("SELECT * FROM agent_webhooks ORDER BY createdAt DESC")
    fun observeWebhooks(): Flow<List<AgentWebhookEntity>>

    @Query("UPDATE agent_webhooks SET lastStatus = :status, lastAttemptAt = :at WHERE id = :id")
    suspend fun recordWebhookAttempt(id: String, status: String, at: Long)

    @Query("DELETE FROM agent_webhooks WHERE id = :id")
    suspend fun deleteWebhook(id: String)
}

@Dao
interface AgentAuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AgentAuditEntity)

    @Query("SELECT * FROM agent_audit ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AgentAuditEntity>>

    @Query("SELECT COUNT(*) FROM agent_audit WHERE clientId = :clientId AND action = :action AND at >= :since")
    suspend fun countSince(clientId: String, action: String, since: Long): Int

    @Query("DELETE FROM agent_audit WHERE at < :before")
    suspend fun pruneBefore(before: Long)
}
