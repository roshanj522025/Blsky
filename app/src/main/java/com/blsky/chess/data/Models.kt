package com.blsky.chess.data

import com.google.gson.annotations.SerializedName

// Lichess API models
data class LichessGame(
    val id: String,
    val rated: Boolean,
    val variant: String,
    val speed: String,
    val perf: String,
    val createdAt: Long,
    val lastMoveAt: Long,
    val status: String,
    val players: Players,
    val moves: String?,
    val pgn: String?,
    val opening: Opening?
)

data class Players(
    val white: PlayerInfo,
    val black: PlayerInfo
)

data class PlayerInfo(
    val user: UserInfo?,
    val rating: Int?,
    val ratingDiff: Int?
)

data class UserInfo(
    val name: String,
    val id: String
)

data class Opening(
    val eco: String,
    val name: String,
    val ply: Int
)

// Puzzle model
data class ChessPuzzle(
    val id: String,
    val gameId: String,
    val fen: String,               // FEN at puzzle start position
    val moves: List<String>,       // Solution moves in UCI format
    val playerColor: String,       // "white" or "black" - who to play
    val puzzleType: PuzzleType,
    val difficulty: Int,           // 1-5
    val description: String,
    val rating: Int = 0,
    val solved: Boolean = false,
    val failed: Boolean = false
)

enum class PuzzleType(val displayName: String, val emoji: String) {
    CHECKMATE("Checkmate", "♚"),
    FORK("Fork", "⚔️"),
    PIN("Pin", "📌"),
    SKEWER("Skewer", "🗡️"),
    DISCOVERED_ATTACK("Discovered Attack", "💡"),
    HANGING_PIECE("Hanging Piece", "🎯"),
    BACK_RANK("Back Rank", "🏰"),
    DEFLECTION("Deflection", "↩️"),
    DECOY("Decoy", "🎭"),
    ZWISCHENZUG("Zwischenzug", "⚡"),
    WINNING_MATERIAL("Win Material", "♟️"),
    COMBINATION("Combination", "🔗")
}

data class PuzzleSession(
    val puzzles: List<ChessPuzzle>,
    val currentIndex: Int = 0,
    val solvedCount: Int = 0,
    val failedCount: Int = 0
) {
    val currentPuzzle: ChessPuzzle? get() = puzzles.getOrNull(currentIndex)
    val isComplete: Boolean get() = currentIndex >= puzzles.size
    val accuracy: Int get() = if (solvedCount + failedCount == 0) 0
        else (solvedCount * 100) / (solvedCount + failedCount)
}

data class UserSettings(
    val username: String,
    val numberOfGames: Int = 100,
    val autoFetch: Boolean = false
)

// Board representation
data class Square(val file: Int, val rank: Int) {
    val notation: String get() = "${'a' + file}${rank + 1}"
    val color: SquareColor get() = if ((file + rank) % 2 == 0) SquareColor.DARK else SquareColor.LIGHT

    override fun toString() = notation

    companion object {
        fun fromNotation(notation: String): Square? {
            if (notation.length != 2) return null
            val file = notation[0] - 'a'
            val rank = notation[1] - '1'
            if (file < 0 || file > 7 || rank < 0 || rank > 7) return null
            return Square(file, rank)
        }
    }
}

enum class SquareColor { LIGHT, DARK }

data class Move(
    val from: Square,
    val to: Square,
    val promotion: Char? = null
) {
    val uci: String get() = "${from.notation}${to.notation}${promotion ?: ""}"

    companion object {
        fun fromUci(uci: String): Move? {
            if (uci.length < 4) return null
            val from = Square.fromNotation(uci.substring(0, 2)) ?: return null
            val to = Square.fromNotation(uci.substring(2, 4)) ?: return null
            val promo = if (uci.length == 5) uci[4] else null
            return Move(from, to, promo)
        }
    }
}
