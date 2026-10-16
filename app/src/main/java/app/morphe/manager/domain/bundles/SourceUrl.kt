/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.bundles

import java.util.Locale

/**
 * ORU: the form a patch source address is compared and stored in. Addresses that name the same
 * repository come out equal however each was written: surrounding spaces, a trailing slash, a
 * ".git" suffix, the case of the host, owner or repository, or a scheme left out or written as
 * http on GitHub and GitLab.
 */
object SourceUrl {
    private val REPOSITORY_HOSTS = setOf("github.com", "raw.githubusercontent.com", "gitlab.com")

    /**
     * [input] in that form, without judging the rest of it. Only the repository part of a GitHub
     * or GitLab address is folded to lower case: a bundle file name on any host stays as written,
     * because the server may treat it as case sensitive.
     */
    fun canonical(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        val scheme = if ("://" in trimmed) trimmed.substringBefore("://") + "://" else ""
        val afterScheme = trimmed.substringAfter("://", missingDelimiterValue = trimmed)
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val lowerHost = host.lowercase(Locale.US)
        val rest = afterScheme.substring(host.length)

        if (lowerHost !in REPOSITORY_HOSTS) return scheme + lowerHost + rest

        // Forced to https: a GitHub or GitLab address is never read over plain http
        val path = rest.substringBefore('?').substringBefore('#')
        val query = rest.substring(path.length)
        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return "https://$lowerHost$rest"

        val owner = segments[0].lowercase(Locale.US)
        val repository = segments[1].lowercase(Locale.US).removeSuffix(".git")
        val remaining = segments.drop(2)
        return "https://$lowerHost/" + (listOf(owner, repository) + remaining).joinToString("/") + query
    }
}
