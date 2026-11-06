package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.composio.ComposioConnectClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The one check that talks to the real Composio Connect endpoint. It only runs when COMPOSIO_CONSUMER_KEY is in the
 * environment, so the normal suite never needs a key and nothing secret is ever written into the repo. It reads
 * only: the handshake, the tool list, and the connected accounts.
 */
class ComposioConnectLiveTest {

    @Test fun aRealConsumerKeyCanHandshakeAndListItsConnections() = runBlocking {
        val key = System.getenv("COMPOSIO_CONSUMER_KEY")
        assumeTrue("no COMPOSIO_CONSUMER_KEY in the environment", !key.isNullOrBlank())
        val client = ComposioConnectClient(
            keyProvider = { key },
            http = ComposioConnectClient.defaultHttp(),
        )
        val check = client.checkKey()
        assertTrue("handshake refused: ${check.attempts}", check.ok)

        val tools = client.listTools()
        assertTrue("the endpoint lost its meta tools", tools.contains("COMPOSIO_MULTI_EXECUTE_TOOL"))
        assertTrue("a code-running tool appeared", tools.contains("COMPOSIO_REMOTE_BASH_TOOL"))

        // Read only, and only the connection list. No tool of his is called.
        val accounts = client.listConnectedAccounts()
        println("live connect: ${accounts.size} connected account(s): ${accounts.map { it.toolkit }}")
    }
}
