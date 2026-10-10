package com.mathpal.app.ui.solve

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.mathpal.app.MathPalApplication
import com.mathpal.app.inference.AnswerValidator
import com.mathpal.app.inference.PromptFormatter
import org.pytorch.executorch.extension.llm.LlmCallback

class MathViewModel(application: Application) : AndroidViewModel(application), LlmCallback {

    private val app = application as MathPalApplication
    private val engine = app.inferenceEngine
    private val mainHandler = Handler(Looper.getMainLooper())

    val steps = mutableStateListOf<StepState>()
    var finalAnswer: String? by mutableStateOf(null)
        private set
    var isGenerating by mutableStateOf(false)
        private set
    var isModelLoaded by mutableStateOf(false)
        private set
    var loadingMessage by mutableStateOf("Loading math brain...")
        private set
    var currentQuestion by mutableStateOf("")
        private set

    private val responseAccumulator = StringBuilder()
    private val currentStepText = StringBuilder()
    private var isInThinking = false

    fun ensureModelLoaded() {
        if (engine.isLoaded) {
            isModelLoaded = true
            return
        }
        loadingMessage = "Loading math brain..."
        engine.loadAsync { success, durationMs ->
            isModelLoaded = success
            loadingMessage = if (success) "Ready! (${durationMs / 1000.0}s)" else "Failed to load model"
        }
    }

    fun solve(question: String) {
        if (!engine.isLoaded) return

        // Stop any in-flight generation and reset context
        if (isGenerating) {
            engine.stop()
        }
        engine.resetContext()

        currentQuestion = question
        steps.clear()
        finalAnswer = null
        responseAccumulator.clear()
        currentStepText.clear()
        isInThinking = false
        sawHashMarker = false
        hashTokenCount = 0
        isGenerating = true

        val prompt = PromptFormatter.formatMathPrompt(question)
        engine.generate(prompt, 768, this)
    }

    fun explainMore() {
        if (!engine.isLoaded || isGenerating) return

        steps.clear()
        finalAnswer = null
        responseAccumulator.clear()
        currentStepText.clear()
        isInThinking = false
        sawHashMarker = false
        hashTokenCount = 0
        isGenerating = true

        val prompt = PromptFormatter.formatFollowUp(
            "Can you explain that more simply, step by step?"
        )
        engine.generate(prompt, 768, this)
    }

    fun stopGeneration() {
        engine.stop()
        isGenerating = false
        flushCurrentStep()
    }

    private var sawHashMarker = false
    private var hashTokenCount = 0

    override fun onResult(result: String) {
        mainHandler.post { handleResult(result) }
    }

    override fun onStats(stats: String) {
        mainHandler.post { handleStats(stats) }
    }

    private fun handleResult(result: String) {
        // If we already have the answer, ignore remaining tokens
        if (finalAnswer != null) return

        responseAccumulator.append(result)

        // Skip think tags but show thinking content as steps
        if (result == "<think>" || result == "</think>") return
        if (result == "<|im_end|>") {
            isGenerating = false
            flushFinalResult()
            return
        }

        // Keep accumulating — show everything in the reasoning card
        currentStepText.append(result)

        // Update the single reasoning card live
        val cleaned = cleanLatex(currentStepText.toString().trim())
        if (cleaned.length >= 3) {
            if (steps.isEmpty() || steps[0].isAnswer) {
                steps.add(0, StepState(text = cleaned))
            } else {
                steps[0] = StepState(text = cleaned)
            }
        }
    }

    private fun handleStats(stats: String) {
        isGenerating = false
        flushFinalResult()
    }

