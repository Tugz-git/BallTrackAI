package com.balltrack.ai.game

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.rosterDataStore by preferencesDataStore("rosters")

data class Player(
    val id: String,
    val name: String,
    val faceDataId: String? = null, // links to FaceRecognitionManager.PlayerFaceData.playerId, null if manual-only
    val makes: Int = 0,
    val attempts: Int = 0,
    val streak: Int = 0
)

enum class Team { A, B }

data class Roster(
    val name: String,                          // e.g. "Mike vs Jake & Sam"
    val teamA: List<Player>,
    val teamB: List<Player>,
    val savedAt: Long = System.currentTimeMillis()
) {
    fun format() = "${teamA.joinToString(" & ") { it.name }} vs ${teamB.joinToString(" & ") { it.name }}"
}

class RosterRepository(private val context: Context) {
    private val gson = Gson()
    private val key = stringPreferencesKey("saved_rosters")

    suspend fun saveRoster(roster: Roster) {
        val current = loadAll().toMutableList()
        current.removeAll { it.name == roster.name }
        current.add(0, roster)
        context.rosterDataStore.edit { prefs -> prefs[key] = gson.toJson(current.take(MAX_SAVED)) }
    }

    suspend fun loadAll(): List<Roster> {
        val json = context.rosterDataStore.data.map { it[key] }.first() ?: return emptyList()
        return try {
            val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, Roster::class.java).type
            gson.fromJson(json, type)
        } catch (_: Exception) { emptyList() }
    }

    suspend fun deleteRoster(name: String) {
        val current = loadAll().filterNot { it.name == name }
        context.rosterDataStore.edit { prefs -> prefs[key] = gson.toJson(current) }
    }

    companion object {
        private const val MAX_SAVED = 20
    }
}

/** Tracks live possession + auto-attribution logic during a multi-player session. */
class TeamScoringEngine(private var teamA: List<Player>, private var teamB: List<Player>) {

    private var possessionTeam: Team = Team.A
    private var possessionPlayerIndex = 0

    fun currentTeam() = possessionTeam
    fun currentRoster() = if (possessionTeam == Team.A) teamA else teamB

    /**
     * Best guess at who's shooting next within the team in possession.
     * HONEST LIMITATION: with 2+ players on a team, the camera cannot actually
     * see who's holding the ball — this rotates through teammates in order as
     * a reasonable default, not a tracked fact. If it guesses wrong, use
     * recordMake/recordMiss with an explicitly chosen player (e.g. via a quick
     * tap on the Team screen) to correct the credit.
     */
    fun likelyCurrentShooter(): Player? {
        val roster = currentRoster()
        if (roster.isEmpty()) return null
        return roster[possessionPlayerIndex % roster.size]
    }

    /** Player explicitly tapped, or auto-resolved via face recognition match. */
    fun recordMake(player: Player, points: Int, rules: GameRules): Pair<List<Player>, List<Player>> {
        updatePlayer(player.id) { it.copy(makes = it.makes + 1, attempts = it.attempts + 1, streak = it.streak + 1) }
        if (!rules.makeItTakeIt) switchPossession()
        return teamA to teamB
    }

    fun recordMiss(player: Player): Pair<List<Player>, List<Player>> {
        updatePlayer(player.id) { it.copy(attempts = it.attempts + 1, streak = 0) }
        switchPossession()
        possessionPlayerIndex++ // next guess rotates to a different teammate
        return teamA to teamB
    }

    /** Try to resolve which player took the shot via face recognition; null if no match (needs manual tap). */
    fun resolvePlayerByFace(faceDataId: String?): Player? {
        if (faceDataId == null) return null
        return (teamA + teamB).firstOrNull { it.faceDataId == faceDataId }
    }

    private fun switchPossession() { possessionTeam = if (possessionTeam == Team.A) Team.B else Team.A }

    private fun updatePlayer(id: String, transform: (Player) -> Player) {
        teamA = teamA.map { if (it.id == id) transform(it) else it }
        teamB = teamB.map { if (it.id == id) transform(it) else it }
    }

    fun teamScore(team: Team): Int = (if (team == Team.A) teamA else teamB).sumOf { it.makes }
}
