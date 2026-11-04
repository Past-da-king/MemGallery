package com.example.memgallery.agent.server

import android.content.Context
import android.util.Log
import com.example.memgallery.agent.AgentApiImpl
import com.example.memgallery.agent.AgentBridgeSettings
import com.example.memgallery.agent.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

private const val TAG = "AgentServer"

/**
 * The HTTP face of [com.example.memgallery.agent.AgentApi].
 *
 * Thin on purpose: parse, authenticate, delegate, serialise. No business logic and no
 * scope checks of its own — those belong to the API implementation, so a second
 * transport can't accidentally ship without them.
 */
@Singleton
class AgentServer @Inject constructor(
    // Named `appContext`, not `context`: inside a Ktor route lambda the receiver already
    // has a `context` property (the ApplicationCall), and it silently shadows this one.
    @ApplicationContext private val appContext: Context,
    private val api: AgentApiImpl,
    private val settings: AgentBridgeSettings
) {
    private var engine: ApplicationEngine? = null

    val isRunning: Boolean get() = engine != null

    suspend fun start(): Int {
        stop()

        // Binding with no tokens configured would publish the operator's entire personal
        // database to the network with nothing in front of it.
        if (!api.hasAnyClient()) {
            throw IllegalStateException("Refusing to start: no agent tokens exist yet.")
        }

        val port = settings.port.first()
        val allowLan = settings.allowLan.first()

        val server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            routing {
                route("/v1") { mountRoutes(this, allowLan) }
                // Tolerate clients that forget the version prefix rather than 404ing them.
                mountRoutes(this, allowLan)
            }
        }
        server.start(wait = false)
        engine = server
        Log.i(TAG, "Agent Bridge listening on 0.0.0.0:$port (lan=$allowLan)")
        return port
    }

    fun stop() {
        engine?.let {
            runCatching { it.stop(500, 1500) }
            Log.i(TAG, "Agent Bridge stopped")
        }
        engine = null
    }

    // -----------------------------------------------------------------------
    // Access control
    // -----------------------------------------------------------------------

    /**
     * Loopback and the Tailscale CGNAT range are allowed by default. Everything else needs
     * the operator to have explicitly opened the LAN — because "any device on this wifi"
     * is a very different promise from "my own tailnet".
     */
    private fun addressAllowed(host: String, allowLan: Boolean): Boolean {
        if (host == "127.0.0.1" || host == "::1" || host == "localhost") return true
        if (isTailscale(host)) return true
        if (!allowLan) return false
        return host.startsWith("192.168.") ||
            host.startsWith("10.") ||
            host.startsWith("172.") ||
            host.startsWith("169.254.") ||
            host.startsWith("fe80:") ||
            host.startsWith("fd")
    }

    /**
     * 100.64.0.0/10 — Tailscale's slice of the carrier-grade NAT space — or a MagicDNS
     * name.
     *
     * The `.ts.net` case is not decoration. Ktor's `remoteHost` reverse-resolves the peer,
     * and on a tailnet with MagicDNS that turns a perfectly good `100.86.29.23` into
     * `desktop-mshpvrg-1.tailXXXX.ts.net`, which passes none of the numeric tests. That
     * mismatch rejected every real client while loopback kept working, so it survives only
     * as far as the first device that isn't the phone itself.
     */
    private fun isTailscale(host: String): Boolean {
        if (host.endsWith(".ts.net") || host.endsWith(".ts.net.")) return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return first == 100 && second in 64..127
    }

    private suspend fun ApplicationCall.authenticate(allowLan: Boolean): AgentClient? {
        // Check the raw address first and the resolved name second: `remoteAddress` skips
        // reverse DNS, so it is both faster and the only one guaranteed to be numeric.
        val address = runCatching { request.origin.remoteAddress }.getOrNull().orEmpty()
        val host = runCatching { request.origin.remoteHost }.getOrNull().orEmpty()
        if (!addressAllowed(address, allowLan) && !addressAllowed(host, allowLan)) {
            Log.w(TAG, "Rejected connection from address='$address' host='$host'")
            respondError(
                AgentErrorCode.UNAUTHORIZED,
                "This device only accepts agent connections over Tailscale or loopback. " +
                    "You appear to be connecting from '${address.ifBlank { host }}'. " +
                    "Turn on 'Allow local network' in Agent Access if that is a LAN address.",
                mapOf("peer" to address.ifBlank { host })
            )
            return null
        }
        val header = request.header("Authorization")
        val token = header?.removePrefix("Bearer ")?.trim()
        val client = api.authenticate(token)
        if (client == null) {
            respondError(AgentErrorCode.UNAUTHORIZED, "Missing or invalid bearer token.")
            return null
        }
        return client
    }

    private suspend fun ApplicationCall.respondError(
        code: AgentErrorCode,
        message: String,
        details: Map<String, String> = emptyMap()
    ) {
        respondText(
            AgentJson.error(code, message, details),
            ContentType.Application.Json,
            HttpStatusCode.fromValue(code.httpStatus)
        )
    }

    private suspend fun ApplicationCall.ok(json: String, status: HttpStatusCode = HttpStatusCode.OK) {
        respondText(json, ContentType.Application.Json, status)
    }

    /**
     * Every authenticated handler runs through here, so an unexpected exception becomes a
     * clean 500 with a code instead of a dropped connection the client can't interpret.
     */
    private suspend fun ApplicationCall.handle(
        allowLan: Boolean,
        block: suspend (AgentClient) -> Unit
    ) {
        val client = authenticate(allowLan) ?: return
        try {
            block(client)
        } catch (e: AgentException) {
            respondError(e.code, e.message ?: "Request failed.", e.details)
        } catch (e: Exception) {
            Log.e(TAG, "Unhandled error on ${request.local.uri}", e)
            respondError(AgentErrorCode.INTERNAL, e.message ?: "Internal error.")
        }
    }

    private fun ApplicationCall.intParam(name: String): Int =
        parameters[name]?.toIntOrNull()
            ?: throw AgentException(AgentErrorCode.INVALID_REQUEST, "'$name' must be an integer.")

    private fun ApplicationCall.q(name: String): String? =
        request.queryParameters[name]?.takeIf { it.isNotBlank() }

    private fun ApplicationCall.qInt(name: String): Int? = q(name)?.toIntOrNull()

    private fun ApplicationCall.qBool(name: String): Boolean? = q(name)?.toBooleanStrictOrNull()

    private suspend fun ApplicationCall.body(): com.google.gson.JsonObject =
        AgentJson.parseObject(receiveText())

    // -----------------------------------------------------------------------
    // Routes
    // -----------------------------------------------------------------------

    private fun mountRoutes(route: io.ktor.server.routing.Route, allowLan: Boolean) = with(route) {

        get("/health") {
            call.ok("""{"ok":true,"uptime_ms":${android.os.SystemClock.elapsedRealtime()}}""")
        }

        get("/openapi.json") {
            val spec: String? = runCatching {
                appContext.assets.open("agent-api/openapi.json").bufferedReader().use { reader -> reader.readText() }
            }.getOrNull()
            if (spec == null) {
                call.respondError(AgentErrorCode.NOT_FOUND, "Spec not bundled in this build.")
            } else {
                call.ok(spec)
            }
        }

        get("/meta") {
            call.handle(allowLan) { call.ok(AgentJson.toJson(api.meta())) }
        }

        get("/digest") {
            call.handle(allowLan) { client ->
                call.ok(AgentJson.envelope(api.digest(client)))
            }
        }

        get("/search") {
            call.handle(allowLan) { client ->
                val scope = call.q("scope")
                    ?.split(',')
                    ?.mapNotNull { s -> SearchEntity.entries.firstOrNull { it.wire.equals(s.trim(), true) } }
                    ?.toSet()
                    ?: setOf(SearchEntity.MEMORIES, SearchEntity.TASKS)
                val result = api.search(
                    SearchQuery(
                        text = call.q("q") ?: throw AgentException(AgentErrorCode.INVALID_REQUEST, "'q' is required."),
                        scope = scope,
                        limitPerEntity = call.qInt("limit_per_entity") ?: 10
                    ),
                    client
                )
                call.ok(AgentJson.envelope(result))
            }
        }

        // --- tasks ---

        get("/tasks") {
            call.handle(allowLan) { client ->
                val query = TaskQuery(
                    status = TaskStatusFilter.fromWire(call.q("status")),
                    approved = call.qBool("approved"),
                    type = TaskType.fromWire(call.q("type")),
                    priority = TaskPriority.fromWire(call.q("priority")),
                    projectId = call.qInt("project_id"),
                    memoryId = call.qInt("memory_id"),
                    dueBefore = call.q("due_before"),
                    dueAfter = call.q("due_after"),
                    search = call.q("q"),
                    source = call.q("source"),
                    externalId = call.q("external_id"),
                    order = TaskOrder.fromWire(call.q("order")),
                    limit = call.qInt("limit") ?: DEFAULT_PAGE_SIZE,
                    cursor = call.q("cursor")
                )
                call.ok(AgentJson.envelopePage(api.listTasks(query, client)))
            }
        }

        post("/tasks") {
            call.handle(allowLan) { client ->
                val task = api.createTask(AgentJson.createTask(call.body()), client)
                call.ok(AgentJson.toJson(task), HttpStatusCode.Created)
            }
        }

        get("/tasks/pending-approval") {
            call.handle(allowLan) { client ->
                call.ok(AgentJson.toJson(api.listPendingApproval(client)))
            }
        }

        get("/tasks/{id}") {
            call.handle(allowLan) { client ->
                val task = api.getTask(call.intParam("id"), client)
                    ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No such task.")
                call.ok(AgentJson.toJson(task))
            }
        }

        patch("/tasks/{id}") {
            call.handle(allowLan) { client ->
                val task = api.updateTask(call.intParam("id"), AgentJson.taskPatch(call.body()), client)
                call.ok(AgentJson.toJson(task))
            }
        }

        delete("/tasks/{id}") {
            call.handle(allowLan) { client ->
                api.deleteTask(call.intParam("id"), client)
                call.respondText("", status = HttpStatusCode.NoContent)
            }
        }

        post("/tasks/{id}/complete") {
            call.handle(allowLan) { client ->
                call.ok(AgentJson.toJson(api.completeTask(call.intParam("id"), client)))
            }
        }

        post("/tasks/{id}/approve") {
            call.handle(allowLan) { client ->
                call.ok(AgentJson.toJson(api.approveTask(call.intParam("id"), client)))
            }
        }

        post("/tasks/{id}/reject") {
            call.handle(allowLan) { client ->
                api.rejectTask(call.intParam("id"), client)
                call.respondText("", status = HttpStatusCode.NoContent)
            }
        }

        // --- memories ---

        get("/memories") {
            call.handle(allowLan) { client ->
                val query = MemoryQuery(
                    search = call.q("q"),
                    kind = MemoryKind.entries.firstOrNull { it.wire.equals(call.q("kind"), true) },
                    status = ProcessingStatus.entries.firstOrNull { it.wire.equals(call.q("status"), true) },
                    projectId = call.qInt("project_id"),
                    tag = call.q("tag"),
                    from = call.q("from")?.toLongOrNull(),
                    to = call.q("to")?.toLongOrNull(),
                    includeHidden = call.qBool("include_hidden") ?: false,
                    order = MemoryOrder.fromWire(call.q("order")),
                    limit = call.qInt("limit") ?: DEFAULT_PAGE_SIZE,
                    cursor = call.q("cursor")
                )
                call.ok(AgentJson.envelopePage(api.listMemories(query, client)))
            }
        }

        post("/memories") {
            call.handle(allowLan) { client ->
                val memory = api.createMemory(AgentJson.createMemory(call.body()), client)
                call.ok(AgentJson.toJson(memory), HttpStatusCode.Created)
            }
        }

        get("/memories/{id}") {
            call.handle(allowLan) { client ->
                val memory = api.getMemory(call.intParam("id"), client)
                    ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No such memory.")
                call.ok(AgentJson.toJson(memory))
            }
        }

        patch("/memories/{id}") {
            call.handle(allowLan) { client ->
                call.ok(AgentJson.toJson(api.updateMemory(call.intParam("id"), AgentJson.memoryPatch(call.body()), client)))
            }
        }

        delete("/memories/{id}") {
            call.handle(allowLan) { client ->
                api.deleteMemory(call.intParam("id"), client)
                call.respondText("", status = HttpStatusCode.NoContent)
            }
        }

        get("/memories/{id}/media/{index}") {
            call.handle(allowLan) { client ->
                val blob = api.getMemoryMedia(call.intParam("id"), call.intParam("index"), client)
                    ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No such media.")
                call.respondBytes(blob.bytes, ContentType.parse(blob.mimeType))
            }
        }

        // --- projects (and the /collections alias) ---

        for (prefix in listOf("/projects", "/collections")) {
            get(prefix) {
                call.handle(allowLan) { client ->
                    val query = ProjectQuery(
                        status = ProjectStatus.fromWire(call.q("status")),
                        includeArchived = call.qBool("include_archived") ?: false,
                        withCounts = call.qBool("with_counts") ?: false,
                        search = call.q("q"),
                        limit = call.qInt("limit") ?: DEFAULT_PAGE_SIZE,
                        cursor = call.q("cursor")
                    )
                    call.ok(AgentJson.envelopePage(api.listProjects(query, client)))
                }
            }

            post(prefix) {
                call.handle(allowLan) { client ->
                    call.ok(AgentJson.toJson(api.createProject(AgentJson.createProject(call.body()), client)), HttpStatusCode.Created)
                }
            }

            get("$prefix/{id}") {
                call.handle(allowLan) { client ->
                    val project = api.getProject(call.intParam("id"), client)
                        ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No such project.")
                    call.ok(AgentJson.toJson(project))
                }
            }

            patch("$prefix/{id}") {
                call.handle(allowLan) { client ->
                    call.ok(AgentJson.toJson(api.updateProject(call.intParam("id"), AgentJson.projectPatch(call.body()), client)))
                }
            }

            delete("$prefix/{id}") {
                call.handle(allowLan) { client ->
                    api.deleteProject(call.intParam("id"), client)
                    call.respondText("", status = HttpStatusCode.NoContent)
                }
            }

            put("$prefix/{id}/memories/{memory_id}") {
                call.handle(allowLan) { client ->
                    api.addMemoryToProject(call.intParam("id"), call.intParam("memory_id"), client)
                    call.respondText("", status = HttpStatusCode.NoContent)
                }
            }

            delete("$prefix/{id}/memories/{memory_id}") {
                call.handle(allowLan) { client ->
                    api.removeMemoryFromProject(call.intParam("id"), call.intParam("memory_id"), client)
                    call.respondText("", status = HttpStatusCode.NoContent)
                }
            }
        }

        // --- chats ---

        get("/chats") {
            call.handle(allowLan) { client ->
                val query = ChatQuery(
                    search = call.q("q"),
                    limit = call.qInt("limit") ?: DEFAULT_PAGE_SIZE,
                    cursor = call.q("cursor")
                )
                call.ok(AgentJson.envelopePage(api.listChats(query, client)))
            }
        }

        get("/chats/{id}/messages") {
            call.handle(allowLan) { client ->
                call.ok(AgentJson.toJson(api.getChatMessages(call.intParam("id"), client)))
            }
        }

        // --- attention ---

        post("/notify") {
            call.handle(allowLan) { client ->
                val ref = api.notify(AgentJson.notifyRequest(call.body()), client)
                call.ok(AgentJson.toJson(ref), HttpStatusCode.Accepted)
            }
        }

        post("/ask") {
            call.handle(allowLan) { client ->
                val ask = api.ask(AgentJson.askRequest(call.body()), client)
                call.ok(AgentJson.toJson(ask), HttpStatusCode.Accepted)
            }
        }

        get("/ask/{ask_id}") {
            call.handle(allowLan) { client ->
                val id = call.parameters["ask_id"]
                    ?: throw AgentException(AgentErrorCode.INVALID_REQUEST, "'ask_id' is required.")
                val wait = call.qInt("wait") ?: 0
                call.ok(AgentJson.toJson(api.pollAsk(id, wait, client)))
            }
        }

        get("/asks") {
            call.handle(allowLan) { client ->
                call.ok(AgentJson.toJson(api.listPendingAsks(client)))
            }
        }

        // --- events ---

        get("/events") {
            val client = call.authenticate(allowLan) ?: return@get
            client.require(AgentScope.READ)
            val types = AgentEventType.expand(
                call.q("types")?.split(',')?.map { it.trim() } ?: listOf(AgentEventType.ALL)
            )
            val afterId = call.q("after_id")?.toLongOrNull() ?: 0L

            call.respondTextWriter(ContentType.Text.EventStream) {
                // A retry hint means a client that drops mid-stream reconnects on its own
                // rather than the agent having to notice and re-issue the request.
                write("retry: 3000\n\n")
                flush()

                // Events merged with a 20-second tick. Without the tick a quiet stream looks
                // identical to a dead one, and proxies close it.
                val heartbeat = kotlinx.coroutines.flow.flow<AgentEvent?> {
                    while (true) {
                        kotlinx.coroutines.delay(20_000)
                        emit(null)
                    }
                }
                val merged = kotlinx.coroutines.flow.merge(api.events(types, afterId), heartbeat)

                try {
                    merged.collect { event ->
                        if (!coroutineContext.isActive) throw StopStreaming()
                        if (event == null) {
                            write(": ping\n\n")
                        } else {
                            write("id: ${event.id}\n")
                            write("event: ${event.type.wire}\n")
                            write("data: ${AgentJson.gson.toJson(eventJson(event))}\n\n")
                        }
                        flush()
                    }
                } catch (e: StopStreaming) {
                    // client went away
                } catch (e: Exception) {
                    Log.d(TAG, "SSE stream closed: ${e.message}")
                }
            }
        }

        get("/webhooks") {
            call.handle(allowLan) { client ->
                call.ok(AgentJson.toJson(api.listWebhooks(client)))
            }
        }

        post("/webhooks") {
            call.handle(allowLan) { client ->
                val hook = api.registerWebhook(AgentJson.registerWebhook(call.body()), client)
                call.ok(AgentJson.toJson(hook), HttpStatusCode.Created)
            }
        }

        delete("/webhooks/{webhook_id}") {
            call.handle(allowLan) { client ->
                api.deleteWebhook(call.parameters["webhook_id"].orEmpty(), client)
                call.respondText("", status = HttpStatusCode.NoContent)
            }
        }
    }

    private fun eventJson(event: AgentEvent): com.google.gson.JsonObject {
        val obj = com.google.gson.JsonObject()
        obj.addProperty("id", event.id)
        obj.addProperty("type", event.type.wire)
        obj.addProperty("at", event.at)
        obj.addProperty("entity_id", event.entityId)
        obj.add(
            "payload",
            runCatching { com.google.gson.JsonParser.parseString(event.payloadJson) }
                .getOrElse { com.google.gson.JsonObject() }
        )
        return obj
    }
}

/**
 * Ktor 2.3 has no server-side SSE plugin, so the stream is driven by hand. This is how it
 * stops: an infinite flow has no other way out, and writing into a dead socket forever is
 * how you leak a coroutine per disconnected agent.
 */
private class StopStreaming : RuntimeException(null, null, false, false)
