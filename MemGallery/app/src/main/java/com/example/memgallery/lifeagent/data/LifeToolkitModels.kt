package com.example.memgallery.lifeagent.data

/** What the Connections screen shows for one toolkit: its catalog entry plus whether it is connected here. */
data class LifeToolkit(
    val slug: String,
    val name: String,
    val description: String?,
    val logoUrl: String?,
    val categories: List<String>,
    val noAuth: Boolean,
    val connected: Boolean,
    val accountLabel: String?,
    val lastPulledAt: Long,
    val lastError: String?,
) {
    val known: Boolean get() = LifeApp.from(slug) != null
}

/** The result of tapping Connect: either it is already connected, or the user has to finish in the browser. */
sealed class ConnectOutcome {
    data class Connected(val accountLabel: String?) : ConnectOutcome()
    data class Browser(val url: String) : ConnectOutcome()
    data class Failed(val message: String) : ConnectOutcome()
}
