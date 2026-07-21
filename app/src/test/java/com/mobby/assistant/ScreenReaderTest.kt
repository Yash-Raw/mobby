package com.mobby.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ScreenReader] label matching and Levenshtein fuzzy distance algorithms.
 */
class ScreenReaderTest {

    // ── Levenshtein Distance Calculation Tests ─────────────────────────

    @Test
    fun `levenshteinDistance identical strings returns 0`() {
        assertEquals(0, ScreenReader.levenshteinDistance("whatsapp", "whatsapp"))
    }

    @Test
    fun `levenshteinDistance single character substitution returns 1`() {
        assertEquals(1, ScreenReader.levenshteinDistance("whatapp", "whatsapp"))
        assertEquals(1, ScreenReader.levenshteinDistance("serch", "search"))
    }

    @Test
    fun `levenshteinDistance insertion and deletion returns correct edit distance`() {
        assertEquals(1, ScreenReader.levenshteinDistance("camra", "camera"))
        assertEquals(2, ScreenReader.levenshteinDistance("gmale", "gmail"))
    }

    // ── labelsMatch Exact and Substring Tests ──────────────────────────

    @Test
    fun `labelsMatch exact match returns true`() {
        assertTrue(ScreenReader.labelsMatch("WhatsApp", "whatsapp"))
        assertTrue(ScreenReader.labelsMatch("Search", "Search"))
    }

    @Test
    fun `labelsMatch substring match returns true`() {
        assertTrue(ScreenReader.labelsMatch("Google Search", "Search"))
        assertTrue(ScreenReader.labelsMatch("Search", "Google Search"))
    }

    // ── labelsMatch Fuzzy Typo Tests ───────────────────────────────────

    @Test
    fun `labelsMatch recognizes Whatapp as WhatsApp`() {
        assertTrue(ScreenReader.labelsMatch("WhatsApp", "Whatapp"))
        assertTrue(ScreenReader.labelsMatch("WhatsApp", "Watsapp"))
    }

    @Test
    fun `labelsMatch recognizes Serch as Search`() {
        assertTrue(ScreenReader.labelsMatch("Search", "Serch"))
        assertTrue(ScreenReader.labelsMatch("Search", "Seach"))
    }

    @Test
    fun `labelsMatch recognizes Camra as Camera`() {
        assertTrue(ScreenReader.labelsMatch("Camera", "Camra"))
    }

    @Test
    fun `labelsMatch token-level fuzzy match recognizes search bar`() {
        assertTrue(ScreenReader.labelsMatch("Search Bar", "Serch"))
        assertTrue(ScreenReader.labelsMatch("Open Settings Menu", "Setting"))
    }

    // ── Non-Matching Tests ──────────────────────────────────────────────

    @Test
    fun `labelsMatch returns false for unrelated labels`() {
        assertFalse(ScreenReader.labelsMatch("Delete Account", "Save"))
        assertFalse(ScreenReader.labelsMatch("Settings", "Camera"))
        assertFalse(ScreenReader.labelsMatch("Submit", "Cancel"))
    }

    @Test
    fun `labelsMatch returns false for empty input`() {
        assertFalse(ScreenReader.labelsMatch("", "Search"))
        assertFalse(ScreenReader.labelsMatch("Search", ""))
    }
}
