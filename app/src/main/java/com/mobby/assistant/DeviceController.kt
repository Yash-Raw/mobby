package com.mobby.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Performs device-level actions: tap a control, type text, scroll, and global navigation.
 * All actions require the accessibility node tree from the active window.
 *
 * The caller (overlay or command dispatcher) is responsible for user confirmation
 * before invoking destructive actions like tap, type, or send.
 */
class DeviceController(private val service: AccessibilityService) {

    /** Taps a visible control whose label matches [wantedLabel]. */
    suspend fun tapControl(wantedLabel: String): OperationResult {
        if (TextUtils.isEmpty(wantedLabel)) {
            return OperationResult.failure("Tell me the label of the button or control to tap.")
        }
        return tapControlInternal(wantedLabel, attemptSelfCorrection = true)
    }

    private suspend fun tapControlInternal(wantedLabel: String, attemptSelfCorrection: Boolean): OperationResult {
        val root = service.rootInActiveWindow
            ?: return OperationResult.failure("I can't reach the current screen.")
        var target: AccessibilityNodeInfo? = null
        try {
            target = findActionableNode(root, wantedLabel)
            if (target != null) {
                if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.d(TAG, "tapControl: tapped \"$wantedLabel\" via performAction")
                    return OperationResult.success("Tapped \u201C$wantedLabel\u201D.")
                }
                
                // Fallback 1: Coordinate-based gesture tap on the target
                val rect = Rect()
                target.getBoundsInScreen(rect)
                if (tapGesture(rect.centerX().toFloat(), rect.centerY().toFloat())) {
                    Log.d(TAG, "tapControl: tapped \"$wantedLabel\" via coordinate gesture fallback")
                    return OperationResult.success("Tapped \u201C$wantedLabel\u201D.")
                }

                // Fallback 2: Tap standard container bounds (the target's parent/container)
                val parent = target.parent
                if (parent != null) {
                    val parentRect = Rect()
                    parent.getBoundsInScreen(parentRect)
                    NodeCompat.recycle(parent)
                    if (tapGesture(parentRect.centerX().toFloat(), parentRect.centerY().toFloat())) {
                        Log.d(TAG, "tapControl: tapped \"$wantedLabel\" via container bounds fallback")
                        return OperationResult.success("Tapped \u201C$wantedLabel\u201D.")
                    }
                }
            }
            
            // Fallback 3: Find any matching node by label (clickable or not) and tap its coordinates
            val fallbackNode = findNodeByLabel(root, wantedLabel)
            if (fallbackNode != null) {
                val rect = Rect()
                fallbackNode.getBoundsInScreen(rect)
                val fallbackParent = fallbackNode.parent
                NodeCompat.recycle(fallbackNode)
                if (tapGesture(rect.centerX().toFloat(), rect.centerY().toFloat())) {
                    NodeCompat.recycle(fallbackParent)
                    Log.d(TAG, "tapControl: tapped \"$wantedLabel\" via text coordinate fallback")
                    return OperationResult.success("Tapped \u201C$wantedLabel\u201D.")
                }
                if (fallbackParent != null) {
                    val pRect = Rect()
                    fallbackParent.getBoundsInScreen(pRect)
                    NodeCompat.recycle(fallbackParent)
                    if (tapGesture(pRect.centerX().toFloat(), pRect.centerY().toFloat())) {
                        Log.d(TAG, "tapControl: tapped \"$wantedLabel\" via text container bounds fallback")
                        return OperationResult.success("Tapped \u201C$wantedLabel\u201D.")
                    }
                }
            }

            // If we get here, normal and container-bounds taps failed.
            if (attemptSelfCorrection) {
                Log.d(TAG, "tapControl: attempting self-correction scroll loops for \"$wantedLabel\"")
                
                // 1. Scroll down and retry
                Log.d(TAG, "tapControl: scrolling down to bring \"$wantedLabel\" into view")
                val scrollDownResult = scrollActiveWindow("down")
                if (scrollDownResult.successful) {
                    delay(1000)
                    val retryResult = tapControlInternal(wantedLabel, attemptSelfCorrection = false)
                    if (retryResult.successful) {
                        return retryResult
                    }
                }

                // 2. Scroll up and retry
                Log.d(TAG, "tapControl: scrolling up to bring \"$wantedLabel\" into view")
                val scrollUpResult = scrollActiveWindow("up")
                if (scrollUpResult.successful) {
                    delay(1000)
                    val retryResult = tapControlInternal(wantedLabel, attemptSelfCorrection = false)
                    if (retryResult.successful) {
                        return retryResult
                    }
                }
            }

            Log.w(TAG, "tapControl: failed to tap \"$wantedLabel\" after all fallbacks/retries")
            return OperationResult.failure("Android would not activate \u201C$wantedLabel\u201D.")
        } finally {
            NodeCompat.recycle(target)
            NodeCompat.recycle(root)
        }
    }

    /** Sets text in the currently focused (or first editable) field. */
    suspend fun setFocusedText(value: String): OperationResult {
        if (TextUtils.isEmpty(value)) return OperationResult.failure("The text was empty.")
        val root = service.rootInActiveWindow
            ?: return OperationResult.failure("I can't reach the current screen.")
        var input: AccessibilityNodeInfo? = null
        return try {
            input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditableNode(root)
            if (input == null) {
                Log.w(TAG, "setFocusedText: no editable field found")
                OperationResult.failure("I couldn't find a text field. Tap the message box first, then ask me to type.")
            } else {
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                }
                if (input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                    Log.d(TAG, "setFocusedText: text set (${value.length} chars)")
                    OperationResult.success("Typed your message. Say \u201Csend\u201D when you have checked it.")
                } else {
                    Log.w(TAG, "setFocusedText: ACTION_SET_TEXT refused")
                    OperationResult.failure("This app did not let Mobby enter text in that field.")
                }
            }
        } finally {
            NodeCompat.recycle(input)
            NodeCompat.recycle(root)
        }
    }

    /** Scrolls the active window in the given [direction] ("up" or "down"). */
    suspend fun scrollActiveWindow(direction: String): OperationResult {
        val root = service.rootInActiveWindow
            ?: return OperationResult.failure("I can't reach the current screen.")
        var scrollable: AccessibilityNodeInfo? = null
        return try {
            scrollable = findScrollableNode(root)
            if (scrollable == null) {
                Log.w(TAG, "scrollActiveWindow: no scrollable node found")
                OperationResult.failure("I couldn't find anything to scroll on this screen.")
            } else {
                val action = if (direction == "up") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                if (scrollable.performAction(action)) {
                    Log.d(TAG, "scrollActiveWindow: scrolled $direction")
                    OperationResult.success("Scrolled $direction.")
                } else {
                    Log.w(TAG, "scrollActiveWindow: scroll $direction refused")
                    OperationResult.failure("This screen cannot scroll $direction.")
                }
            }
        } finally {
            NodeCompat.recycle(scrollable)
            NodeCompat.recycle(root)
        }
    }

    /** Performs the system Back action. */
    suspend fun goBack(): OperationResult {
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        Log.d(TAG, "goBack: ${if (success) "success" else "refused"}")
        return if (success) OperationResult.success("Went back.")
        else OperationResult.failure("Android would not go back from this screen.")
    }

    /** Performs the system Home action. */
    suspend fun goHome(): OperationResult {
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        Log.d(TAG, "goHome: ${if (success) "success" else "refused"}")
        return if (success) OperationResult.success("Opened Home.")
        else OperationResult.failure("Android would not open Home.")
    }

    // ── Node tree search helpers ─────────────────────────────────────────

    private fun findActionableNode(node: AccessibilityNodeInfo, wantedLabel: String): AccessibilityNodeInfo? {
        if (ScreenReader.labelsMatch(ScreenReader.nodeLabel(node), wantedLabel)) {
            val actionOwner = findClickableOwner(node)
            if (actionOwner != null) return actionOwner
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findActionableNode(child, wantedLabel)
            NodeCompat.recycle(child)
            if (found != null) return found
        }
        return null
    }

    private fun findClickableOwner(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = NodeCompat.copy(node)
        while (current != null) {
            if (current.isClickable || current.isLongClickable) return current
            val parent = current.parent
            NodeCompat.recycle(current)
            current = parent
        }
        return null
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return NodeCompat.copy(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findEditableNode(child)
            NodeCompat.recycle(child)
            if (found != null) return found
        }
        return null
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return NodeCompat.copy(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findScrollableNode(child)
            NodeCompat.recycle(child)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByLabel(node: AccessibilityNodeInfo, wantedLabel: String): AccessibilityNodeInfo? {
        if (ScreenReader.labelsMatch(ScreenReader.nodeLabel(node), wantedLabel)) {
            return NodeCompat.copy(node)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findNodeByLabel(child, wantedLabel)
            NodeCompat.recycle(child)
            if (found != null) return found
        }
        return null
    }

    private suspend fun tapGesture(x: Float, y: Float): Boolean = suspendCancellableCoroutine { continuation ->
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        val handler = Handler(Looper.getMainLooper())
        handler.post {
            try {
                val success = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }, null)
                if (!success) {
                    if (continuation.isActive) continuation.resume(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error dispatching gesture", e)
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    companion object {
        private const val TAG = "MobbyDevice"
    }
}
