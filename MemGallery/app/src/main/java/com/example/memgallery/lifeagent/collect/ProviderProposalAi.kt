package com.example.memgallery.lifeagent.collect

import com.example.memgallery.data.remote.ai.AIProviderFactory
import com.example.memgallery.lifeagent.composio.RawItem
import com.example.memgallery.lifeagent.data.LifeKind
import com.google.gson.JsonParser
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uses the user's own main AI provider (the same per-job setup the rest of the app uses) for one sentence and one
 * classification per proposal. Returns null whenever no provider is set up, it is slow, or the answer is not
 * usable, so the rule-based draft stands. Only the title and kind of an item already kept by the filter leave the
 * phone, and only to the provider the user chose for the app.
 */
@Singleton
class ProviderProposalAi @Inject constructor(
    private val factory: AIProviderFactory,
) : ProposalAi {

    override suspend fun refine(item: RawItem, draft: ProposalDraft): AiRefinement? {
        return try {
            val provider = factory.getProvider()
            if (!provider.isEnabled()) return null
            val prompt = buildString {
                append("Source: ").append(item.app).append('\n')
                append("Subject: ").append(item.title.take(160)).append('\n')
                append("Text: ").append(item.body.take(600)).append('\n')
                append("Draft: ").append(draft.title).append(" (").append(draft.kind.key).append(")")
            }
            val answer = withTimeoutOrNull(TIMEOUT_MS) {
                provider.sendMessage("", prompt, SYSTEM, null).getOrNull()
            } ?: return null
            parse(answer)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TIMEOUT_MS = 12_000L
        private val SYSTEM = "You turn one item from the user's mail, chat, calendar or tasks into a single memory. " +
            "Reply with JSON only: {\"kind\":\"person|commitment|deadline|decision|money|event|document|security|awaiting|other\"," +
            "\"sentence\":\"one plain sentence under 140 characters that says who, what and when\"}. " +
            "Use only facts in the text. No greetings, no advice."

        internal fun parse(answer: String): AiRefinement? = try {
            val start = answer.indexOf('{'); val end = answer.lastIndexOf('}')
            if (start < 0 || end <= start) null else {
                val o = JsonParser.parseString(answer.substring(start, end + 1)).asJsonObject
                val kind = o.get("kind")?.takeIf { it.isJsonPrimitive }?.asString?.let { k -> LifeKind.entries.firstOrNull { it.key == k.lowercase() } }
                val sentence = o.get("sentence")?.takeIf { it.isJsonPrimitive }?.asString
                if (kind == null && sentence == null) null else AiRefinement(sentence, kind)
            }
        } catch (e: Exception) { null }
    }
}
