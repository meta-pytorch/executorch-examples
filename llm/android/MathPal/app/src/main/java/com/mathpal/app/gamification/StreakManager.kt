package com.mathpal.app.gamification

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class StreakMilestone(val days: Int) {
    DAY_3(3),
    WEEK(7),
    TWO_WEEKS(14),
    MONTH(30),
    TWO_MONTHS(60),
    HUNDRED_DAYS(100),
    HALF_YEAR(182),
    FULL_YEAR(365)
}

data class StreakResult(
    val newStreak: Int,
    val streakBroken: Boolean,
    val isGraceDay: Boolean,
    val milestone: StreakMilestone?
)

class StreakManager(
    private val getLastActiveDate: () -> String?,
    private val setLastActiveDate: (String) -> Unit
) {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    private var graceDaysUsedInWindow = 0
    private var windowStartDate: LocalDate? = null

    fun checkAndUpdateStreak(currentDate: String, currentStreak: Int): StreakResult {
        val today = LocalDate.parse(currentDate, formatter)
        val lastDateStr = getLastActiveDate()

        if (lastDateStr == null) {
            setLastActiveDate(currentDate)
            windowStartDate = today
            graceDaysUsedInWindow = 0
            val milestone = checkMilestone(1)
            return StreakResult(newStreak = 1, streakBroken = false, isGraceDay = false, milestone = milestone)
        }

        val lastDate = LocalDate.parse(lastDateStr, formatter)
        val daysBetween = ChronoUnit.DAYS.between(lastDate, today).toInt()

        if (daysBetween == 0) {
            return StreakResult(
                newStreak = currentStreak,
                streakBroken = false,
                isGraceDay = false,
                milestone = null
            )
        }

        if (daysBetween == 1) {
            setLastActiveDate(currentDate)
            refreshGraceWindow(today)
            val newStreak = currentStreak + 1
            return StreakResult(
                newStreak = newStreak,
                streakBroken = false,
                isGraceDay = false,
                milestone = checkMilestone(newStreak)
            )
        }

        if (daysBetween == 2) {
            refreshGraceWindow(today)
            if (graceDaysUsedInWindow < 1) {
                graceDaysUsedInWindow++
                setLastActiveDate(currentDate)
                val newStreak = currentStreak + 1
                return StreakResult(
                    newStreak = newStreak,
                    streakBroken = false,
                    isGraceDay = true,
                    milestone = checkMilestone(newStreak)
                )
            }
        }

        setLastActiveDate(currentDate)
        windowStartDate = today
        graceDaysUsedInWindow = 0
        return StreakResult(
            newStreak = 1,
            streakBroken = true,
            isGraceDay = false,
            milestone = null
        )
    }

    private fun refreshGraceWindow(today: LocalDate) {
        val start = windowStartDate
        if (start == null || ChronoUnit.DAYS.between(start, today) >= 14) {
            windowStartDate = today
            graceDaysUsedInWindow = 0
        }
    }

    private fun checkMilestone(streak: Int): StreakMilestone? {
        return StreakMilestone.entries.find { it.days == streak }
    }
}
