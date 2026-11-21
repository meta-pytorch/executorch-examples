package com.mathpal.app.inference

import kotlin.math.abs

object AnswerValidator {

    private val FINAL_ANSWER_PATTERN = Regex("""####\s*([+-]?[\d,]*\.?\d+)""")
    private val BOXED_ANSWER_PATTERN = Regex("""\\boxed\{([^}]+)\}""")
    private val STEP_PATTERN = Regex("""(?:Step\s+\d+[.:]\s|^\d+[.)]\s)""", RegexOption.MULTILINE)
    private val ARITHMETIC_PATTERN = Regex(
        """([+-]?[\d,]*\.?\d+)\s*([+\-*/×÷])\s*([+-]?[\d,]*\.?\d+)\s*=\s*([+-]?[\d,]*\.?\d+)"""
    )

    enum class ValidationResult {
        PLAUSIBLE,
        MISMATCH,
        NO_ANSWER_FOUND
    }

    fun extractFinalAnswer(modelOutput: String): Double? {
        val match = FINAL_ANSWER_PATTERN.find(modelOutput)
            ?: BOXED_ANSWER_PATTERN.find(modelOutput)
            ?: return null
        val raw = match.groupValues[1].replace(",", "")
        return raw.toDoubleOrNull()
    }

    fun isCorrect(modelOutput: String, expected: Double, tolerance: Double = 0.01): Boolean {
        val answer = extractFinalAnswer(modelOutput) ?: return false
        return abs(answer - expected) <= tolerance
    }

    fun sanityCheck(modelOutput: String): ValidationResult {
        val finalAnswer = extractFinalAnswer(modelOutput)
            ?: return ValidationResult.NO_ANSWER_FOUND

        val matches = ARITHMETIC_PATTERN.findAll(modelOutput).toList()
        if (matches.isEmpty()) return ValidationResult.PLAUSIBLE

        for (match in matches) {
            val left = parseNumber(match.groupValues[1]) ?: continue
            val op = normalizeOperator(match.groupValues[2])
            val right = parseNumber(match.groupValues[3]) ?: continue
            val stated = parseNumber(match.groupValues[4]) ?: continue

            val computed = when (op) {
                "+" -> left + right
                "-" -> left - right
                "*" -> left * right
                "/" -> if (right != 0.0) left / right else continue
                else -> continue
            }
            if (abs(computed - stated) > 0.01) {
                return ValidationResult.MISMATCH
            }
        }

        return ValidationResult.PLAUSIBLE
    }

    fun extractSteps(modelOutput: String): List<String> {
        val textBeforeAnswer = modelOutput.substringBefore("####").trim()
        val parts = STEP_PATTERN.split(textBeforeAnswer)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.size > 1) return parts
        val numbered = textBeforeAnswer.split(Regex("""\n(?=\d+[).:])\s*"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return numbered
    }

    private fun parseNumber(raw: String): Double? =
        raw.replace(",", "").toDoubleOrNull()

    private fun normalizeOperator(op: String): String = when (op) {
        "×" -> "*"
        "÷" -> "/"
        else -> op
    }
}
