package com.tappy.assistant

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Reads the accessibility node tree of the active window and produces structured
 * descriptions of visible text and controls. All screen data stays on-device.
 *
 * Requires an [AccessibilityNodeProvider] to access the active window's root node
 * and the system's PackageManager.
 */
class ScreenReader(private val provider: AccessibilityNodeProvider) {

    /** Describes or guides through the current screen. */
    fun describeActiveWindow(giveGuidance: Boolean): OperationResult {
        val screen = readScreen()
        if (!screen.available) return OperationResult.failure(screen.error)

        return if (giveGuidance) {
            if (screen.controls.isEmpty()) {
                OperationResult.success(
                    "You're in ${screen.appName}. I can read: ${screen.visibleText}. " +
                            "There are no labelled controls I can safely suggest yet."
                )
            } else {
                OperationResult.success(
                    "You're in ${screen.appName}. I can see ${screen.visibleText}. " +
                            "Start with ${screen.controls[0]}. Other available controls: ${join(screen.controls, 6)}."
                )
            }
        } else {
            val controlsSuffix = if (screen.controls.isEmpty()) "." else ". Controls: ${join(screen.controls, 6)}."
            OperationResult.success("On ${screen.appName}, I can see: ${screen.visibleText}$controlsSuffix")
        }
    }

    /** Lists all labelled controls on the current screen. */
    fun listActiveControls(): OperationResult {
        val screen = readScreen()
        if (!screen.available) return OperationResult.failure(screen.error)
        return if (screen.controls.isEmpty()) {
            OperationResult.success("I couldn't find labelled buttons or input fields on this screen.")
        } else {
            OperationResult.success("Available controls: ${join(screen.controls, MAX_SCREEN_ITEMS)}.")
        }
    }

    internal fun readScreen(): ScreenContents {
        val root = provider.rootInActiveWindow
            ?: return ScreenContents.unavailable("I can't read the current screen. Unlock the phone or open an app, then try again.")
        return try {
            val text = LinkedHashSet<String>()
            val controls = LinkedHashSet<String>()
            collectScreenItems(root, text, controls)
            val layoutXml = buildLayoutXml(root, 0)
            val packageName = root.packageName?.toString() ?: "this app"
            val appName = appLabel(packageName)
            Log.d(TAG, "readScreen: app=$appName, text=${text.size}, controls=${controls.size}")
            ScreenContents.available(appName, join(ArrayList(text), 7), ArrayList(controls), layoutXml)
        } finally {
            NodeCompat.recycle(root)
        }
    }

    private fun collectScreenItems(node: AccessibilityNodeInfo?, text: MutableSet<String>, controls: MutableSet<String>) {
        if (node == null || text.size + controls.size >= MAX_SCREEN_ITEMS) return

        val label = nodeLabel(node)
        if (label.isNotEmpty()) {
            when {
                node.isClickable -> controls.add(label)
                node.isEditable -> controls.add("$label (text field)")
                else -> text.add(label)
            }
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectScreenItems(child, text, controls)
            NodeCompat.recycle(child)
        }
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildLayoutXml(node: AccessibilityNodeInfo?, depth: Int): String {
        if (node == null || depth > 25) return ""

        val label = escapeXml(nodeLabel(node))
        val childrenXml = StringBuilder()
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val childXml = buildLayoutXml(child, depth + 1)
            NodeCompat.recycle(child)
            childrenXml.append(childXml)
        }

        val childContent = childrenXml.toString().trim()

        return when {
            node.isEditable -> {
                val focused = if (node.isFocused) " focused=\"true\"" else ""
                "<input label=\"$label\"$focused />\n"
            }
            node.isClickable -> {
                if (childContent.isNotEmpty()) {
                    "<button label=\"$label\">\n$childContent\n</button>\n"
                } else {
                    "<button label=\"$label\" />\n"
                }
            }
            node.isScrollable -> {
                if (childContent.isNotEmpty()) {
                    "<list>\n$childContent\n</list>\n"
                } else {
                    ""
                }
            }
            label.isNotEmpty() -> {
                "<text>$label</text>\n"
            }
            else -> {
                if (childContent.isNotEmpty()) "$childContent\n" else ""
            }
        }
    }

    private fun appLabel(packageName: String): String =
        try {
            val pm = provider.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            val separator = packageName.lastIndexOf('.')
            if (separator >= 0) packageName.substring(separator + 1) else packageName
        }

    internal class ScreenContents internal constructor(
        val available: Boolean,
        val appName: String,
        val visibleText: String,
        val controls: List<String>,
        val layoutXml: String,
        val error: String
    ) {
        companion object {
            fun available(appName: String, visibleText: String, controls: List<String>, layoutXml: String) =
                ScreenContents(true, appName, visibleText, controls, layoutXml, "")

            fun unavailable(error: String) =
                ScreenContents(false, "", "", emptyList(), "", error)
        }
    }

    companion object {
        private const val TAG = "MobbyScreen"
        internal const val MAX_SCREEN_ITEMS = 18

        /** Extracts the best human-readable label from an accessibility node. */
        fun nodeLabel(node: AccessibilityNodeInfo): String {
            var label: CharSequence? = node.text
            if (label.isNullOrEmpty()) label = node.contentDescription
            if (label.isNullOrEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) label = node.hintText
            return label?.toString()?.trim()?.replace(Regex("\\s+"), " ") ?: ""
        }

        /** Checks whether a visible label and a requested label refer to the same control. */
        fun labelsMatch(visible: String, requested: String): Boolean {
            val actual = visible.lowercase(Locale.ROOT)
            val wanted = requested.trim().lowercase(Locale.ROOT)
            return actual.isNotEmpty() && (actual == wanted || actual.contains(wanted) || wanted.contains(actual))
        }

        /** Joins a list of strings, capped at [max] items. */
        fun join(values: List<String>, max: Int): String {
            if (values.isEmpty()) return "no readable text"
            return values.take(max).joinToString(", ")
        }
    }

    /**
     * Abstraction over the accessibility service, so ScreenReader doesn't depend
     * directly on [android.accessibilityservice.AccessibilityService].
     */
    interface AccessibilityNodeProvider {
        val rootInActiveWindow: AccessibilityNodeInfo?
        val packageManager: PackageManager
    }
}
