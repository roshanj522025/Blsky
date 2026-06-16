package com.blsky.chess.engine

import com.blsky.chess.data.Move
import com.blsky.chess.data.Square

// Piece constants
const val EMPTY = 0
const val W_PAWN = 1; const val W_KNIGHT = 2; const val W_BISHOP = 3
const val W_ROOK = 4; const val W_QUEEN = 5; const val W_KING = 6
const val B_PAWN = -1; const val B_KNIGHT = -2; const val B_BISHOP = -3
const val B_ROOK = -4; const val B_QUEEN = -5; const val B_KING = -6

data class ChessPosition(
    val board: Array<IntArray> = Array(8) { IntArray(8) },
    val whiteToMove: Boolean = true,
    val castlingRights: String = "KQkq",
    val enPassantSquare: Square? = null,
    val halfMoveClock: Int = 0,
    val fullMoveNumber: Int = 1
) {
    fun pieceAt(file: Int, rank: Int): Int = board[rank][file]
    fun pieceAt(sq: Square): Int = board[sq.rank][sq.file]

    fun isWhitePiece(piece: Int) = piece > 0
    fun isBlackPiece(piece: Int) = piece < 0
    fun isEmpty(piece: Int) = piece == 0

    fun copy(): ChessPosition {
        val newBoard = Array(8) { r -> board[r].copyOf() }
        return ChessPosition(newBoard, whiteToMove, castlingRights, enPassantSquare, halfMoveClock, fullMoveNumber)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChessPosition) return false
        return board.contentDeepEquals(other.board) &&
                whiteToMove == other.whiteToMove &&
                castlingRights == other.castlingRights &&
                enPassantSquare == other.enPassantSquare
    }

    override fun hashCode(): Int {
        var result = board.contentDeepHashCode()
        result = 31 * result + whiteToMove.hashCode()
        return result
    }
}

object FenParser {
    fun parse(fen: String): ChessPosition? {
        return try {
            val parts = fen.trim().split(" ")
            if (parts.size < 4) return null

            val board = Array(8) { IntArray(8) }
            val rows = parts[0].split("/")
            if (rows.size != 8) return null

            for (rank in 0..7) {
                var file = 0
                for (ch in rows[7 - rank]) {
                    when {
                        ch.isDigit() -> file += ch.digitToInt()
                        else -> {
                            board[rank][file] = charToPiece(ch)
                            file++
                        }
                    }
                }
            }

            val whiteToMove = parts[1] == "w"
            val castling = parts[2]
            val epSquare = if (parts[3] == "-") null else Square.fromNotation(parts[3])
            val halfMove = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val fullMove = parts.getOrNull(5)?.toIntOrNull() ?: 1

            ChessPosition(board, whiteToMove, castling, epSquare, halfMove, fullMove)
        } catch (e: Exception) {
            null
        }
    }

    fun toFen(pos: ChessPosition): String {
        val sb = StringBuilder()
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val piece = pos.board[rank][file]
                if (piece == EMPTY) {
                    empty++
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(pieceToChar(piece))
                }
            }
            if (empty > 0) sb.append(empty)
            if (rank > 0) sb.append('/')
        }
        sb.append(' ')
        sb.append(if (pos.whiteToMove) 'w' else 'b')
        sb.append(' ')
        sb.append(if (pos.castlingRights.isEmpty()) '-' else pos.castlingRights)
        sb.append(' ')
        sb.append(pos.enPassantSquare?.notation ?: '-')
        sb.append(' ')
        sb.append(pos.halfMoveClock)
        sb.append(' ')
        sb.append(pos.fullMoveNumber)
        return sb.toString()
    }

    private fun charToPiece(ch: Char): Int = when (ch) {
        'P' -> W_PAWN; 'N' -> W_KNIGHT; 'B' -> W_BISHOP
        'R' -> W_ROOK; 'Q' -> W_QUEEN; 'K' -> W_KING
        'p' -> B_PAWN; 'n' -> B_KNIGHT; 'b' -> B_BISHOP
        'r' -> B_ROOK; 'q' -> B_QUEEN; 'k' -> B_KING
        else -> EMPTY
    }

    fun pieceToChar(piece: Int): Char = when (piece) {
        W_PAWN -> 'P'; W_KNIGHT -> 'N'; W_BISHOP -> 'B'
        W_ROOK -> 'R'; W_QUEEN -> 'Q'; W_KING -> 'K'
        B_PAWN -> 'p'; B_KNIGHT -> 'n'; B_BISHOP -> 'b'
        B_ROOK -> 'r'; B_QUEEN -> 'q'; B_KING -> 'k'
        else -> '.'
    }
}

