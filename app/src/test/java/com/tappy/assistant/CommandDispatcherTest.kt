package com.tappy.assistant

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.coVerify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests for [CommandDispatcher] — verifies routing of commands to the correct handlers
 * and that destructive commands show confirmation dialogs.
 */
class CommandDispatcherTest {

    private lateinit var context: Context
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var screenReader: ScreenReader
    private lateinit var deviceController: DeviceController
    private lateinit var overlay: OverlayManager
    private lateinit var geminiBrain: GeminiBrain
    private lateinit var dispatcher: CommandDispatcher

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = mockk(relaxed = true)
        sharedPrefs = mockk(relaxed = true)
        every { context.getSharedPreferences("mobby_prefs", Context.MODE_PRIVATE) } returns sharedPrefs
        every { sharedPrefs.getBoolean("require_confirmation", any()) } returns true

        screenReader = mockk(relaxed = true)
        deviceController = mockk(relaxed = true)
        overlay = mockk(relaxed = true)
        geminiBrain = mockk(relaxed = true)
        dispatcher = CommandDispatcher(context, screenReader, deviceController, overlay, geminiBrain)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Read-only commands route to ScreenReader ────────────────────────

    @Test
    fun `DESCRIBE_SCREEN calls describeActiveWindow without guidance`() {
        every { screenReader.describeActiveWindow(false) } returns
                OperationResult.success("Screen content")

        dispatcher.dispatch(command(CommandParser.Type.DESCRIBE_SCREEN))

        verify { screenReader.describeActiveWindow(false) }
        verify { overlay.setMessage("Screen content") }
    }

    @Test
    fun `GUIDE_SCREEN calls describeActiveWindow with guidance`() {
        every { screenReader.describeActiveWindow(true) } returns
                OperationResult.success("Guide content")

        dispatcher.dispatch(command(CommandParser.Type.GUIDE_SCREEN))

        verify { screenReader.describeActiveWindow(true) }
        verify { overlay.setMessage("Guide content") }
    }

    @Test
    fun `LIST_CONTROLS calls listActiveControls`() {
        every { screenReader.listActiveControls() } returns
                OperationResult.success("Button A, Button B")

        dispatcher.dispatch(command(CommandParser.Type.LIST_CONTROLS))

        verify { screenReader.listActiveControls() }
        verify { overlay.setMessage("Button A, Button B") }
    }

    // ── Navigation commands route to DeviceController ───────────────────

    @Test
    fun `BACK calls goBack`() {
        coEvery { deviceController.goBack() } returns OperationResult.success("Went back.")

        dispatcher.dispatch(command(CommandParser.Type.BACK))

        coVerify { deviceController.goBack() }
        verify { overlay.setMessage("Went back.") }
    }

    @Test
    fun `HOME calls goHome`() {
        coEvery { deviceController.goHome() } returns OperationResult.success("Opened Home.")

        dispatcher.dispatch(command(CommandParser.Type.HOME))

        coVerify { deviceController.goHome() }
        verify { overlay.setMessage("Opened Home.") }
    }

    @Test
    fun `SCROLL calls scrollActiveWindow with direction`() {
        coEvery { deviceController.scrollActiveWindow("down") } returns
                OperationResult.success("Scrolled down.")

        dispatcher.dispatch(CommandParser.AgentCommand(CommandParser.Type.SCROLL, "down", ""))

        coVerify { deviceController.scrollActiveWindow("down") }
        verify { overlay.setMessage("Scrolled down.") }
    }

    // ── Destructive commands show confirmation ──────────────────────────

    @Test
    fun `TAP shows confirmation with target label`() {
        dispatcher.dispatch(CommandParser.AgentCommand(CommandParser.Type.TAP, "Search", ""))

        val questionSlot = slot<String>()
        verify { overlay.showConfirmation(capture(questionSlot), any()) }
        assert(questionSlot.captured.contains("Search"))
    }

    @Test
    fun `TYPE_TEXT shows confirmation with text content`() {
        dispatcher.dispatch(CommandParser.AgentCommand(CommandParser.Type.TYPE_TEXT, "", "hello"))

        val questionSlot = slot<String>()
        verify { overlay.showConfirmation(capture(questionSlot), any()) }
        assert(questionSlot.captured.contains("hello"))
    }

    @Test
    fun `REPLY_IN_CURRENT_CONVERSATION shows confirmation`() {
        dispatcher.dispatch(CommandParser.AgentCommand(
            CommandParser.Type.REPLY_IN_CURRENT_CONVERSATION, "", "I'm running late"
        ))

        val questionSlot = slot<String>()
        verify { overlay.showConfirmation(capture(questionSlot), any()) }
        assert(questionSlot.captured.contains("I'm running late"))
    }

    @Test
    fun `SEND_CURRENT_MESSAGE shows confirmation`() {
        dispatcher.dispatch(command(CommandParser.Type.SEND_CURRENT_MESSAGE))

        verify { overlay.showConfirmation(any(), any()) }
    }

    // ── UNSUPPORTED shows help text ─────────────────────────────────────

