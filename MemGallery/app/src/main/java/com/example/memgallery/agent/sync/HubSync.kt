package com.example.memgallery.agent.sync

import android.util.Log
import com.example.memgallery.agent.AgentApiImpl
import com.example.memgallery.agent.AgentBridgeSettings
import com.example.memgallery.agent.model.AgentClient
import com.example.memgallery.agent.model.AgentScope
import com.example.memgallery.agent.model.ChatQuery
import com.example.memgallery.agent.model.MemoryQuery
import com.example.memgallery.agent.model.ProjectQuery
import com.example.memgallery.agent.model.TaskQuery
import com.example.memgallery.agent.model.TaskStatusFilter
import com.example.memgallery.agent.server.AgentJson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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

private const val TAG = "HubSync"

/**
 * The phone's half of the hub relationship.
 *
 * Two jobs, and it is important they stay separate:
 *
 *  - **Push the mirror.** A compact snapshot — tasks, projects, memory *metadata*, the
 *    digest — so an agent can answer "what is he up to" instantly with the phone in a
 *    drawer. Never bytes; media stays on the device.
 *  - **Drain the outbox.** Commands an agent queued while the phone was unreachable,
 *    executed here and acknowledged. Writes always land on the device, never on the
 *    mirror — the mirror is a cache and is allowed to be wrong.
 *
 * The hub is deliberately dumb: it stores, it queues, it relays. All judgement lives here.
 */
@Singleton
class HubSync @Inject constructor(
    private val api: AgentApiImpl,
    private val settings: AgentBridgeSettings
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One sync at a time: a push racing a drain would ship a mirror that omits its own writes. */
    private val gate = Mutex()

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Internal caller for queued commands: full rights, but still audited under its own name. */
    private val relayClient = AgentClient(
        id = "internal",
        name = "hub-relay",
        scopes = setOf(AgentScope.ADMIN),
        createdAt = 0L
    )

    fun requestDrain(reason: String) {
        scope.launch {
            Log.d(TAG, "Sync requested ($reason)")
            runCatching { syncNow() }.onFailure { Log.w(TAG, "Sync failed: ${it.message}") }
        }
    }

    suspend fun syncNow(): Boolean = gate.withLock {
        val hub = settings.hubUrl.first()?.trimEnd('/') ?: return false
        val token = settings.hubToken.first() ?: return false
        val deviceId = settings.deviceId()

        val drained = drainCommands(hub, token, deviceId)
        if (settings.mirrorEnabled.first()) {
            pushMirror(hub, token, deviceId)
        }
        drained
    }

    // -----------------------------------------------------------------------
    // Mirror
    // -----------------------------------------------------------------------

    private suspend fun pushMirror(hub: String, token: String, deviceId: String) {
        val snapshot = JsonObject().apply {
            addProperty("device_id", deviceId)
            addProperty("as_of", System.currentTimeMillis())
            add("digest", AgentJson.gson.toJsonTree(api.digest(relayClient)))
            add(
                "tasks",
                AgentJson.gson.toJsonTree(
                    api.listTasks(TaskQuery(status = TaskStatusFilter.ANY, limit = 500), relayClient).items
                )
            )
            add(
                "projects",
                AgentJson.gson.toJsonTree(
                    api.listProjects(ProjectQuery(includeArchived = true, withCounts = true, limit = 200), relayClient).items
                )
            )
            // Metadata only, and capped. The mirror exists to answer questions, not to be
            // a second copy of the library.
            add(
                "memories",
                AgentJson.gson.toJsonTree(api.listMemories(MemoryQuery(limit = 200), relayClient).items)
            )
            add(
                "chats",
                AgentJson.gson.toJsonTree(api.listChats(ChatQuery(limit = 50), relayClient).items)
            )
        }

        val request = Request.Builder()
            .url("$hub/device/mirror")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("X-Device-Id", deviceId)
            .post(AgentJson.gson.toJson(snapshot).toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) Log.w(TAG, "Mirror push rejected: ${response.code}")
            }
        }.onFailure { Log.w(TAG, "Mirror push failed: ${it.message}") }
    }

    // -----------------------------------------------------------------------
    // Command outbox
    // -----------------------------------------------------------------------

    private suspend fun drainCommands(hub: String, token: String, deviceId: String): Boolean {
        val request = Request.Builder()
            .url("$hub/device/commands")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("X-Device-Id", deviceId)
            .get()
            .build()

        val body = runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                response.body?.string()
            }
        }.getOrNull() ?: return false

        val commands = runCatching { JsonParser.parseString(body).asJsonObject.getAsJsonArray("commands") }
            .getOrNull() ?: return false
        if (commands.size() == 0) return true

        val results = JsonArray()
        for (element in commands) {
            val command = element.asJsonObject
            val id = command.get("id")?.asString ?: continue
            val op = command.get("op")?.asString.orEmpty()
            val args = command.getAsJsonObject("args") ?: JsonObject()

            val result = JsonObject().apply { addProperty("id", id) }
            try {
                result.add("data", AgentJson.gson.toJsonTree(execute(op, args)))
                result.addProperty("ok", true)
            } catch (e: Exception) {
                // A poison command must not block the queue behind it forever, so the
                // failure is reported and the command is considered handled.
                result.addProperty("ok", false)
                result.addProperty("error", e.message ?: e::class.java.simpleName)
                Log.w(TAG, "Queued command '$op' failed: ${e.message}")
            }
            results.add(result)
        }

        val ack = Request.Builder()
            .url("$hub/device/results")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("X-Device-Id", deviceId)
            .post(
                JsonObject().apply { add("results", results) }
                    .let { AgentJson.gson.toJson(it) }
                    .toRequestBody("application/json".toMediaType())
            )
            .build()

        runCatching { http.newCall(ack).execute().close() }
            .onFailure { Log.w(TAG, "Could not acknowledge results: ${it.message}") }
        return true
    }

    /**
     * Queued commands are semantic, not raw HTTP. Replaying a URL would mean reimplementing
     * routing, parsing and scope checks on the hub; naming the operation keeps every one of
     * those decisions on the device where the data actually is.
     *
     * Reads are absent on purpose — those are what the mirror is for.
     */
    private suspend fun execute(op: String, args: JsonObject): Any? = when (op) {
        "create_task" -> api.createTask(AgentJson.createTask(args), relayClient)
        "update_task" -> api.updateTask(args.requireInt("id"), AgentJson.taskPatch(args), relayClient)
        "complete_task" -> api.completeTask(args.requireInt("id"), relayClient)
        "delete_task" -> api.deleteTask(args.requireInt("id"), relayClient)
        "approve_task" -> api.approveTask(args.requireInt("id"), relayClient)
        "reject_task" -> api.rejectTask(args.requireInt("id"), relayClient)
        "create_project" -> api.createProject(AgentJson.createProject(args), relayClient)
        "update_project" -> api.updateProject(args.requireInt("id"), AgentJson.projectPatch(args), relayClient)
        "create_memory" -> api.createMemory(AgentJson.createMemory(args), relayClient)
        "notify" -> api.notify(AgentJson.notifyRequest(args), relayClient)
        "ask" -> api.ask(AgentJson.askRequest(args), relayClient)
        else -> throw IllegalArgumentException("Unknown queued operation '$op'.")
    }

    private fun JsonObject.requireInt(name: String): Int =
        get(name)?.takeIf { !it.isJsonNull }?.asInt
            ?: throw IllegalArgumentException("'$name' is required.")
}