object MoveApplier {
    fun applyMove(pos: ChessPosition, move: Move): ChessPosition? {
        val newPos = pos.copy()
        val piece = newPos.pieceAt(move.from)
        if (piece == EMPTY) return null

        // Validate side to move
        val isWhitePiece = piece > 0
        if (isWhitePiece != pos.whiteToMove) return null

        val captured = newPos.pieceAt(move.to)

        // Move piece
        newPos.board[move.from.rank][move.from.file] = EMPTY
        newPos.board[move.to.rank][move.to.file] = piece

        // Promotion
        if (move.promotion != null) {
            val promoPiece = when (move.promotion.lowercaseChar()) {
                'q' -> if (isWhitePiece) W_QUEEN else B_QUEEN
                'r' -> if (isWhitePiece) W_ROOK else B_ROOK
                'b' -> if (isWhitePiece) W_BISHOP else B_BISHOP
                'n' -> if (isWhitePiece) W_KNIGHT else B_KNIGHT
                else -> if (isWhitePiece) W_QUEEN else B_QUEEN
            }
            newPos.board[move.to.rank][move.to.file] = promoPiece
        }

        // En passant capture
        if (Math.abs(piece) == Math.abs(W_PAWN) && move.to == pos.enPassantSquare) {
            val captureRank = if (isWhitePiece) move.to.rank - 1 else move.to.rank + 1
            newPos.board[captureRank][move.to.file] = EMPTY
        }

        // Castling
        if (Math.abs(piece) == W_KING) {
            val fileDiff = move.to.file - move.from.file
            if (Math.abs(fileDiff) == 2) {
                val rank = move.from.rank
                if (fileDiff > 0) { // Kingside
                    newPos.board[rank][5] = newPos.board[rank][7]
                    newPos.board[rank][7] = EMPTY
                } else { // Queenside
                    newPos.board[rank][3] = newPos.board[rank][0]
                    newPos.board[rank][0] = EMPTY
                }
            }
        }

        // Update en passant
        val newEp = if (Math.abs(piece) == Math.abs(W_PAWN) && Math.abs(move.to.rank - move.from.rank) == 2) {
            Square(move.from.file, (move.from.rank + move.to.rank) / 2)
        } else null

        // Update castling rights
        var newCastling = pos.castlingRights
        if (piece == W_KING) newCastling = newCastling.replace("K", "").replace("Q", "")
        if (piece == B_KING) newCastling = newCastling.replace("k", "").replace("q", "")
        if (move.from.file == 0 && move.from.rank == 0) newCastling = newCastling.replace("Q", "")
        if (move.from.file == 7 && move.from.rank == 0) newCastling = newCastling.replace("K", "")
        if (move.from.file == 0 && move.from.rank == 7) newCastling = newCastling.replace("q", "")
        if (move.from.file == 7 && move.from.rank == 7) newCastling = newCastling.replace("k", "")

        val halfMove = if (Math.abs(piece) == Math.abs(W_PAWN) || captured != EMPTY) 0 else pos.halfMoveClock + 1
        val fullMove = if (!pos.whiteToMove) pos.fullMoveNumber + 1 else pos.fullMoveNumber

        return newPos.copy(
            whiteToMove = !pos.whiteToMove,
            castlingRights = newCastling,
            enPassantSquare = newEp,
            halfMoveClock = halfMove,
            fullMoveNumber = fullMove
        )
    }
}

