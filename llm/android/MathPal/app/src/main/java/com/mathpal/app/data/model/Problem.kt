package com.mathpal.app.data.model

enum class ProblemCategory {
    ARITHMETIC,
    FRACTIONS,
    DECIMALS_PERCENT,
    RATIOS,
    LINEAR_EQUATIONS,
    GEOMETRY,
    RATE_SPEED,
    PROBABILITY,
    UNIT_CONVERSION,
    MULTI_STEP
}

enum class Difficulty {
    FOUNDATION,
    INTERMEDIATE,
    CHALLENGE
}

enum class GradeLevel(val label: String) {
    GRADE_4("Grade 4"),
    GRADE_5("Grade 5"),
    GRADE_6("Grade 6"),
    GRADE_7("Grade 7"),
    GRADE_8("Grade 8"),
}

data class Problem(
    val id: String,
    val category: ProblemCategory,
    val difficulty: Difficulty,
    val question: String,
    val expectedAnswer: Double? = null,
    val hint: String? = null,
    val gradeLevel: GradeLevel = GradeLevel.GRADE_8,
)