    @Test
    fun `UNSUPPORTED shows help message`() {
        every { geminiBrain.isEnabled() } returns false
        dispatcher.dispatch(command(CommandParser.Type.UNSUPPORTED))

        val messageSlot = slot<String>()
        verify { overlay.setMessage(capture(messageSlot)) }
        assert(messageSlot.captured.contains("tap Search"))
    }

    @Test
    fun `UNSUPPORTED starts Gemini session when enabled`() {
        every { geminiBrain.isEnabled() } returns true
        every { screenReader.readScreen() } returns mockk(relaxed = true) {
            every { available } returns false
            every { error } returns "Mock screen unavailable"
        }

        dispatcher.dispatch(CommandParser.AgentCommand(CommandParser.Type.UNSUPPORTED, "", "write an email"))

        val messageSlot = slot<String>()
        verify { overlay.setMessage(capture(messageSlot)) }
        assert(messageSlot.captured.contains("Mock screen unavailable"))
    }

    // ── CHECK_MESSAGES_FROM shows message or not-found ──────────────────

    @Test
    fun `CHECK_MESSAGES_FROM with no match shows not-found message`() {
        dispatcher.dispatch(CommandParser.AgentCommand(
            CommandParser.Type.CHECK_MESSAGES_FROM, "Maya", ""
        ))

        val messageSlot = slot<String>()
        verify { overlay.setMessage(capture(messageSlot)) }
        assert(messageSlot.captured.contains("Maya"))
        assert(messageSlot.captured.contains("No active message"))
    }

    // ── Bypass confirmation tests ───────────────────────────────────────

    @Test
    fun `TAP non-critical bypasses confirmation when disabled`() {
        every { sharedPrefs.getBoolean("require_confirmation", any()) } returns false

        dispatcher.dispatch(CommandParser.AgentCommand(CommandParser.Type.TAP, "WhatsApp", ""))

        verify(exactly = 0) { overlay.showConfirmation(any(), any()) }
        verify { overlay.setMessage(any()) }
    }

    @Test
    fun `TAP critical does not bypass confirmation when disabled`() {
        every { sharedPrefs.getBoolean("require_confirmation", any()) } returns false

        dispatcher.dispatch(CommandParser.AgentCommand(CommandParser.Type.TAP, "Delete Account", ""))

        verify { overlay.showConfirmation(any(), any()) }
    }

    @Test
    fun `TYPE_TEXT bypasses confirmation when disabled`() {
        every { sharedPrefs.getBoolean("require_confirmation", any()) } returns false

        dispatcher.dispatch(CommandParser.AgentCommand(CommandParser.Type.TYPE_TEXT, "", "Hello World"))

        verify(exactly = 0) { overlay.showConfirmation(any(), any()) }
        verify { overlay.setMessage(any()) }
    }

    @Test
    fun `TAP failing with confirmation shows clarification message`() {
        coEvery { deviceController.tapControl("Search") } returns OperationResult.failure("Failure")
        
        dispatcher.dispatch(CommandParser.AgentCommand(CommandParser.Type.TAP, "Search", ""))
        
        val confirmCallbackSlot = slot<suspend () -> OperationResult>()
        verify { overlay.showConfirmation(any(), capture(confirmCallbackSlot)) }
        runBlocking { confirmCallbackSlot.captured.invoke() }
        
        verify(timeout = 2000) {
            overlay.setMessage("I couldn't tap \u201CSearch\u201D. Could you clarify where it is, or tap it yourself?")
        }
    }

    @Test
    fun `TAP failing without confirmation shows clarification message`() {
        every { sharedPrefs.getBoolean("require_confirmation", any()) } returns false
        coEvery { deviceController.tapControl("Search") } returns OperationResult.failure("Failure")
        
        dispatcher.dispatch(CommandParser.AgentCommand(CommandParser.Type.TAP, "Search", ""))
        
        verify(timeout = 2000) {
            overlay.setMessage("I couldn't tap \u201CSearch\u201D. Could you clarify where it is, or tap it yourself?")
        }
    }

    @Test
    fun `Gemini action failure under limit appends error to session history`() {
        val session = GeminiSession("Tap submit")
        val action = GeminiAction("Try tap", "TAP", "Submit", "", false)
        
        every { screenReader.readScreen() } returns mockk(relaxed = true) {
            every { available } returns true
            every { appName } returns "TestApp"
            every { layoutXml } returns "<button label=\"Submit\" />"
        }

        dispatcher.handleActionFailure(session, action, "Element not clickable")

        assertEquals(1, session.consecutiveFailures)
        assertEquals(1, session.history.size)
    }

    @Test
    fun `Gemini action reaching max consecutive failures terminates session`() {
        val session = GeminiSession("Tap submit")
        session.consecutiveFailures = 2
        val action = GeminiAction("Try tap", "TAP", "Submit", "", false)

        dispatcher.handleActionFailure(session, action, "Element not clickable")

        assertEquals(3, session.consecutiveFailures)
        verify {
            overlay.setMessage("I couldn't tap \u201CSubmit\u201D. Could you clarify where it is, or tap it yourself?")
        }
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private fun command(type: CommandParser.Type) =
        CommandParser.AgentCommand(type, "", "")
}