object PositionEvaluator {
    private val pieceValues = mapOf(
        W_PAWN to 100, W_KNIGHT to 320, W_BISHOP to 330,
        W_ROOK to 500, W_QUEEN to 900, W_KING to 20000,
        B_PAWN to -100, B_KNIGHT to -320, B_BISHOP to -330,
        B_ROOK to -500, B_QUEEN to -900, B_KING to -20000
    )

    fun materialScore(pos: ChessPosition): Int {
        var score = 0
        for (rank in 0..7) for (file in 0..7) {
            score += pieceValues[pos.board[rank][file]] ?: 0
        }
        return score
    }

    fun materialCount(pos: ChessPosition, forWhite: Boolean): Int {
        var count = 0
        for (rank in 0..7) for (file in 0..7) {
            val piece = pos.board[rank][file]
            val value = Math.abs(pieceValues[piece] ?: 0)
            if (value < 20000) { // exclude king
                if (forWhite && piece > 0) count += value
                if (!forWhite && piece < 0) count += value
            }
        }
        return count
    }

    fun isSquareAttacked(pos: ChessPosition, sq: Square, byWhite: Boolean): Boolean {
        // Check knight attacks
        val knightMoves = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
        for ((dr, df) in knightMoves) {
            val r = sq.rank + dr; val f = sq.file + df
            if (r in 0..7 && f in 0..7) {
                val piece = pos.board[r][f]
                if (byWhite && piece == W_KNIGHT) return true
                if (!byWhite && piece == B_KNIGHT) return true
            }
        }

        // Check pawn attacks
        val pawnDir = if (byWhite) -1 else 1
        for (df in listOf(-1, 1)) {
            val r = sq.rank + pawnDir; val f = sq.file + df
            if (r in 0..7 && f in 0..7) {
                val piece = pos.board[r][f]
                if (byWhite && piece == W_PAWN) return true
                if (!byWhite && piece == B_PAWN) return true
            }
        }

        // Check sliding pieces (rook/queen directions)
        val rookDirs = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
        for ((dr, df) in rookDirs) {
            var r = sq.rank + dr; var f = sq.file + df
            while (r in 0..7 && f in 0..7) {
                val piece = pos.board[r][f]
                if (piece != EMPTY) {
                    if (byWhite && (piece == W_ROOK || piece == W_QUEEN)) return true
                    if (!byWhite && (piece == B_ROOK || piece == B_QUEEN)) return true
                    break
                }
                r += dr; f += df
            }
        }

        // Check sliding pieces (bishop/queen directions)
        val bishopDirs = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        for ((dr, df) in bishopDirs) {
            var r = sq.rank + dr; var f = sq.file + df
            while (r in 0..7 && f in 0..7) {
                val piece = pos.board[r][f]
                if (piece != EMPTY) {
                    if (byWhite && (piece == W_BISHOP || piece == W_QUEEN)) return true
                    if (!byWhite && (piece == B_BISHOP || piece == B_QUEEN)) return true
                    break
                }
                r += dr; f += df
            }
        }

        // Check king
        val kingMoves = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
        for ((dr, df) in kingMoves) {
            val r = sq.rank + dr; val f = sq.file + df
            if (r in 0..7 && f in 0..7) {
                val piece = pos.board[r][f]
                if (byWhite && piece == W_KING) return true
                if (!byWhite && piece == B_KING) return true
            }
        }

        return false
    }

    fun isInCheck(pos: ChessPosition, whiteKing: Boolean): Boolean {
        // Find king
        val kingPiece = if (whiteKing) W_KING else B_KING
        for (rank in 0..7) for (file in 0..7) {
            if (pos.board[rank][file] == kingPiece) {
                return isSquareAttacked(pos, Square(file, rank), !whiteKing)
            }
        }
        return false
    }
}
