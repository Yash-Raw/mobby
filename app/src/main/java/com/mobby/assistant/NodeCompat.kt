package com.mobby.assistant

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Compatibility helpers for [AccessibilityNodeInfo] lifecycle management.
 *
 * Before API 33, callers were required to call [AccessibilityNodeInfo.obtain] to copy
 * a node and [AccessibilityNodeInfo.recycle] to return it to the pool. Starting with
 * API 33 the pool is gone — `obtain` and `recycle` are no-ops and the copy constructor
 * should be used instead. These helpers centralise the version check so the rest of
 * the codebase can avoid `@Suppress("DEPRECATION")` annotations.
 */
internal object NodeCompat {

    /**
     * Returns an independent copy of [node] that the caller owns.
     * On API 33+ uses the copy constructor; on older versions falls back to `obtain`.
     */
    fun copy(node: AccessibilityNodeInfo): AccessibilityNodeInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AccessibilityNodeInfo(node)
        } else {
            @Suppress("DEPRECATION")
            AccessibilityNodeInfo.obtain(node)
        }

    /**
     * Releases [node] back to the pool if running on a version that requires it.
     * Safe to call on any API level.
     */
    fun recycle(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }
}
