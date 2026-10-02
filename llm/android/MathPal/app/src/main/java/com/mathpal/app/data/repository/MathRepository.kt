package com.mathpal.app.data.repository

import android.content.Context
import com.mathpal.app.data.db.MathPalDatabase
import com.mathpal.app.data.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MathRepository(context: Context) {

    private val db = MathPalDatabase(context.applicationContext)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun recordAnswer(
        problem: Problem,
        wasCorrect: Boolean,
        timeMs: Long,
        hints: Int
    ): Int {
        val xpEarned = calculateXP(problem.difficulty, wasCorrect, hints)
        val now = System.currentTimeMillis()
        val today = dateFormat.format(Date(now))

        val result = ProblemResult(
            problemId = problem.id,
            timestamp = now,
            wasCorrect = wasCorrect,
            timeSpentMs = timeMs,
            hintsUsed = hints,
            xpEarned = xpEarned
        )
        db.insertProblemResult(result)

        val stats = db.getUserStats()
        val streakInfo = computeStreak(stats, today)
        db.updateUserStats(stats.copy(
            totalXP = stats.totalXP + xpEarned,
            level = levelForXP(stats.totalXP + xpEarned),
            currentStreak = streakInfo.first,
            longestStreak = streakInfo.second,
            lastActiveDate = today,
            totalProblemsSolved = stats.totalProblemsSolved + 1,
            totalCorrect = stats.totalCorrect + if (wasCorrect) 1 else 0
        ))

        val daily = db.getDailyProgress(today)
        db.upsertDailyProgress(daily.copy(
            problemsCompleted = daily.problemsCompleted + 1,
            problemsCorrect = daily.problemsCorrect + if (wasCorrect) 1 else 0,
            timeSpentMs = daily.timeSpentMs + timeMs
        ))

        val masteryList = db.getTopicMastery()
        val existing = masteryList.find { it.category == problem.category }
        val updated = if (existing != null) {
            val newAttempted = existing.attempted + 1
            val newCorrect = existing.correct + if (wasCorrect) 1 else 0
            existing.copy(
                attempted = newAttempted,
                correct = newCorrect,
                masteryPercent = (newCorrect.toFloat() / newAttempted) * 100f
            )
        } else {
            TopicMastery(
                category = problem.category,
                attempted = 1,
                correct = if (wasCorrect) 1 else 0,
                masteryPercent = if (wasCorrect) 100f else 0f
            )
        }
        db.upsertTopicMastery(updated)

        return xpEarned
    }

    fun getStats(): UserStats = db.getUserStats()

    fun getDailyProgress(): DailyProgress {
        val today = dateFormat.format(Date())
        return db.getDailyProgress(today)
    }

    fun getTopicMasteryAll(): List<TopicMastery> = db.getTopicMastery()

    fun getWeakTopics(): List<ProblemCategory> {
        val mastery = db.getTopicMastery()
        val weakThreshold = 60f
        val minAttempts = 3
        return mastery
            .filter { it.attempted >= minAttempts && it.masteryPercent < weakThreshold }
            .sortedBy { it.masteryPercent }
            .map { it.category }
    }

    private fun calculateXP(difficulty: Difficulty, correct: Boolean, hints: Int): Int {
        if (!correct) return 1
        val base = when (difficulty) {
            Difficulty.FOUNDATION -> 10
            Difficulty.INTERMEDIATE -> 20
            Difficulty.CHALLENGE -> 35
        }
        val hintPenalty = (hints * 3).coerceAtMost(base - 1)
        return base - hintPenalty
    }

    private fun levelForXP(xp: Int): Int = (xp / 100) + 1

    private fun computeStreak(stats: UserStats, today: String): Pair<Int, Int> {
        if (stats.lastActiveDate == today) {
            return Pair(stats.currentStreak, stats.longestStreak)
        }
        val yesterday = dateFormat.format(Date(System.currentTimeMillis() - 86_400_000L))
        val newStreak = if (stats.lastActiveDate == yesterday) {
            stats.currentStreak + 1
        } else {
            1
        }
        val newLongest = maxOf(stats.longestStreak, newStreak)
        return Pair(newStreak, newLongest)
    }
}
