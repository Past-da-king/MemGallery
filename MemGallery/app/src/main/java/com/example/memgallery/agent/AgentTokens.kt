package com.example.memgallery.agent

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Token minting and verification.
 *
 * A token is shown to the operator exactly once and never stored — only a PBKDF2 hash
 * and an 8-character prefix. The prefix exists so the UI can say "mg_7f3a…" next to a
 * revoke button, and so verification can narrow to one or two candidate rows before
 * doing the expensive hash. Lookup is never by hash: hashes are salted per client, so
 * there is nothing to look up by.
 */
object AgentTokens {

    private const val PREFIX = "mg_"
    private const val TOKEN_BYTES = 32
    private const val SALT_BYTES = 16
    private const val ITERATIONS = 12_000
    private const val KEY_LENGTH_BITS = 256
    const val PREFIX_LENGTH = 8

    private val random = SecureRandom()

    /** A fresh opaque token, e.g. `mg_7f3a1c…`. URL-safe so it survives being pasted anywhere. */
    fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return PREFIX + Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun generateSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun generateId(): String = UUID.randomUUID().toString()

    /** A webhook signing secret. Same generator, different purpose. */
    fun generateSecret(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun prefixOf(token: String): String = token.take(PREFIX_LENGTH)

    fun hash(token: String, salt: String): String {
        val spec = PBEKeySpec(
            token.toCharArray(),
            Base64.decode(salt, Base64.NO_WRAP),
            ITERATIONS,
            KEY_LENGTH_BITS
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * Constant-time comparison. A plain `==` on strings short-circuits at the first
     * differing byte, which is measurable over a loopback socket.
     */
    fun verify(token: String, salt: String, expectedHash: String): Boolean {
        val actual = hash(token, salt)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }

    /** HMAC-SHA256 over a webhook body, hex-encoded, sent as `sha256=<hex>`. */
    fun hmacSha256Hex(secret: String, body: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val out = mac.doFinal(body.toByteArray(Charsets.UTF_8))
        return out.joinToString("") { "%02x".format(it) }
    }
}
