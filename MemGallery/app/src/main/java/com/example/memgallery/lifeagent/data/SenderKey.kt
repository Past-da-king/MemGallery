package com.example.memgallery.lifeagent.data

/** Turns a raw sender ("Name <a@b.co>", a Slack user, a ClickUp assignee) into one stable key for learning. */
object SenderKey {
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    fun of(author: String?): String {
        if (author.isNullOrBlank()) return ""
        email.find(author)?.let { return it.value.lowercase() }
        return author.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    fun emailOf(author: String?): String? = author?.let { email.find(it)?.value?.lowercase() }

    /** "Nomsa Mahlangu <n@x.co>" gives "Nomsa Mahlangu"; a bare address gives null. */
    fun displayNameOf(author: String?): String? {
        if (author.isNullOrBlank()) return null
        val name = author.substringBefore('<').trim().trim('"')
        return name.takeIf { it.isNotBlank() && !email.matches(it) }
    }

    fun personId(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")
}
