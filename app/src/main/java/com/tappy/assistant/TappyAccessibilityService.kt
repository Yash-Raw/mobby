package com.tappy.assistant

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The device-control layer. Android creates it only after the device owner explicitly enables
 * Mobby in Accessibility Settings. All screen text remains on-device and all outward actions
 * (tap, type, and send) require a confirmation in Mobby.
 *
 * This class is a thin orchestrator that wires together focused modules:
 * - [ScreenReader] — reads the accessibility tree and describes what's on screen.
 * - [DeviceController] — performs tap, type, scroll, and navigation actions.
 * - [OverlayManager] — constructs and manages the floating panel UI.
 * - [VoiceController] — handles the SpeechRecognizer lifecycle.
 * - [CommandDispatcher] — routes parsed commands to the correct handler.
 */
class TappyAccessibilityService : AccessibilityService(),
    OverlayManager.Callbacks,
    VoiceController.Listener {

    // ── Module instances (created in onServiceConnected) ─────────────────

    private var screenReader: ScreenReader? = null
    private var deviceController: DeviceController? = null
    private var overlayManager: OverlayManager? = null
    private var voiceController: VoiceController? = null
    private var geminiBrain: GeminiBrain? = null
    internal var commandDispatcher: CommandDispatcher? = null
    private var wakewordDetector: WakewordDetector? = null

    // ── Service lifecycle ────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "onServiceConnected: accessibility service active")

        // Configure accessibility flags.
        val info = serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
            serviceInfo = info
            accessibilityButtonController.registerAccessibilityButtonCallback(
                object : AccessibilityButtonController.AccessibilityButtonCallback() {
                    override fun onClicked(controller: AccessibilityButtonController) {
                        Log.d(TAG, "accessibility button clicked")
                        overlayManager?.toggle()
                    }
                }
            )
        } else {
            serviceInfo = info
        }

        // Wire up all modules.
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager

        screenReader = ScreenReader(object : ScreenReader.AccessibilityNodeProvider {
            override val rootInActiveWindow: AccessibilityNodeInfo?
                get() = this@TappyAccessibilityService.rootInActiveWindow
            override val packageManager: PackageManager
                get() = this@TappyAccessibilityService.packageManager
        })

        deviceController = DeviceController(this)

        overlayManager = OverlayManager(this, wm, this)

        voiceController = VoiceController(this)

        geminiBrain = GeminiBrain(this)

        commandDispatcher = CommandDispatcher(this, screenReader!!, deviceController!!, overlayManager!!, geminiBrain!!)
        
        wakewordDetector = WakewordDetector(this) {
            Log.d(TAG, "Wakeword detected: summoning listener")
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                wakewordDetector?.stopListening()
                onSpeakPressed()
            }
        }
        wakewordDetector?.startListening()

        Log.i(TAG, "onServiceConnected: all modules wired")
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: tearing down accessibility service")
        if (instance === this) instance = null
        commandDispatcher?.destroy()
        overlayManager?.destroy()
        voiceController?.destroy()
        wakewordDetector?.stopListening()
        wakewordDetector = null
        screenReader = null
        deviceController = null
        overlayManager = null
        voiceController = null
        commandDispatcher = null
        geminiBrain = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Mobby reads the active window only when the user asks a screen-related question.
    }

    override fun onInterrupt() {
        // There is no continuous task to interrupt.
    }

    // ── OverlayManager.Callbacks ─────────────────────────────────────────

    override fun onSpeakPressed() {
        Log.d(TAG, "onSpeakPressed: starting voice recognition")
        wakewordDetector?.stopListening()
        overlayManager?.setVoiceButtonEnabled(false)
        overlayManager?.setMessage("Listening\u2026")
        val error = voiceController?.startListening(this)
        if (error != null) {
            Log.w(TAG, "onSpeakPressed: voice start failed — $error")
            overlayManager?.setVoiceButtonEnabled(true)
            overlayManager?.setMessage(error)
        }
    }

    override fun onGuidePressed() {
        Log.d(TAG, "onGuidePressed: describing active window with guidance")
        val result = screenReader?.describeActiveWindow(true)
            ?: OperationResult.failure("Mobby controls are not ready yet.")
        overlayManager?.setMessage(result.message)
    }

    override fun onClosePressed() {
        Log.d(TAG, "onClosePressed: cancelling active Gemini session and restarting wakeword")
        commandDispatcher?.cancelActiveSession()
        wakewordDetector?.startListening()
    }

    override fun onSpeakFinished() {
        if (overlayManager?.isShowing == true) {
            Log.d(TAG, "onSpeakFinished: starting voice recognition for continuous conversation")
            onSpeakPressed()
        }
    }

    // ── VoiceController.Listener ─────────────────────────────────────────

    override fun onVoiceResult(transcript: String) {
        Log.d(TAG, "onVoiceResult: \"$transcript\"")
        overlayManager?.setVoiceButtonEnabled(true)
        if (overlayManager?.handleVoiceConfirmation(transcript) == true) {
            return
        }
        val command = CommandParser.parse(transcript)
        commandDispatcher?.dispatch(command)
    }

    override fun onVoiceError(errorMessage: String) {
        Log.w(TAG, "onVoiceError: $errorMessage")
        overlayManager?.setVoiceButtonEnabled(true)
        overlayManager?.setMessage("Going to sleep.")
        overlayManager?.remove()
    }

    // ── Static API (used by MainActivity and other components) ───────────

    companion object {
        private const val TAG = "MobbyService"

        @Volatile
        internal var instance: TappyAccessibilityService? = null
            private set

        fun describeScreen(): OperationResult =
            withService { it.screenReader?.describeActiveWindow(false) }

        fun guideCurrentScreen(): OperationResult =
            withService { it.screenReader?.describeActiveWindow(true) }

        fun listControls(): OperationResult =
            withService { it.screenReader?.listActiveControls() }

        fun tapVisibleControl(label: String): OperationResult =
            withService { it.deviceController?.tapControl(label) }

        fun typeIntoFocusedField(value: String): OperationResult =
            withService { it.deviceController?.setFocusedText(value) }

        fun scroll(direction: String): OperationResult =
            withService { it.deviceController?.scrollActiveWindow(direction) }

        fun goBack(): OperationResult =
            withService { it.deviceController?.goBack() }

        fun goHome(): OperationResult =
            withService { it.deviceController?.goHome() }

        /** Opens the small accessibility overlay without replacing the app the user is working in. */
        fun openQuickPanel(): Boolean {
            val service = instance ?: return false
            val overlay = service.overlayManager ?: return false
            overlay.show()
            return overlay.isShowing
        }

        private fun withService(operation: (TappyAccessibilityService) -> OperationResult?): OperationResult {
            val service = instance
                ?: return OperationResult.failure("Turn on Mobby controls in Android Accessibility Settings first.")
            return operation(service)
                ?: OperationResult.failure("Mobby controls are still initializing. Please try again.")
        }
    }
}
