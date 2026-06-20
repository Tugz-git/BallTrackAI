package com.balltrack.ai.game

import com.google.gson.Gson

// ─── Scoring ───────────────────────────────────────────────────────────────

data class Score(val home: Int = 0, val away: Int = 0) {
    fun total() = home + away
}

enum class PointValue(val points: Int) {
    ONE(1), TWO(2), THREE(3)
}

// ─── Game Rules ────────────────────────────────────────────────────────────

data class GameRules(
    val name: String,
    val winScore: Int,           // e.g. 21, 50, 11
    val winByTwo: Boolean,       // must win by 2
    val makeItTakeIt: Boolean,   // scorer keeps possession (street)
    val clearBall: Boolean,      // must clear to arc after defensive rebound
    val threePointsEnabled: Boolean,
    val twoPointsEnabled: Boolean,
    val foulTracking: Boolean,
    val shotClock: Int?,         // seconds, null = no shot clock
    val maxFouls: Int?,          // null = unlimited
    val description: String = ""
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): GameRules = Gson().fromJson(json, GameRules::class.java)

        val STREET_BALL = GameRules(
            name = "Street Ball",
            winScore = 21,
            winByTwo = true,
            makeItTakeIt = true,
            clearBall = true,
            threePointsEnabled = true,
            twoPointsEnabled = true,
            foulTracking = false,
            shotClock = null,
            maxFouls = null,
            description = "Classic street rules. Make it take it, win by 2, twos and threes."
        )

        val PRO_BALL = GameRules(
            name = "Pro Ball",
            winScore = 50,
            winByTwo = false,
            makeItTakeIt = false,
            clearBall = false,
            threePointsEnabled = true,
            twoPointsEnabled = true,
            foulTracking = true,
            shotClock = 24,
            maxFouls = 6,
            description = "Pro rules. Alternating possession, shot clock, foul tracking."
        )

        val ONES = GameRules(
            name = "Ones",
            winScore = 11,
            winByTwo = true,
            makeItTakeIt = true,
            clearBall = false,
            threePointsEnabled = false,
            twoPointsEnabled = false,
            foulTracking = false,
            shotClock = null,
            maxFouls = null,
            description = "Everything is 1 point. First to 11, win by 2."
        )

        val TWENTYONE = GameRules(
            name = "21",
            winScore = 21,
            winByTwo = false,
            makeItTakeIt = true,
            clearBall = true,
            threePointsEnabled = true,
            twoPointsEnabled = true,
            foulTracking = false,
            shotClock = null,
            maxFouls = null,
            description = "Classic 21. Make it take it. Exactly 21 to win — going over resets you to 11."
        )

        val HORSE = GameRules(
            name = "H.O.R.S.E",
            winScore = 0, // special — letter-based, not score-based
            winByTwo = false,
            makeItTakeIt = false,
            clearBall = false,
            threePointsEnabled = true,
            twoPointsEnabled = true,
            foulTracking = false,
            shotClock = null,
            maxFouls = null,
            description = "Match shots or earn a letter. Spell HORSE to lose."
        )

        val defaults = listOf(STREET_BALL, PRO_BALL, ONES, TWENTYONE, HORSE)
    }
}

// ─── Game State ────────────────────────────────────────────────────────────

data class GameState(
    val rules: GameRules,
    val score: Score = Score(),
    val possession: Possession = Possession.HOME,
    val fouls: FoulCount = FoulCount(),
    val horseLetters: HorseLetters = HorseLetters(),
    val shotClockRemaining: Int? = null,
    val isGameOver: Boolean = false,
    val winner: Possession? = null,
    val currentStreak: Int = 0,
    val sessionMakes: Int = 0,
    val sessionAttempts: Int = 0
) {
    fun withMake(by: Possession, points: Int): GameState {
        val newScore = when (by) {
            Possession.HOME -> score.copy(home = score.home + points)
            Possession.AWAY -> score.copy(away = score.away + points)
        }
        val possession = if (rules.makeItTakeIt) by else by.other()
        val gameOver = checkWin(newScore)
        return copy(
            score = newScore,
            possession = possession,
            isGameOver = gameOver,
            winner = if (gameOver) by else null,
            currentStreak = currentStreak + 1,
            sessionMakes = sessionMakes + 1,
            sessionAttempts = sessionAttempts + 1,
            shotClockRemaining = rules.shotClock
        )
    }

    fun withMiss(by: Possession): GameState {
        val possession = by.other()
        return copy(
            possession = possession,
            currentStreak = 0,
            sessionAttempts = sessionAttempts + 1,
            shotClockRemaining = rules.shotClock
        )
    }

    private fun checkWin(s: Score): Boolean {
        val homeWins = s.home >= rules.winScore && (!rules.winByTwo || s.home - s.away >= 2)
        val awayWins = s.away >= rules.winScore && (!rules.winByTwo || s.away - s.home >= 2)
        return homeWins || awayWins
    }
}

enum class Possession {
    HOME, AWAY;
    fun other() = if (this == HOME) AWAY else HOME
}

data class FoulCount(val home: Int = 0, val away: Int = 0)

data class HorseLetters(val home: String = "", val away: String = "") {
    private val word = "HORSE"
    fun homeDisplay() = word.take(home.length) + "_".repeat(5 - home.length)
    fun awayDisplay() = word.take(away.length) + "_".repeat(5 - away.length)
    fun homeOut() = home.length >= 5
    fun awayOut() = away.length >= 5
}
