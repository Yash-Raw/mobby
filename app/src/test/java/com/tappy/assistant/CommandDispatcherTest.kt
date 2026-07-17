package com.tappy.assistant

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests for [CommandDispatcher] — verifies routing of commands to the correct handlers
 * and that destructive commands show confirmation dialogs.
 */
class CommandDispatcherTest {

    private lateinit var screenReader: ScreenReader
    private lateinit var deviceController: DeviceController
    private lateinit var overlay: OverlayManager
    private lateinit var geminiBrain: GeminiBrain
    private lateinit var dispatcher: CommandDispatcher

    @Before
    fun setUp() {
        screenReader = mockk(relaxed = true)
        deviceController = mockk(relaxed = true)
        overlay = mockk(relaxed = true)
        geminiBrain = mockk(relaxed = true)
        dispatcher = CommandDispatcher(screenReader, deviceController, overlay, geminiBrain)
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
        every { deviceController.goBack() } returns OperationResult.success("Went back.")

        dispatcher.dispatch(command(CommandParser.Type.BACK))

        verify { deviceController.goBack() }
        verify { overlay.setMessage("Went back.") }
    }

    @Test
    fun `HOME calls goHome`() {
        every { deviceController.goHome() } returns OperationResult.success("Opened Home.")

        dispatcher.dispatch(command(CommandParser.Type.HOME))

        verify { deviceController.goHome() }
        verify { overlay.setMessage("Opened Home.") }
    }

    @Test
    fun `SCROLL calls scrollActiveWindow with direction`() {
        every { deviceController.scrollActiveWindow("down") } returns
                OperationResult.success("Scrolled down.")

        dispatcher.dispatch(CommandParser.AgentCommand(CommandParser.Type.SCROLL, "down", ""))

        verify { deviceController.scrollActiveWindow("down") }
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

    // ── Helper ──────────────────────────────────────────────────────────

    private fun command(type: CommandParser.Type) =
        CommandParser.AgentCommand(type, "", "")
}
