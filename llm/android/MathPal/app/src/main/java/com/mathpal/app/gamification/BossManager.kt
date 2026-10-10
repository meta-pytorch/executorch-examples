package com.mathpal.app.gamification

import com.mathpal.app.data.model.ProblemCategory

data class BossConfig(
    val id: String,
    val name: String,
    val topic: ProblemCategory,
    val totalHp: Int,
    val rounds: Int,
    val description: String
)

data class BattleAction(
    val damage: Int,
    val isCritical: Boolean,
    val description: String
)

object BossManager {

    val ALL_BOSSES: List<BossConfig> = listOf(
        BossConfig(
            id = "boss_baron_bracket",
            name = "Baron Von Bracket",
            topic = ProblemCategory.ARITHMETIC,
            totalHp = 300,
            rounds = 5,
            description = "A ruthless noble who hides behind walls of parentheses."
        ),
        BossConfig(
            id = "boss_slope_serpent",
            name = "The Slope Serpent",
            topic = ProblemCategory.LINEAR_EQUATIONS,
            totalHp = 400,
            rounds = 6,
            description = "A slithering beast that rises and runs across the coordinate plane."
        ),
        BossConfig(
            id = "boss_radical_rex",
            name = "Radical Rex",
            topic = ProblemCategory.ARITHMETIC,
            totalHp = 350,
            rounds = 5,
            description = "A dinosaur powered by square roots and exponents."
        ),
        BossConfig(
            id = "boss_queen_quadratic",
            name = "Queen Quadratic",
            topic = ProblemCategory.LINEAR_EQUATIONS,
            totalHp = 500,
            rounds = 7,
            description = "She rules with two solutions and a discriminant of terror."
        ),
        BossConfig(
            id = "boss_fraction_phantom",
            name = "The Fraction Phantom",
            topic = ProblemCategory.FRACTIONS,
            totalHp = 350,
            rounds = 6,
            description = "A ghostly figure that splits everything into pieces."
        ),
        BossConfig(
            id = "boss_general_geo",
            name = "General Geo",
            topic = ProblemCategory.GEOMETRY,
            totalHp = 400,
            rounds = 6,
            description = "A geometric warlord commanding an army of shapes."
        ),
        BossConfig(
            id = "boss_professor_probability",
            name = "Professor Probability",
            topic = ProblemCategory.PROBABILITY,
            totalHp = 450,
            rounds = 7,
            description = "He calculates the odds of your defeat — they are not in your favor."
        ),
        BossConfig(
            id = "boss_the_system",
            name = "The System",
            topic = ProblemCategory.LINEAR_EQUATIONS,
            totalHp = 500,
            rounds = 8,
            description = "The final boss: a system of equations that must be solved simultaneously."
        )
    )

    fun calculateDamage(
        wasCorrect: Boolean,
        timeMs: Long,
        expectedTimeMs: Long,
        hintsUsed: Int
    ): BattleAction {
        if (!wasCorrect) {
            return BattleAction(
                damage = 0,
                isCritical = false,
                description = "Wrong answer! You lose a shield."
            )
        }

        val baseDamage = (50..100).random()
        val isCritical = timeMs < expectedTimeMs / 2
        var multiplier = 1.0

        if (isCritical) multiplier *= 1.5
        if (hintsUsed > 0) multiplier *= 0.5

        val finalDamage = (baseDamage * multiplier).toInt()

        val desc = buildString {
            append("You deal $finalDamage damage")
            if (isCritical) append(" — CRITICAL HIT!")
            if (hintsUsed > 0) append(" (reduced by hints)")
            append(".")
        }

        return BattleAction(
            damage = finalDamage,
            isCritical = isCritical,
            description = desc
        )
    }
}
