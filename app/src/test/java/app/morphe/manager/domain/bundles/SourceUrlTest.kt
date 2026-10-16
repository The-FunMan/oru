/*
 * Copyright 2026 The-FunMan and Oru contributors
 * https://github.com/The-FunMan/oru
 */

package app.morphe.manager.domain.bundles

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceUrlTest {

    @Test
    fun `a trailing slash is dropped`() {
        assertEquals("https://github.com/owner/repo", SourceUrl.canonical("https://github.com/owner/repo/"))
    }

    @Test
    fun `a dot git suffix is dropped`() {
        assertEquals("https://github.com/owner/repo", SourceUrl.canonical("https://github.com/owner/repo.git"))
        assertEquals("https://gitlab.com/owner/repo", SourceUrl.canonical("https://gitlab.com/owner/repo.git/"))
    }

    @Test
    fun `surrounding spaces are dropped`() {
        assertEquals("https://github.com/owner/repo", SourceUrl.canonical("  https://github.com/owner/repo \n"))
    }

    @Test
    fun `the host and the owner and repository are lower case on github and gitlab`() {
        assertEquals("https://github.com/owner/repo", SourceUrl.canonical("https://GitHub.com/Owner/Repo"))
        assertEquals("https://gitlab.com/owner/repo", SourceUrl.canonical("https://GITLAB.com/Owner/Repo"))
    }

    @Test
    fun `a bare github address gets https`() {
        assertEquals("https://github.com/owner/repo", SourceUrl.canonical("github.com/owner/repo"))
        assertEquals("https://gitlab.com/owner/repo", SourceUrl.canonical("gitlab.com/owner/repo"))
    }

    @Test
    fun `http on github is read over https`() {
        assertEquals("https://github.com/owner/repo", SourceUrl.canonical("http://github.com/owner/repo"))
    }

    @Test
    fun `the path after the repository keeps its case`() {
        assertEquals(
            "https://raw.githubusercontent.com/owner/repo/main/Bundle-Latest.json",
            SourceUrl.canonical("https://raw.githubusercontent.com/Owner/Repo/main/Bundle-Latest.json"),
        )
    }

    @Test
    fun `a query is kept after the repository`() {
        assertEquals(
            "https://gitlab.com/owner/repo/-/raw/main/patches-bundle.json?token=Abc",
            SourceUrl.canonical("https://gitlab.com/Owner/Repo.git/-/raw/main/patches-bundle.json?token=Abc"),
        )
    }

    @Test
    fun `other hosts keep their path and scheme`() {
        assertEquals(
            "https://example.com/Piko/Patches.json",
            SourceUrl.canonical("https://Example.com/Piko/Patches.json"),
        )
        assertEquals("http://example.com/patches.json", SourceUrl.canonical("http://example.com/patches.json"))
    }

    @Test
    fun `the .git form and the plain form are the same source`() {
        assertEquals(
            SourceUrl.canonical("https://github.com/owner/repo"),
            SourceUrl.canonical("https://github.com/owner/repo.git"),
        )
    }
}
