package com.tappy.assistant

/**
 * The result of any device operation — screen reading, tap, type, scroll, navigate, or reply.
 * Shared across all modules in the assistant.
 */
data class OperationResult(
    val successful: Boolean,
    val message: String
) {
    companion object {
        fun success(message: String) = OperationResult(true, message)
        fun failure(message: String) = OperationResult(false, message)
    }
}
