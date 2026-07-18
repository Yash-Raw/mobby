package com.tappy.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Comprehensive tests for [CommandParser] covering all 12 command types,
 * edge cases, punctuation stripping, and case insensitivity.
 */
class CommandParserTest {

    // ── DESCRIBE_SCREEN ─────────────────────────────────────────────────

    @Test
    fun `what's on screen`() {
        assertType("what's on screen", CommandParser.Type.DESCRIBE_SCREEN)
    }

    @Test
    fun `what is on screen`() {
        assertType("what is on screen", CommandParser.Type.DESCRIBE_SCREEN)
    }

    @Test
    fun `read the screen`() {
        assertType("read the screen", CommandParser.Type.DESCRIBE_SCREEN)
    }

    @Test
    fun `describe screen`() {
        assertType("describe screen", CommandParser.Type.DESCRIBE_SCREEN)
    }

    @Test
    fun `where am i`() {
        assertType("where am i", CommandParser.Type.DESCRIBE_SCREEN)
    }

    @Test
    fun `tell me what is on screen`() {
        assertType("tell me what is on screen", CommandParser.Type.DESCRIBE_SCREEN)
    }

    @Test
    fun `describe screen case insensitive`() {
        assertType("What's On Screen", CommandParser.Type.DESCRIBE_SCREEN)
    }

    // ── GUIDE_SCREEN ────────────────────────────────────────────────────

    @Test
    fun `guide me`() {
        assertType("guide me", CommandParser.Type.GUIDE_SCREEN)
    }

    @Test
    fun `how do i use this`() {
        assertType("how do i use this", CommandParser.Type.GUIDE_SCREEN)
    }

    @Test
    fun `help me use this app`() {
        assertType("help me use this app", CommandParser.Type.GUIDE_SCREEN)
    }

    @Test
    fun `explain this screen`() {
        assertType("explain this screen", CommandParser.Type.GUIDE_SCREEN)
    }

    @Test
    fun `what can i do here`() {
        assertType("what can i do here", CommandParser.Type.GUIDE_SCREEN)
    }

    // ── LIST_CONTROLS ───────────────────────────────────────────────────

    @Test
    fun `what can i tap`() {
        assertType("what can i tap", CommandParser.Type.LIST_CONTROLS)
    }

    @Test
    fun `list controls`() {
        assertType("list controls", CommandParser.Type.LIST_CONTROLS)
    }

    @Test
    fun `show controls`() {
        assertType("show controls", CommandParser.Type.LIST_CONTROLS)
    }

    @Test
    fun `what buttons are here`() {
        assertType("what buttons are here", CommandParser.Type.LIST_CONTROLS)
    }

    // ── BACK ────────────────────────────────────────────────────────────

    @Test
    fun `back`() {
        assertType("back", CommandParser.Type.BACK)
    }

    @Test
    fun `go back`() {
        assertType("go back", CommandParser.Type.BACK)
    }

    // ── HOME ────────────────────────────────────────────────────────────

    @Test
    fun `home`() {
        assertType("home", CommandParser.Type.HOME)
    }

    @Test
    fun `go home`() {
        assertType("go home", CommandParser.Type.HOME)
    }

    // ── SCROLL ──────────────────────────────────────────────────────────

    @Test
    fun `scroll down`() {
        val command = CommandParser.parse("scroll down")
        assertEquals(CommandParser.Type.SCROLL, command.type)
        assertEquals("down", command.target)
    }

    @Test
    fun `scroll up`() {
        val command = CommandParser.parse("scroll up")
        assertEquals(CommandParser.Type.SCROLL, command.type)
        assertEquals("up", command.target)
    }

    // ── SEND_CURRENT_MESSAGE ────────────────────────────────────────────

    @Test
    fun `send`() {
        assertType("send", CommandParser.Type.SEND_CURRENT_MESSAGE)
    }

    @Test
    fun `send message`() {
        assertType("send message", CommandParser.Type.SEND_CURRENT_MESSAGE)
    }

    @Test
    fun `tap send`() {
        assertType("tap send", CommandParser.Type.SEND_CURRENT_MESSAGE)
    }

    // ── TAP ─────────────────────────────────────────────────────────────

