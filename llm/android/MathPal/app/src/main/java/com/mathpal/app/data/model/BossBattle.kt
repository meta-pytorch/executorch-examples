package com.mathpal.app.data.model

enum class BossDifficulty {
    NORMAL,
    HARD,
    NIGHTMARE
}

data class BossBattle(
    val id: String,
    val name: String,
    val topic: ProblemCategory,
    val bossHp: Int,
    val playerShields: Int,
    val currentRound: Int,
    val totalRounds: Int,
    val difficulty: BossDifficulty,
    val isDefeated: Boolean = false
)
