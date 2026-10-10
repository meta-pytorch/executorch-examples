package com.mathpal.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.mathpal.app.data.model.*

class MathPalDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "mathpal.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_USER_STATS = "user_stats"
        private const val TABLE_PROBLEM_RESULTS = "problem_results"
        private const val TABLE_DAILY_PROGRESS = "daily_progress"
        private const val TABLE_TOPIC_MASTERY = "topic_mastery"
        private const val TABLE_BADGES = "badges"
        private const val TABLE_BOSS_BATTLES = "boss_battles"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_USER_STATS (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                total_xp INTEGER NOT NULL DEFAULT 0,
                level INTEGER NOT NULL DEFAULT 1,
                current_streak INTEGER NOT NULL DEFAULT 0,
                longest_streak INTEGER NOT NULL DEFAULT 0,
                last_active_date TEXT NOT NULL DEFAULT '',
                total_problems_solved INTEGER NOT NULL DEFAULT 0,
                total_correct INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("""
            INSERT INTO $TABLE_USER_STATS (id) VALUES (1)
        """)

        db.execSQL("""
            CREATE TABLE $TABLE_PROBLEM_RESULTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                problem_id TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                was_correct INTEGER NOT NULL,
                time_spent_ms INTEGER NOT NULL,
                hints_used INTEGER NOT NULL,
                xp_earned INTEGER NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE $TABLE_DAILY_PROGRESS (
                date TEXT PRIMARY KEY,
                problems_completed INTEGER NOT NULL DEFAULT 0,
                problems_correct INTEGER NOT NULL DEFAULT 0,
                time_spent_ms INTEGER NOT NULL DEFAULT 0
            )
        """)

        db.execSQL("""
            CREATE TABLE $TABLE_TOPIC_MASTERY (
                category TEXT PRIMARY KEY,
                attempted INTEGER NOT NULL DEFAULT 0,
                correct INTEGER NOT NULL DEFAULT 0,
                mastery_percent REAL NOT NULL DEFAULT 0.0
            )
        """)

        db.execSQL("""
            CREATE TABLE $TABLE_BADGES (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                icon_res INTEGER NOT NULL DEFAULT 0,
                tier TEXT NOT NULL,
                is_unlocked INTEGER NOT NULL DEFAULT 0,
                unlocked_date TEXT
            )
        """)

        db.execSQL("""
            CREATE TABLE $TABLE_BOSS_BATTLES (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                topic TEXT NOT NULL,
                boss_hp INTEGER NOT NULL,
                player_shields INTEGER NOT NULL,
                current_round INTEGER NOT NULL DEFAULT 1,
                total_rounds INTEGER NOT NULL,
                difficulty TEXT NOT NULL,
                is_defeated INTEGER NOT NULL DEFAULT 0
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USER_STATS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PROBLEM_RESULTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DAILY_PROGRESS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TOPIC_MASTERY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BADGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BOSS_BATTLES")
        onCreate(db)
    }

    fun insertProblemResult(result: ProblemResult) {
        val values = ContentValues().apply {
            put("problem_id", result.problemId)
            put("timestamp", result.timestamp)
            put("was_correct", if (result.wasCorrect) 1 else 0)
            put("time_spent_ms", result.timeSpentMs)
            put("hints_used", result.hintsUsed)
            put("xp_earned", result.xpEarned)
        }
        writableDatabase.insert(TABLE_PROBLEM_RESULTS, null, values)
    }

    fun getUserStats(): UserStats {
        val cursor = readableDatabase.rawQuery("SELECT * FROM $TABLE_USER_STATS WHERE id = 1", null)
        return cursor.use { c ->
            if (c.moveToFirst()) {
                UserStats(
                    totalXP = c.getInt(c.getColumnIndexOrThrow("total_xp")),
                    level = c.getInt(c.getColumnIndexOrThrow("level")),
                    currentStreak = c.getInt(c.getColumnIndexOrThrow("current_streak")),
                    longestStreak = c.getInt(c.getColumnIndexOrThrow("longest_streak")),
                    lastActiveDate = c.getString(c.getColumnIndexOrThrow("last_active_date")),
                    totalProblemsSolved = c.getInt(c.getColumnIndexOrThrow("total_problems_solved")),
                    totalCorrect = c.getInt(c.getColumnIndexOrThrow("total_correct"))
                )
            } else {
                UserStats()
            }
        }
    }

    fun updateUserStats(stats: UserStats) {
        val values = ContentValues().apply {
            put("total_xp", stats.totalXP)
            put("level", stats.level)
            put("current_streak", stats.currentStreak)
            put("longest_streak", stats.longestStreak)
            put("last_active_date", stats.lastActiveDate)
            put("total_problems_solved", stats.totalProblemsSolved)
            put("total_correct", stats.totalCorrect)
        }
        writableDatabase.update(TABLE_USER_STATS, values, "id = 1", null)
    }

    fun getDailyProgress(date: String): DailyProgress {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_DAILY_PROGRESS WHERE date = ?",
            arrayOf(date)
        )
        return cursor.use { c ->
            if (c.moveToFirst()) {
                DailyProgress(
                    date = c.getString(c.getColumnIndexOrThrow("date")),
                    problemsCompleted = c.getInt(c.getColumnIndexOrThrow("problems_completed")),
                    problemsCorrect = c.getInt(c.getColumnIndexOrThrow("problems_correct")),
                    timeSpentMs = c.getLong(c.getColumnIndexOrThrow("time_spent_ms"))
                )
            } else {
                DailyProgress(date = date)
            }
        }
    }

    fun upsertDailyProgress(progress: DailyProgress) {
        val values = ContentValues().apply {
            put("date", progress.date)
            put("problems_completed", progress.problemsCompleted)
            put("problems_correct", progress.problemsCorrect)
            put("time_spent_ms", progress.timeSpentMs)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_DAILY_PROGRESS, null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getTopicMastery(): List<TopicMastery> {
        val cursor = readableDatabase.rawQuery("SELECT * FROM $TABLE_TOPIC_MASTERY", null)
        return cursor.use { c ->
            val results = mutableListOf<TopicMastery>()
            while (c.moveToNext()) {
                results.add(
                    TopicMastery(
                        category = ProblemCategory.valueOf(
                            c.getString(c.getColumnIndexOrThrow("category"))
                        ),
                        attempted = c.getInt(c.getColumnIndexOrThrow("attempted")),
                        correct = c.getInt(c.getColumnIndexOrThrow("correct")),
                        masteryPercent = c.getFloat(c.getColumnIndexOrThrow("mastery_percent"))
                    )
                )
            }
            results
        }
    }

    fun upsertTopicMastery(mastery: TopicMastery) {
        val values = ContentValues().apply {
            put("category", mastery.category.name)
            put("attempted", mastery.attempted)
            put("correct", mastery.correct)
            put("mastery_percent", mastery.masteryPercent)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_TOPIC_MASTERY, null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getBadges(): List<Badge> {
        val cursor = readableDatabase.rawQuery("SELECT * FROM $TABLE_BADGES", null)
        return cursor.use { c ->
            val results = mutableListOf<Badge>()
            while (c.moveToNext()) {
                results.add(
                    Badge(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        description = c.getString(c.getColumnIndexOrThrow("description")),
                        iconRes = c.getInt(c.getColumnIndexOrThrow("icon_res")),
                        tier = BadgeTier.valueOf(c.getString(c.getColumnIndexOrThrow("tier"))),
                        isUnlocked = c.getInt(c.getColumnIndexOrThrow("is_unlocked")) == 1,
                        unlockedDate = c.getString(c.getColumnIndexOrThrow("unlocked_date"))
                    )
                )
            }
            results
        }
    }

    fun getBossBattle(id: String): BossBattle? {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_BOSS_BATTLES WHERE id = ?",
            arrayOf(id)
        )
        return cursor.use { c ->
            if (c.moveToFirst()) {
                BossBattle(
                    id = c.getString(c.getColumnIndexOrThrow("id")),
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    topic = ProblemCategory.valueOf(
                        c.getString(c.getColumnIndexOrThrow("topic"))
                    ),
                    bossHp = c.getInt(c.getColumnIndexOrThrow("boss_hp")),
                    playerShields = c.getInt(c.getColumnIndexOrThrow("player_shields")),
                    currentRound = c.getInt(c.getColumnIndexOrThrow("current_round")),
                    totalRounds = c.getInt(c.getColumnIndexOrThrow("total_rounds")),
                    difficulty = BossDifficulty.valueOf(
                        c.getString(c.getColumnIndexOrThrow("difficulty"))
                    ),
                    isDefeated = c.getInt(c.getColumnIndexOrThrow("is_defeated")) == 1
                )
            } else {
                null
            }
        }
    }
}