    @Test
    fun `tap Search`() {
        val command = CommandParser.parse("tap Search")
        assertEquals(CommandParser.Type.TAP, command.type)
        assertEquals("Search", command.target)
    }

    @Test
    fun `click Settings`() {
        val command = CommandParser.parse("click Settings")
        assertEquals(CommandParser.Type.TAP, command.type)
        assertEquals("Settings", command.target)
    }

    @Test
    fun `press Wi-Fi settings`() {
        val command = CommandParser.parse("press Wi-Fi settings")
        assertEquals(CommandParser.Type.TAP, command.type)
        assertEquals("Wi-Fi settings", command.target)
    }

    @Test
    fun `open Camera`() {
        val command = CommandParser.parse("open Camera")
        assertEquals(CommandParser.Type.TAP, command.type)
        assertEquals("Camera", command.target)
    }

    @Test
    fun `tap strips trailing punctuation`() {
        val command = CommandParser.parse("tap Search!")
        assertEquals(CommandParser.Type.TAP, command.type)
        assertEquals("Search", command.target)
    }

    // ── TYPE_TEXT ────────────────────────────────────────────────────────

    @Test
    fun `type hello`() {
        val command = CommandParser.parse("type hello")
        assertEquals(CommandParser.Type.TYPE_TEXT, command.type)
        assertEquals("hello", command.text)
    }

    @Test
    fun `write a long message`() {
        val command = CommandParser.parse("write I'll be there in five minutes")
        assertEquals(CommandParser.Type.TYPE_TEXT, command.type)
        assertEquals("I'll be there in five minutes", command.text)
    }

    @Test
    fun `enter some text`() {
        val command = CommandParser.parse("enter some text")
        assertEquals(CommandParser.Type.TYPE_TEXT, command.type)
        assertEquals("some text", command.text)
    }

    @Test
    fun `type strips trailing punctuation`() {
        val command = CommandParser.parse("type hello world.")
        assertEquals(CommandParser.Type.TYPE_TEXT, command.type)
        assertEquals("hello world", command.text)
    }

    // ── REPLY_IN_CURRENT_CONVERSATION ───────────────────────────────────

    @Test
    fun `reply that I'm running late`() {
        val command = CommandParser.parse("reply that I'm running late")
        assertEquals(CommandParser.Type.REPLY_IN_CURRENT_CONVERSATION, command.type)
        assertEquals("I'm running late", command.text)
    }

    @Test
    fun `reply with sounds good`() {
        val command = CommandParser.parse("reply with sounds good")
        assertEquals(CommandParser.Type.REPLY_IN_CURRENT_CONVERSATION, command.type)
        assertEquals("sounds good", command.text)
    }

    @Test
    fun `reply saying be right there`() {
        val command = CommandParser.parse("reply saying be right there")
        assertEquals(CommandParser.Type.REPLY_IN_CURRENT_CONVERSATION, command.type)
        assertEquals("be right there", command.text)
    }

    // ── REPLY_TO_PERSON ─────────────────────────────────────────────────

    @Test
    fun `reply to Maya that I'll call later`() {
        val command = CommandParser.parse("reply to Maya that I'll call later")
        assertEquals(CommandParser.Type.REPLY_TO_PERSON, command.type)
        assertEquals("Maya", command.target)
        assertEquals("I'll call later", command.text)
    }

    @Test
    fun `reply to John with thanks`() {
        val command = CommandParser.parse("reply to John with thanks")
        assertEquals(CommandParser.Type.REPLY_TO_PERSON, command.type)
        assertEquals("John", command.target)
        assertEquals("thanks", command.text)
    }

    @Test
    fun `reply to Sarah saying on my way`() {
        val command = CommandParser.parse("reply to Sarah saying on my way")
        assertEquals(CommandParser.Type.REPLY_TO_PERSON, command.type)
        assertEquals("Sarah", command.target)
        assertEquals("on my way", command.text)
    }

    @Test
    fun `reply to person with colon syntax`() {
        val command = CommandParser.parse("reply to Alex: be there soon")
        assertEquals(CommandParser.Type.REPLY_TO_PERSON, command.type)
        assertEquals("Alex", command.target)
        assertEquals("be there soon", command.text)
    }

    // ── CHECK_MESSAGES_FROM ─────────────────────────────────────────────

