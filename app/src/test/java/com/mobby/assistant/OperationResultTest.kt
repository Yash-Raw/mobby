package com.mobby.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [OperationResult] data class — factory methods, equality, and copy behaviour.
 */
class OperationResultTest {

    @Test
    fun `success factory sets successful to true`() {
        val result = OperationResult.success("Done.")
        assertTrue(result.successful)
        assertEquals("Done.", result.message)
    }

    @Test
    fun `failure factory sets successful to false`() {
        val result = OperationResult.failure("Something went wrong.")
        assertFalse(result.successful)
        assertEquals("Something went wrong.", result.message)
    }

    @Test
    fun `two successes with same message are equal`() {
        val a = OperationResult.success("OK")
        val b = OperationResult.success("OK")
        assertEquals(a, b)
    }

    @Test
    fun `success and failure with same message are not equal`() {
        val success = OperationResult.success("Message")
        val failure = OperationResult.failure("Message")
        assertNotEquals(success, failure)
    }

    @Test
    fun `copy preserves values`() {
        val original = OperationResult.success("Original")
        val copy = original.copy()
        assertEquals(original, copy)
    }

    @Test
    fun `copy can override message`() {
        val original = OperationResult.success("Original")
        val modified = original.copy(message = "Modified")
        assertEquals("Modified", modified.message)
        assertTrue(modified.successful)
    }

    @Test
    fun `copy can override successful`() {
        val original = OperationResult.success("Test")
        val modified = original.copy(successful = false)
        assertFalse(modified.successful)
        assertEquals("Test", modified.message)
    }

    @Test
    fun `empty message is allowed`() {
        val result = OperationResult.success("")
        assertTrue(result.successful)
        assertEquals("", result.message)
    }

    @Test
    fun `hashCode consistent with equals`() {
        val a = OperationResult.failure("Error")
        val b = OperationResult.failure("Error")
        assertEquals(a.hashCode(), b.hashCode())
    }
}
