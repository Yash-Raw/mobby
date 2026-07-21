package com.mobby.assistant

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * A lightweight, local NLP Intent Classifier using TF-IDF token vectorization
 * and Cosine Similarity matching against semantic exemplars. Runs offline with zero latency.
 */
object LocalIntentClassifier {

    private val STOP_WORDS = setOf(
        "a", "an", "the", "is", "are", "was", "were", "of", "to", "in", "on", "at", 
        "for", "with", "me", "i", "my", "you", "your", "this", "that", "please", "could", "would", "can"
    )

    private val TRAINING_PHRASES = listOf(
        // DESCRIBE_SCREEN
        TrainingInstance("what is on the screen", CommandParser.Type.DESCRIBE_SCREEN),
        TrainingInstance("what's on screen", CommandParser.Type.DESCRIBE_SCREEN),
        TrainingInstance("read the screen", CommandParser.Type.DESCRIBE_SCREEN),
        TrainingInstance("describe this screen", CommandParser.Type.DESCRIBE_SCREEN),
        TrainingInstance("what do you see", CommandParser.Type.DESCRIBE_SCREEN),
        TrainingInstance("where am i", CommandParser.Type.DESCRIBE_SCREEN),
        TrainingInstance("tell me what is on screen", CommandParser.Type.DESCRIBE_SCREEN),

        // GUIDE_SCREEN
        TrainingInstance("how do i use this screen", CommandParser.Type.GUIDE_SCREEN),
        TrainingInstance("guide me", CommandParser.Type.GUIDE_SCREEN),
        TrainingInstance("help me use this app", CommandParser.Type.GUIDE_SCREEN),
        TrainingInstance("what can i do here", CommandParser.Type.GUIDE_SCREEN),
        TrainingInstance("explain this screen", CommandParser.Type.GUIDE_SCREEN),
        TrainingInstance("what do i do next", CommandParser.Type.GUIDE_SCREEN),

        // LIST_CONTROLS
        TrainingInstance("what can i tap", CommandParser.Type.LIST_CONTROLS),
        TrainingInstance("list controls", CommandParser.Type.LIST_CONTROLS),
        TrainingInstance("show controls", CommandParser.Type.LIST_CONTROLS),
        TrainingInstance("what buttons are here", CommandParser.Type.LIST_CONTROLS),
        TrainingInstance("show me clickable buttons", CommandParser.Type.LIST_CONTROLS),

        // BACK
        TrainingInstance("go back", CommandParser.Type.BACK),
        TrainingInstance("back please", CommandParser.Type.BACK),
        TrainingInstance("return to previous screen", CommandParser.Type.BACK),
        TrainingInstance("go to last screen", CommandParser.Type.BACK),

        // HOME
        TrainingInstance("go home", CommandParser.Type.HOME),
        TrainingInstance("home screen", CommandParser.Type.HOME),
        TrainingInstance("go to home", CommandParser.Type.HOME),
        TrainingInstance("open home screen", CommandParser.Type.HOME),

        // SCROLL
        TrainingInstance("scroll down", CommandParser.Type.SCROLL),
        TrainingInstance("scroll up", CommandParser.Type.SCROLL),
        TrainingInstance("move down", CommandParser.Type.SCROLL),
        TrainingInstance("move up", CommandParser.Type.SCROLL),
        TrainingInstance("page down", CommandParser.Type.SCROLL),
        TrainingInstance("page up", CommandParser.Type.SCROLL),
        TrainingInstance("swipe up", CommandParser.Type.SCROLL),
        TrainingInstance("swipe down", CommandParser.Type.SCROLL),

        // SEND_CURRENT_MESSAGE
        TrainingInstance("send message", CommandParser.Type.SEND_CURRENT_MESSAGE),
        TrainingInstance("send this", CommandParser.Type.SEND_CURRENT_MESSAGE),
        TrainingInstance("hit send", CommandParser.Type.SEND_CURRENT_MESSAGE),
        TrainingInstance("tap send button", CommandParser.Type.SEND_CURRENT_MESSAGE),
        TrainingInstance("submit form", CommandParser.Type.SEND_CURRENT_MESSAGE),

        // CLOSE
        TrainingInstance("goodbye", CommandParser.Type.CLOSE),
        TrainingInstance("stop listening", CommandParser.Type.CLOSE),
        TrainingInstance("exit mobby", CommandParser.Type.CLOSE),
        TrainingInstance("close overlay", CommandParser.Type.CLOSE),
        TrainingInstance("never mind", CommandParser.Type.CLOSE),
        TrainingInstance("nevermind", CommandParser.Type.CLOSE),
        TrainingInstance("go to sleep", CommandParser.Type.CLOSE),

        // TAP
        TrainingInstance("tap on WhatsApp", CommandParser.Type.TAP),
        TrainingInstance("click on search button", CommandParser.Type.TAP),
        TrainingInstance("press log in", CommandParser.Type.TAP),
        TrainingInstance("open settings", CommandParser.Type.TAP),
        TrainingInstance("tap search field", CommandParser.Type.TAP),
        TrainingInstance("click next", CommandParser.Type.TAP),

        // TYPE_TEXT
        TrainingInstance("type hello", CommandParser.Type.TYPE_TEXT),
        TrainingInstance("write message hello", CommandParser.Type.TYPE_TEXT),
        TrainingInstance("enter text hello", CommandParser.Type.TYPE_TEXT),
        TrainingInstance("type this message", CommandParser.Type.TYPE_TEXT),

        // CHECK_MESSAGES_FROM
        TrainingInstance("check messages from Maya", CommandParser.Type.CHECK_MESSAGES_FROM),
        TrainingInstance("show texts from John", CommandParser.Type.CHECK_MESSAGES_FROM),
        TrainingInstance("do i have messages from Sarah", CommandParser.Type.CHECK_MESSAGES_FROM),
        TrainingInstance("check if i have text from mom", CommandParser.Type.CHECK_MESSAGES_FROM),

        // REPLY_TO_PERSON
        TrainingInstance("reply to Maya that i will call later", CommandParser.Type.REPLY_TO_PERSON),
        TrainingInstance("text Sarah that i am on my way", CommandParser.Type.REPLY_TO_PERSON),
        TrainingInstance("reply to mom saying okay", CommandParser.Type.REPLY_TO_PERSON),

        // REPLY_IN_CURRENT_CONVERSATION
        TrainingInstance("reply that i am running late", CommandParser.Type.REPLY_IN_CURRENT_CONVERSATION),
        TrainingInstance("say that i will be there soon", CommandParser.Type.REPLY_IN_CURRENT_CONVERSATION)
    )