    private fun flushFinalResult() {
        val fullText = responseAccumulator.toString()

        // Extract answer from #### or \boxed{}
        var answer: String? = null

        val hashMatch = Regex("####\\s*([\\d.,/\\-]+)").find(fullText)
        if (hashMatch != null) {
            answer = hashMatch.groupValues[1].trim()
        }

        if (answer == null) {
            val boxedIdx = fullText.indexOf("\\boxed{")
            if (boxedIdx >= 0) {
                val start = boxedIdx + 7
                var depth = 1
                var end = start
                while (end < fullText.length && depth > 0) {
                    if (fullText[end] == '{') depth++
                    if (fullText[end] == '}') depth--
                    end++
                }
                if (depth == 0) {
                    answer = cleanLatex(fullText.substring(start, end - 1))
                }
            }
        }

        // Clean the reasoning card — remove answer markers
        val reasoningRaw = currentStepText.toString()
            .replace(Regex("####.*"), "")
            .replace(Regex("\\\\boxed\\{.*?\\}"), "")
            .trim()
        val reasoningText = cleanLatex(reasoningRaw)
        if (reasoningText.length >= 3) {
            if (steps.isEmpty()) {
                steps.add(StepState(text = reasoningText))
            } else if (!steps[0].isAnswer) {
                steps[0] = StepState(text = reasoningText)
            }
        }

        // Add answer card
        if (!answer.isNullOrEmpty()) {
            finalAnswer = answer
            steps.add(StepState(text = "Answer: $finalAnswer", isAnswer = true))
        } else {
            // Fallback: find the last number/fraction in the response
            val lastNumber = Regex("[\\d]+[/\\d.]*").findAll(reasoningRaw).lastOrNull()?.value
            if (lastNumber != null) {
                finalAnswer = lastNumber
                steps.add(StepState(text = "Answer: $finalAnswer", isAnswer = true))
            }
        }

        // Fallback — if no steps at all, show raw response
        if (steps.isEmpty() && fullText.isNotEmpty()) {
            val cleaned = cleanLatex(fullText.replace("<think>", "").replace("</think>", "").replace("<|im_end|>", ""))
            if (cleaned.isNotEmpty()) {
                steps.add(StepState(text = cleaned))
            }
        }
    }

    private fun cleanLatex(text: String): String {
        return text
            .replace("\\[", "").replace("\\]", "")
            .replace("\\(", "").replace("\\)", "")
            .replace(Regex("\\\\boxed\\{([^}]*)\\}"), "→ $1")
            .replace(Regex("\\\\frac\\{([^}]*)\\}\\{([^}]*)\\}"), "$1/$2")
            .replace("\\times", "×").replace("\\div", "÷")
            .replace("\\cdot", "·").replace("\\pi", "π")
            .replace("\\pm", "±").replace("\\leq", "≤")
            .replace("\\geq", "≥").replace("\\neq", "≠")
            .replace("\\approx", "≈").replace("\\infty", "∞")
            .replace("\\quad", " ").replace("\\,", " ")
            .replace("\\text{", "").replace("\\left", "")
            .replace("\\right", "").replace("\\sqrt{", "√(")
            .replace("$$", "").replace("$", "")
            .replace(Regex("\\\\[a-zA-Z]+"), "") // strip remaining \commands
            .replace("{", "(").replace("}", ")")  // braces to parens
            .replace(Regex("\\s{2,}"), " ")       // collapse whitespace
            .trim()
    }

    private fun flushCurrentStep() {
        val raw = currentStepText.toString().trim()
        if (raw.isEmpty()) {
            currentStepText.clear()
            return
        }
        val text = cleanLatex(raw)
        if (text.length < 3) {
            currentStepText.clear()
            return
        }
        steps.add(StepState(text = text))
        currentStepText.clear()
    }

    private fun updateLiveStep() {
        val text = currentStepText.toString().trim()
        if (text.isEmpty()) return

        val lastIdx = steps.indexOfLast { !it.isAnswer }
        if (lastIdx >= 0) {
            steps[lastIdx] = StepState(text = text)
        } else {
            steps.add(StepState(text = text))
        }
    }

    private fun extractFinalAnswer() {
        if (finalAnswer != null) return
        val extracted = AnswerValidator.extractFinalAnswer(responseAccumulator.toString())
        if (extracted != null) {
            finalAnswer = extracted.toString()
            if (steps.none { it.isAnswer }) {
                steps.add(StepState(text = "Answer: $finalAnswer", isAnswer = true))
            }
        }
    }
}
