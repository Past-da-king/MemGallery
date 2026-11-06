package com.example.memgallery.lifeagent.composio

import android.util.Log

/**
 * The Life Agent's own debug log: every Composio call, its status, the tool slug and how many items came back, in a
 * ring buffer the Diagnostics screen shows. Keys and message content are never written: a key is reduced to its kind
 * and its last three characters, and bodies are counted, not quoted.
 */
object LifeAgentLog {
    const val TAG = "LifeAgent"
    private const val MAX = 50

    private val entries = ArrayDeque<String>()

    fun add(line: String) {
        val stamped = "${stamp()}  $line"
        synchronized(entries) {
            entries.addLast(stamped)
            while (entries.size > MAX) entries.removeFirst()
        }
        Log.d(TAG, stamped)
    }

    fun error(line: String) {
        val stamped = "${stamp()}  $line"
        synchronized(entries) {
            entries.addLast(stamped)
            while (entries.size > MAX) entries.removeFirst()
        }
        Log.w(TAG, stamped)
    }

    /** The last calls, newest last, for the Diagnostics screen. */
    fun snapshot(): List<String> = synchronized(entries) { entries.toList() }

    fun clear() = synchronized(entries) { entries.clear() }

    /** A key never appears in a log: only its kind and three characters, enough to tell two keys apart. */
    fun maskKey(key: String?): String {
        val k = key?.trim().orEmpty()
        if (k.isEmpty()) return "none"
        val kind = KeyKind.of(k)
        val tail = if (k.length > 3) k.takeLast(3) else "***"
        return "${kind.name.lowercase()} key ...$tail"
    }

    private fun stamp(): String {
        val now = java.time.LocalTime.now().withNano(0)
        return now.toString()
    }
}