    private val vocabulary: List<String>
    private val idfs: Map<String, Double>
    private val trainedVectors: List<DoubleArray>

    init {
        // Build vocabulary
        val allTokens = TRAINING_PHRASES.flatMap { tokenize(it.phrase) }.distinct()
        vocabulary = allTokens

        // Calculate IDFs
        val numDocuments = TRAINING_PHRASES.size.toDouble()
        idfs = vocabulary.associateWith { term ->
            val docsWithTerm = TRAINING_PHRASES.count { tokenize(it.phrase).contains(term) }
            ln(1.0 + numDocuments / (1.0 + docsWithTerm))
        }

        // Precompute TF-IDF vectors for training data
        trainedVectors = TRAINING_PHRASES.map { instance ->
            val tfIdf = computeTfIdf(instance.phrase)
            normalize(tfIdf)
        }
    }

    fun classify(query: String): ClassificationResult? {
        val queryVector = normalize(computeTfIdf(query))
        
        var maxSimilarity = -1.0
        var bestMatchIdx = -1

        for (i in trainedVectors.indices) {
            val similarity = cosineSimilarity(queryVector, trainedVectors[i])
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity
                bestMatchIdx = i
            }
        }

        if (bestMatchIdx == -1) return null

        val queryTokens = tokenize(query)
        val bestPhraseTokens = tokenize(TRAINING_PHRASES[bestMatchIdx].phrase)
        val intersectionSize = queryTokens.filter { bestPhraseTokens.contains(it) }.size

        // To prevent false positives from single-word overlaps in multi-word exemplars:
        val isAccepted = if (bestPhraseTokens.size > 1 && intersectionSize <= 1) {
            false
        } else {
            maxSimilarity >= 0.35
        }

        return if (isAccepted) {
            ClassificationResult(TRAINING_PHRASES[bestMatchIdx].type, maxSimilarity)
        } else {
            null
        }
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "") // Keep letters, numbers, spaces
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && !STOP_WORDS.contains(it) }
    }

    private fun computeTfIdf(text: String): DoubleArray {
        val tokens = tokenize(text)
        val tfIdf = DoubleArray(vocabulary.size)
        if (tokens.isEmpty()) return tfIdf

        val termCounts = tokens.groupingBy { it }.eachCount()

        for (i in vocabulary.indices) {
            val term = vocabulary[i]
            val count = termCounts[term] ?: 0
            val tf = count.toDouble() / tokens.size
            val idf = idfs[term] ?: 0.0
            tfIdf[i] = tf * idf
        }
        return tfIdf
    }

    private fun normalize(vector: DoubleArray): DoubleArray {
        val magnitude = sqrt(vector.fold(0.0) { sum, value -> sum + value * value })
        if (magnitude == 0.0) return vector
        return DoubleArray(vector.size) { i -> vector[i] / magnitude }
    }

    private fun cosineSimilarity(vec1: DoubleArray, vec2: DoubleArray): Double {
        var dotProduct = 0.0
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
        }
        return dotProduct
    }

    data class TrainingInstance(val phrase: String, val type: CommandParser.Type)
    data class ClassificationResult(val type: CommandParser.Type, val confidence: Double)
}
