package com.mathpal.app.data.model

enum class BadgeTier {
    BRONZE,
    SILVER,
    GOLD
}

data class Badge(
    val id: String,
    val name: String,
    val description: String,
    val iconRes: Int,
    val tier: BadgeTier,
    val isUnlocked: Boolean = false,
    val unlockedDate: String? = null
)
