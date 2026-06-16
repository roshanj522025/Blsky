package com.blsky.chess.data

import android.content.Context
import android.content.SharedPreferences
import com.blsky.chess.puzzle.PuzzleExtractor
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChessRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("blsky_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    suspend fun fetchGamesAndExtractPuzzles(username: String, maxGames: Int = 100): Result<List<ChessPuzzle>> {
        return withContext(Dispatchers.IO) {
            try {
                val games = LichessApi.fetchGames(username, maxGames)
                if (games.isEmpty()) {
                    return@withContext Result.failure(Exception("No games found for user '$username'. Make sure the username is correct."))
                }

                val puzzles = mutableListOf<ChessPuzzle>()
                for (game in games) {
                    val gamePuzzles = PuzzleExtractor.extractPuzzles(game, username)
                    puzzles.addAll(gamePuzzles)
                }

                if (puzzles.isEmpty()) {
                    return@withContext Result.failure(Exception("No tactical puzzles found in ${games.size} games."))
                }

                // Save puzzles
                savePuzzles(puzzles)
                saveUsername(username)

                Result.success(puzzles)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun savePuzzles(puzzles: List<ChessPuzzle>) {
        val json = gson.toJson(puzzles)
        prefs.edit().putString("puzzles", json).apply()
        prefs.edit().putLong("puzzles_timestamp", System.currentTimeMillis()).apply()
    }

    fun loadPuzzles(): List<ChessPuzzle> {
        val json = prefs.getString("puzzles", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ChessPuzzle>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveUsername(username: String) {
        prefs.edit().putString("username", username).apply()
    }

    fun getUsername(): String = prefs.getString("username", "") ?: ""

    fun getPuzzlesTimestamp(): Long = prefs.getLong("puzzles_timestamp", 0)

    fun updatePuzzle(puzzle: ChessPuzzle) {
        val puzzles = loadPuzzles().toMutableList()
        val idx = puzzles.indexOfFirst { it.id == puzzle.id }
        if (idx != -1) {
            puzzles[idx] = puzzle
            savePuzzles(puzzles)
        }
    }

    fun getStats(): Map<String, Int> {
        val puzzles = loadPuzzles()
        return mapOf(
            "total" to puzzles.size,
            "solved" to puzzles.count { it.solved },
            "failed" to puzzles.count { it.failed },
            "unsolved" to puzzles.count { !it.solved && !it.failed }
        )
    }
}
