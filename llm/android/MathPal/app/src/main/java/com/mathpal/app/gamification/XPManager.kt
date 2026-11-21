package com.mathpal.app.gamification

import kotlin.math.max
import kotlin.math.roundToInt

object XPManager {

    private const val MAX_LEVEL = 50
    private const val BASE_XP = 100
    private const val GROWTH_RATE = 1.20

    private val levelThresholds: List<Int> = buildList {
        add(0) // Level 1
        var cumulative = 0
        var requirement = BASE_XP.toDouble()
        for (i in 2..MAX_LEVEL) {
            cumulative += requirement.roundToInt()
            add(cumulative)
            requirement *= GROWTH_RATE
        }
    }

    fun calculateXP(
        wasCorrect: Boolean,
        firstAttempt: Boolean,
        timeMs: Long,
        expectedTimeMs: Long,
        hintsUsed: Int,
        currentCombo: Int
    ): Int {
        if (!wasCorrect) return 0

        var xp = 10

        if (firstAttempt) xp += 5

        if (timeMs < expectedTimeMs / 2) xp += 5

        if (currentCombo > 0 && currentCombo % 3 == 0) xp += 5

        xp -= 2 * hintsUsed

        return max(0, xp)
    }

    fun getLevelForXP(totalXP: Int): Int {
        for (level in MAX_LEVEL downTo 1) {
            if (totalXP >= levelThresholds[level - 1]) return level
        }
        return 1
    }

    fun getLevelTitle(level: Int): String = when (level) {
        in 1..5 -> "Number Newbie"
        in 6..10 -> "Algebra Adept"
        in 11..15 -> "Math Mercenary"
        in 16..20 -> "Theorem Titan"
        in 21..25 -> "Calculus Conjurer"
        in 26..30 -> "Infinity Invoker"
        in 31..35 -> "Prime Paladin"
        in 36..40 -> "Axiom Archon"
        in 41..45 -> "Euler's Heir"
        in 46..50 -> "Gauss Guardian"
        else -> "Number Newbie"
    }

    fun getTierForLevel(level: Int): String = when (level) {
        in 1..5 -> "Apprentice"
        in 6..10 -> "Scholar"
        in 11..20 -> "Warrior"
        in 21..30 -> "Mage"
        in 31..40 -> "Legend"
        in 41..50 -> "Mythic"
        else -> "Apprentice"
    }

    fun getXPForNextLevel(currentXP: Int): Int {
        val currentLevel = getLevelForXP(currentXP)
        if (currentLevel >= MAX_LEVEL) return 0
        return levelThresholds[currentLevel] - currentXP
    }
}
