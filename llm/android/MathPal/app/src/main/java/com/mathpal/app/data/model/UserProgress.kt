package com.mathpal.app.data.model

data class UserStats(
    val totalXP: Int = 0,
    val level: Int = 1,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastActiveDate: String = "",
    val totalProblemsSolved: Int = 0,
    val totalCorrect: Int = 0
)

data class ProblemResult(
    val problemId: String,
    val timestamp: Long,
    val wasCorrect: Boolean,
    val timeSpentMs: Long,
    val hintsUsed: Int,
    val xpEarned: Int
)

data class DailyProgress(
    val date: String,
    val problemsCompleted: Int = 0,
    val problemsCorrect: Int = 0,
    val timeSpentMs: Long = 0L
)

data class TopicMastery(
    val category: ProblemCategory,
    val attempted: Int = 0,
    val correct: Int = 0,
    val masteryPercent: Float = 0f
)