    @Test
    fun `check messages from Maya`() {
        val command = CommandParser.parse("check messages from Maya")
        assertEquals(CommandParser.Type.CHECK_MESSAGES_FROM, command.type)
        assertEquals("Maya", command.target)
    }

    @Test
    fun `show texts from John`() {
        val command = CommandParser.parse("show texts from John")
        assertEquals(CommandParser.Type.CHECK_MESSAGES_FROM, command.type)
        assertEquals("John", command.target)
    }

    @Test
    fun `check if I have any messages from Sarah`() {
        val command = CommandParser.parse("check if I have any messages from Sarah")
        assertEquals(CommandParser.Type.CHECK_MESSAGES_FROM, command.type)
        assertEquals("Sarah", command.target)
    }

    @Test
    fun `find recent texts from Alex`() {
        val command = CommandParser.parse("find recent texts from Alex")
        assertEquals(CommandParser.Type.CHECK_MESSAGES_FROM, command.type)
        assertEquals("Alex", command.target)
    }

    // ── CHECK_AND_REPLY (combined check+reply) ──────────────────────────

    @Test
    fun `messages from Maya reply to her that OK`() {
        val command = CommandParser.parse("messages from Maya reply to her that OK")
        assertEquals(CommandParser.Type.REPLY_TO_PERSON, command.type)
        assertEquals("Maya", command.target)
        assertEquals("OK", command.text)
    }

    @Test
    fun `check texts from John reply to him saying thanks`() {
        val command = CommandParser.parse("check texts from John reply to him saying thanks")
        assertEquals(CommandParser.Type.REPLY_TO_PERSON, command.type)
        assertEquals("John", command.target)
        assertEquals("thanks", command.text)
    }

    // ── UNSUPPORTED ─────────────────────────────────────────────────────

    @Test
    fun `null input returns UNSUPPORTED`() {
        assertType(null, CommandParser.Type.UNSUPPORTED)
    }

    @Test
    fun `empty string returns UNSUPPORTED`() {
        assertType("", CommandParser.Type.UNSUPPORTED)
    }

    @Test
    fun `whitespace-only returns UNSUPPORTED`() {
        assertType("   ", CommandParser.Type.UNSUPPORTED)
    }

    @Test
    fun `random phrase returns UNSUPPORTED`() {
        assertType("tell me a joke", CommandParser.Type.UNSUPPORTED)
    }

    @Test
    fun `partial keyword does not match`() {
        assertType("go backstage", CommandParser.Type.UNSUPPORTED)
    }

    // ── Whitespace normalisation ────────────────────────────────────────

    @Test
    fun `extra whitespace is normalised`() {
        assertType("  what's   on   screen  ", CommandParser.Type.DESCRIBE_SCREEN)
    }

    @Test
    fun `leading and trailing whitespace stripped`() {
        val command = CommandParser.parse("  tap Search  ")
        assertEquals(CommandParser.Type.TAP, command.type)
        assertEquals("Search", command.target)
    }

    // ── LocalIntentClassifier tests ─────────────────────────────────────

    @Test
    fun `local intent classifier maps natural home phrasing`() {
        assertType("please go to the home screen", CommandParser.Type.HOME)
    }

    @Test
    fun `local intent classifier maps natural describe screen phrasing`() {
        assertType("tell me what you see on the screen", CommandParser.Type.DESCRIBE_SCREEN)
    }

    @Test
    fun `local intent classifier maps natural tap phrasing and extracts target`() {
        val command = CommandParser.parse("click on search button")
        assertEquals(CommandParser.Type.TAP, command.type)
        assertEquals("search button", command.target)
    }

    @Test
    fun `local intent classifier maps natural type phrasing and extracts text`() {
        val command = CommandParser.parse("write down hello world")
        assertEquals(CommandParser.Type.TYPE_TEXT, command.type)
        assertEquals("hello world", command.text)
    }

    @Test
    fun `local intent classifier maps close phrasing`() {
        assertType("never mind please", CommandParser.Type.CLOSE)
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private fun assertType(transcript: String?, expectedType: CommandParser.Type) {
        val command = CommandParser.parse(transcript)
        assertEquals(expectedType, command.type)
    }
}
