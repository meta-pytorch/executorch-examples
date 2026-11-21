package com.mathpal.app.gamification

data class UserStats(
    val speedBonusProblems: Int = 0,
    val firstAttemptCorrect: Int = 0,
    val bossesDefeated: Int = 0,
    val comebackCorrect: Int = 0,
    val nightSessions: Int = 0,
    val earlySessions: Int = 0,
    val masteredTopics: Int = 0,
    val totalTopics: Int = 10,
    val maxWrongStreak: Int = 0,
    val sessionsFinishedAfterWrongStreak: Int = 0,
    val categoriesAttempted: Int = 0,
    val totalCategories: Int = 10,
    val badgesEarned: Int = 0,
    val totalBadges: Int = 30
)

data class ProblemResult(
    val wasCorrect: Boolean,
    val firstAttempt: Boolean,
    val timeMs: Long,
    val expectedTimeMs: Long,
    val hintsUsed: Int,
    val category: String
)

enum class BadgeTier { BRONZE, SILVER, GOLD }

data class BadgeDefinition(
    val id: String,
    val name: String,
    val description: String,
    val tier: BadgeTier,
    val checkCondition: (UserStats) -> Boolean
)

object BadgeManager {

    val ALL_BADGES: List<BadgeDefinition> = listOf(
        // Speed Demon
        BadgeDefinition("speed_demon_bronze", "Speed Demon (Bronze)", "Solve 10 problems with speed bonus", BadgeTier.BRONZE) { it.speedBonusProblems >= 10 },
        BadgeDefinition("speed_demon_silver", "Speed Demon (Silver)", "Solve 50 problems with speed bonus", BadgeTier.SILVER) { it.speedBonusProblems >= 50 },
        BadgeDefinition("speed_demon_gold", "Speed Demon (Gold)", "Solve 200 problems with speed bonus", BadgeTier.GOLD) { it.speedBonusProblems >= 200 },

        // Perfectionist
        BadgeDefinition("perfectionist_bronze", "Perfectionist (Bronze)", "Get 10 correct on first attempt", BadgeTier.BRONZE) { it.firstAttemptCorrect >= 10 },
        BadgeDefinition("perfectionist_silver", "Perfectionist (Silver)", "Get 50 correct on first attempt", BadgeTier.SILVER) { it.firstAttemptCorrect >= 50 },
        BadgeDefinition("perfectionist_gold", "Perfectionist (Gold)", "Get 200 correct on first attempt", BadgeTier.GOLD) { it.firstAttemptCorrect >= 200 },

        // Boss Slayer
        BadgeDefinition("boss_slayer_bronze", "Boss Slayer (Bronze)", "Defeat 3 bosses", BadgeTier.BRONZE) { it.bossesDefeated >= 3 },
        BadgeDefinition("boss_slayer_silver", "Boss Slayer (Silver)", "Defeat 10 bosses", BadgeTier.SILVER) { it.bossesDefeated >= 10 },
        BadgeDefinition("boss_slayer_gold", "Boss Slayer (Gold)", "Defeat 25 bosses", BadgeTier.GOLD) { it.bossesDefeated >= 25 },

        // Comeback Kid
        BadgeDefinition("comeback_kid_bronze", "Comeback Kid (Bronze)", "Retry after wrong and get correct 5 times", BadgeTier.BRONZE) { it.comebackCorrect >= 5 },
        BadgeDefinition("comeback_kid_silver", "Comeback Kid (Silver)", "Retry after wrong and get correct 20 times", BadgeTier.SILVER) { it.comebackCorrect >= 20 },
        BadgeDefinition("comeback_kid_gold", "Comeback Kid (Gold)", "Retry after wrong and get correct 100 times", BadgeTier.GOLD) { it.comebackCorrect >= 100 },

        // Night Owl
        BadgeDefinition("night_owl_bronze", "Night Owl (Bronze)", "5 sessions between 8PM-midnight", BadgeTier.BRONZE) { it.nightSessions >= 5 },
        BadgeDefinition("night_owl_silver", "Night Owl (Silver)", "20 sessions between 8PM-midnight", BadgeTier.SILVER) { it.nightSessions >= 20 },
        BadgeDefinition("night_owl_gold", "Night Owl (Gold)", "50 sessions between 8PM-midnight", BadgeTier.GOLD) { it.nightSessions >= 50 },

        // Early Bird
        BadgeDefinition("early_bird_bronze", "Early Bird (Bronze)", "5 sessions before 8AM", BadgeTier.BRONZE) { it.earlySessions >= 5 },
        BadgeDefinition("early_bird_silver", "Early Bird (Silver)", "20 sessions before 8AM", BadgeTier.SILVER) { it.earlySessions >= 20 },
        BadgeDefinition("early_bird_gold", "Early Bird (Gold)", "50 sessions before 8AM", BadgeTier.GOLD) { it.earlySessions >= 50 },

        // Topic Titan
        BadgeDefinition("topic_titan_bronze", "Topic Titan (Bronze)", "Reach mastery in 3 topics", BadgeTier.BRONZE) { it.masteredTopics >= 3 },
        BadgeDefinition("topic_titan_silver", "Topic Titan (Silver)", "Reach mastery in 8 topics", BadgeTier.SILVER) { it.masteredTopics >= 8 },
        BadgeDefinition("topic_titan_gold", "Topic Titan (Gold)", "Reach mastery in all topics", BadgeTier.GOLD) { it.masteredTopics >= it.totalTopics },

        // Iron Will
        BadgeDefinition("iron_will_bronze", "Iron Will (Bronze)", "Get 3 wrong in a row and still finish session", BadgeTier.BRONZE) { it.sessionsFinishedAfterWrongStreak >= 3 },
        BadgeDefinition("iron_will_silver", "Iron Will (Silver)", "Get 5 wrong in a row and still finish session", BadgeTier.SILVER) { it.sessionsFinishedAfterWrongStreak >= 5 },
        BadgeDefinition("iron_will_gold", "Iron Will (Gold)", "Get 10 wrong in a row and still finish session", BadgeTier.GOLD) { it.sessionsFinishedAfterWrongStreak >= 10 },

        // Explorer
        BadgeDefinition("explorer_bronze", "Explorer (Bronze)", "Attempt problems in 5 categories", BadgeTier.BRONZE) { it.categoriesAttempted >= 5 },
        BadgeDefinition("explorer_silver", "Explorer (Silver)", "Attempt problems in 10 categories", BadgeTier.SILVER) { it.categoriesAttempted >= 10 },
        BadgeDefinition("explorer_gold", "Explorer (Gold)", "Attempt problems in all categories", BadgeTier.GOLD) { it.categoriesAttempted >= it.totalCategories },

        // The Collector
        BadgeDefinition("collector_bronze", "The Collector (Bronze)", "Earn 10 badges at any tier", BadgeTier.BRONZE) { it.badgesEarned >= 10 },
        BadgeDefinition("collector_silver", "The Collector (Silver)", "Earn 25 badges at any tier", BadgeTier.SILVER) { it.badgesEarned >= 25 },
        BadgeDefinition("collector_gold", "The Collector (Gold)", "Earn all badges", BadgeTier.GOLD) { it.badgesEarned >= it.totalBadges }
    )

    fun checkBadges(stats: UserStats, results: List<ProblemResult>): List<BadgeDefinition> {
        return ALL_BADGES.filter { badge ->
            badge.checkCondition(stats)
        }
    }
}
