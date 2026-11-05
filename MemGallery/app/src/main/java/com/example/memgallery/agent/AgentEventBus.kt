package com.example.memgallery.agent

import android.util.Log
import com.example.memgallery.agent.model.AgentEvent
import com.example.memgallery.agent.model.AgentEventType
import com.example.memgallery.agent.server.AgentJson
import com.example.memgallery.data.local.dao.AgentOutboxDao
import com.example.memgallery.data.local.entity.AgentEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AgentEventBus"

/**
 * The single way anything leaves the device.
 *
 * Every mutation calls [emit] exactly once. The event is persisted to the outbox first,
 * then fanned out live to SSE subscribers, then pushed to webhooks. Persisting first is
 * what makes a dropped SSE connection recoverable: a client reconnects with `after_id`
 * and replays what it missed instead of silently losing it.
 */
@Singleton
class AgentEventBus @Inject constructor(
    private val outboxDao: AgentOutboxDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _live = MutableSharedFlow<AgentEvent>(replay = 0, extraBufferCapacity = 128)
    val live: Flow<AgentEvent> = _live.asSharedFlow()

    /** Serialises writes so outbox ids stay monotonic under concurrent mutations. */
    private val writeLock = Mutex()

    private val webhookClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** Called after a successful outbox write, so the push tier can wake a hub drain. */
    var onEventPersisted: (suspend (AgentEvent) -> Unit)? = null

    suspend fun emit(type: AgentEventType, entityId: String? = null, payload: Any? = null) {
        if (type == AgentEventType.HEARTBEAT) return // never persisted; see AgentServer
        val json = try {
            AgentJson.toJson(payload ?: emptyMap<String, Any>())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialise payload for ${type.wire}", e)
            "{}"
        }

        val event = writeLock.withLock {
            val entity = AgentEventEntity(
                type = type.wire,
                entityId = entityId,
                payloadJson = json,
                createdAt = System.currentTimeMillis()
            )
            val id = outboxDao.insertEvent(entity)
            AgentEvent(id = id, type = type, at = entity.createdAt, entityId = entityId, payloadJson = json)
        }

        _live.tryEmit(event)
        scope.launch { dispatchWebhooks(event) }
        scope.launch {
            try {
                onEventPersisted?.invoke(event)
            } catch (e: Exception) {
                Log.w(TAG, "Push hook failed for ${type.wire}", e)
            }
        }
    }

    /**
     * Replays anything after [afterId] from the outbox, then continues live.
     *
     * The replay/live handover can duplicate an event that lands mid-switch. Callers
     * de-duplicate on [AgentEvent.id], which is cheap — the alternative, holding a lock
     * across the handover, would stall every writer behind the slowest SSE consumer.
     */
    fun stream(types: Set<AgentEventType>, afterId: Long = 0L): Flow<AgentEvent> = flow {
        var lastId = afterId
        if (afterId > 0) {
            var batch = outboxDao.eventsAfter(lastId, 200)
            while (batch.isNotEmpty()) {
                for (entity in batch) {
                    val event = AgentMappers.toAgentEvent(entity) ?: continue
                    if (event.type in types) emit(event)
                    lastId = entity.id
                }
                batch = outboxDao.eventsAfter(lastId, 200)
            }
        }
        val seenUpTo = lastId
        live.filter { it.type in types && it.id > seenUpTo }.collect { emit(it) }
    }

    private suspend fun dispatchWebhooks(event: AgentEvent) {
        val hooks = try {
            outboxDao.activeWebhooks()
        } catch (e: Exception) {
            Log.e(TAG, "Could not read webhooks", e)
            return
        }
        if (hooks.isEmpty()) {
            outboxDao.markDelivered(listOf(event.id), System.currentTimeMillis())
            return
        }

        val body = buildDeliveryBody(event)
        var anyDelivered = false

        for (hook in hooks) {
            val subscribed = AgentEventType.expand(
                hook.eventTypes.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            )
            if (event.type !in subscribed) continue

            val signature = "sha256=" + AgentTokens.hmacSha256Hex(hook.secret, body)
            val request = Request.Builder()
                .url(hook.url)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-MemGallery-Signature", signature)
                .addHeader("X-MemGallery-Event", event.type.wire)
                .addHeader("X-MemGallery-Delivery", event.id.toString())
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val status = try {
                webhookClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) anyDelivered = true
                    response.code.toString()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Webhook ${hook.url} failed: ${e.message}")
                "error: ${e.message?.take(80)}"
            }
            outboxDao.recordWebhookAttempt(hook.id, status, System.currentTimeMillis())
        }

        if (anyDelivered) {
            outboxDao.markDelivered(listOf(event.id), System.currentTimeMillis())
        } else {
            outboxDao.bumpAttempts(event.id)
        }
    }

    private fun buildDeliveryBody(event: AgentEvent): String {
        val obj = com.google.gson.JsonObject()
        obj.addProperty("id", event.id)
        obj.addProperty("type", event.type.wire)
        obj.addProperty("at", event.at)
        obj.addProperty("entity_id", event.entityId)
        obj.add("payload", parsePayload(event.payloadJson))
        return AgentJson.gson.toJson(obj)
    }

    /** Payloads are stored as JSON text; re-parse so consumers get an object, not a string. */
    private fun parsePayload(json: String): com.google.gson.JsonElement = try {
        com.google.gson.JsonParser.parseString(json)
    } catch (e: Exception) {
        com.google.gson.JsonObject()
    }

    /** Housekeeping: delivered events older than a day are dead weight. */
    suspend fun prune() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        try {
            outboxDao.pruneDeliveredBefore(cutoff)
        } catch (e: Exception) {
            Log.w(TAG, "Prune failed", e)
        }
    }
}
