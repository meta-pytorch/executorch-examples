package com.mathpal.app.inference

object StepParser {

    sealed class StepEvent {
        data class NewStep(val stepNumber: Int, val text: String) : StepEvent()
        data class StepContinuation(val text: String) : StepEvent()
        data class FinalAnswer(val answer: String) : StepEvent()
        data object ThinkingStart : StepEvent()
        data object ThinkingEnd : StepEvent()
        data object EndOfResponse : StepEvent()
    }

    private const val THINK_OPEN = "<think>"
    private const val THINK_CLOSE = "</think>"
    private const val FINAL_MARKER = "####"
    private const val STOP_TOKEN = "<|im_end|>"

    private val STEP_PREFIX = Regex("""^Step\s+(\d+)\s*[:.]""")
    private val NUMBERED_PREFIX = Regex("""^(\d+)[).:]""")

    fun parseStreamingToken(token: String, accumulator: StringBuilder): StepEvent? {
        accumulator.append(token)
        val buffer = accumulator.toString()

        if (buffer.endsWith(STOP_TOKEN)) {
            val before = buffer.removeSuffix(STOP_TOKEN)
            accumulator.clear()
            accumulator.append(before)
            return StepEvent.EndOfResponse
        }

        if (buffer.endsWith(THINK_OPEN)) {
            accumulator.clear()
            return StepEvent.ThinkingStart
        }

        if (buffer.endsWith(THINK_CLOSE)) {
            accumulator.clear()
            return StepEvent.ThinkingEnd
        }

        val hashIdx = buffer.lastIndexOf(FINAL_MARKER)
        if (hashIdx >= 0) {
            val afterMarker = buffer.substring(hashIdx + FINAL_MARKER.length).trim()
            if (afterMarker.isNotEmpty()) {
                accumulator.clear()
                return StepEvent.FinalAnswer(afterMarker)
            }
            return null
        }

        if (token.contains("\n")) {
            val currentLine = buffer.substringAfterLast("\n").trimStart()

            val stepMatch = STEP_PREFIX.find(currentLine)
            if (stepMatch != null) {
                val stepNum = stepMatch.groupValues[1].toInt()
                val text = currentLine.substring(stepMatch.range.last + 1).trimStart()
                return StepEvent.NewStep(stepNum, text)
            }

            val numMatch = NUMBERED_PREFIX.find(currentLine)
            if (numMatch != null) {
                val stepNum = numMatch.groupValues[1].toInt()
                val text = currentLine.substring(numMatch.range.last + 1).trimStart()
                return StepEvent.NewStep(stepNum, text)
            }
        }

        if (!token.contains("\n") && token.isNotEmpty()) {
            return StepEvent.StepContinuation(token)
        }

        return null
    }
}
