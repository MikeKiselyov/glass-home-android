package com.glasshome.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/** The last links sent to the TV, for opening a series again with one tap. */
class RecentLinksTest {

    @Test
    fun newestFirstWithoutRepeats() {
        val recent = RecentLinks(emptyList())
        recent.add("https://a.example/")
        recent.add("https://b.example/")
        recent.add("https://a.example/")
        assertEquals(listOf("https://a.example/", "https://b.example/"), recent.links)
    }

    @Test
    fun onlyTheLastTenAreKept() {
        val recent = RecentLinks(emptyList())
        for (number in 1..12) {
            recent.add("https://example.com/$number")
        }
        assertEquals(RecentLinks.MAX, recent.links.size)
        assertEquals("https://example.com/12", recent.links.first())
        assertEquals("https://example.com/3", recent.links.last())
    }

    @Test
    fun savedListsComeBackAndClearEmpties() {
        val recent = RecentLinks(listOf("https://a.example/", "not a link", "https://b.example/"))
        assertEquals(listOf("https://a.example/", "https://b.example/"), recent.links)
        recent.clear()
        assertEquals(emptyList<String>(), recent.links)
    }
}
