package com.blsky.chess.puzzle

import com.blsky.chess.data.*
import com.blsky.chess.engine.*
import java.util.UUID

object PuzzleExtractor {

    private const val STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    fun extractPuzzles(game: LichessGame, playerUsername: String): List<ChessPuzzle> {
        val moves = game.moves?.split(" ")?.filter { it.isNotBlank() } ?: return emptyList()
        if (moves.size < 6) return emptyList()

        // Determine player color
        val playerIsWhite = game.players.white.user?.name?.equals(playerUsername, ignoreCase = true) == true ||
                game.players.white.user?.id?.equals(playerUsername, ignoreCase = true) == true

        val puzzles = mutableListOf<ChessPuzzle>()

        // Replay game and detect tactical opportunities
        var pos = FenParser.parse(STARTING_FEN) ?: return emptyList()
        val positions = mutableListOf(pos)

        for (uci in moves) {
            val move = Move.fromUci(uci) ?: break
            val newPos = MoveApplier.applyMove(pos, move) ?: break
            positions.add(newPos)
            pos = newPos
        }

        // Scan positions for tactics
        for (i in 1 until positions.size - 2) {
            val currentPos = positions[i]
            val isPlayerTurn = currentPos.whiteToMove == playerIsWhite

            // Analyze position for tactical patterns
            val remainingMoves = moves.subList(i, minOf(i + 8, moves.size))
            val puzzle = analyzeTactics(
                currentPos,
                remainingMoves,
                game.id,
                if (currentPos.whiteToMove) "white" else "black",
                i
            )

            if (puzzle != null) {
                puzzles.add(puzzle)
                // Skip ahead to avoid overlapping puzzles
                break // One puzzle per game for now to keep it clean
            }
        }

        return puzzles
    }

    private fun analyzeTactics(
        pos: ChessPosition,
        futureMoves: List<String>,
        gameId: String,
        sideToMove: String,
        moveIndex: Int
    ): ChessPuzzle? {
        if (futureMoves.isEmpty()) return null

        val isWhite = pos.whiteToMove
        val startMaterial = PositionEvaluator.materialScore(pos)

        // Try applying next few moves and see if material changes significantly
        var currentPos = pos
        var materialGain = 0
        var checkmate = false
        var puzzleType = PuzzleType.WINNING_MATERIAL
        val solutionMoves = mutableListOf<String>()

        for (uci in futureMoves.take(6)) {
            val move = Move.fromUci(uci) ?: break
            val capturedPiece = currentPos.pieceAt(move.to)

            val nextPos = MoveApplier.applyMove(currentPos, move) ?: break
            solutionMoves.add(uci)

            // Check if this results in checkmate
            if (PositionEvaluator.isInCheck(nextPos, nextPos.whiteToMove)) {
                // Quick checkmate detection: no legal moves
                checkmate = true
                puzzleType = PuzzleType.CHECKMATE
            }

            val newMaterial = PositionEvaluator.materialScore(nextPos)
            val gain = if (isWhite) newMaterial - startMaterial else startMaterial - newMaterial

            if (gain > 200) {
                materialGain = gain
            }

            currentPos = nextPos

            // Stop after we've found the key tactical sequence
            if (checkmate || (materialGain > 200 && solutionMoves.size >= 2)) break
        }

        if (solutionMoves.isEmpty()) return null

        // Detect specific tactics by examining the position
        val detectedType = detectTacticType(pos, futureMoves, isWhite) ?: run {
            if (checkmate) PuzzleType.CHECKMATE
            else if (materialGain >= 300) PuzzleType.FORK
            else if (materialGain >= 100) PuzzleType.HANGING_PIECE
            else null
        }

        if (detectedType == null && materialGain < 100 && !checkmate) return null

        val finalType = detectedType ?: if (checkmate) PuzzleType.CHECKMATE else PuzzleType.WINNING_MATERIAL

        val difficulty = when {
            solutionMoves.size <= 2 -> 1
            solutionMoves.size <= 4 -> 2
            solutionMoves.size <= 6 -> 3
            else -> 4
        }

        val description = buildDescription(finalType, solutionMoves.size, sideToMove)

        return ChessPuzzle(
            id = UUID.randomUUID().toString(),
            gameId = gameId,
            fen = FenParser.toFen(pos),
            moves = solutionMoves,
            playerColor = sideToMove,
            puzzleType = finalType,
            difficulty = difficulty,
            description = description
        )
    }

    private fun detectTacticType(pos: ChessPosition, moves: List<String>, isWhite: Boolean): PuzzleType? {
        if (moves.isEmpty()) return null

        val firstMove = Move.fromUci(moves[0]) ?: return null
        val movingPiece = Math.abs(pos.pieceAt(firstMove.from))
        val capturedPiece = Math.abs(pos.pieceAt(firstMove.to))

        // Checkmate patterns
        if (moves.size <= 4 && isCheckmateThreat(pos, moves)) return PuzzleType.CHECKMATE

        // Back rank weakness
        val targetRank = firstMove.to.rank
        if ((isWhite && targetRank == 7) || (!isWhite && targetRank == 0)) {
            if (capturedPiece == Math.abs(B_ROOK) || capturedPiece == Math.abs(W_ROOK)) {
                return PuzzleType.BACK_RANK
            }
        }

        // Fork detection: knight moves and attacks multiple pieces
        if (movingPiece == Math.abs(W_KNIGHT) && moves.size >= 1) {
            return detectFork(pos, firstMove, isWhite)
        }

        // Pin/Skewer: sliding piece moves
        if (movingPiece == Math.abs(W_BISHOP) || movingPiece == Math.abs(W_ROOK) || movingPiece == Math.abs(W_QUEEN)) {
            val sliding = detectPinOrSkewer(pos, firstMove, isWhite)
            if (sliding != null) return sliding
        }

        // Discovered attack
        if (moves.size >= 2) {
            if (isDiscoveredAttack(pos, moves, isWhite)) return PuzzleType.DISCOVERED_ATTACK
        }

        return null
    }

    private fun isCheckmateThreat(pos: ChessPosition, moves: List<String>): Boolean {
        // Check if sequence leads to checkmate
        var current = pos
        for (uci in moves) {
            val move = Move.fromUci(uci) ?: return false
            current = MoveApplier.applyMove(current, move) ?: return false
        }
        return PositionEvaluator.isInCheck(current, current.whiteToMove)
    }

    private fun detectFork(pos: ChessPosition, knightMove: Move, isWhite: Boolean): PuzzleType? {
        val knightFile = knightMove.to.file
        val knightRank = knightMove.to.rank
        val knightAttacks = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)

        var attackedValuePieces = 0
        var attacksKing = false

        for ((dr, df) in knightAttacks) {
            val r = knightRank + dr; val f = knightFile + df
            if (r in 0..7 && f in 0..7) {
                val piece = pos.board[r][f]
                val isEnemy = if (isWhite) piece < 0 else piece > 0
                if (isEnemy) {
                    val absVal = Math.abs(piece)
                    when (absVal) {
                        Math.abs(B_KING) -> attacksKing = true
                        Math.abs(B_QUEEN) -> attackedValuePieces++
                        Math.abs(B_ROOK) -> attackedValuePieces++
                        Math.abs(B_BISHOP), Math.abs(B_KNIGHT) -> attackedValuePieces++
                    }
                }
            }
        }

        return if (attackedValuePieces >= 2 || (attacksKing && attackedValuePieces >= 1)) PuzzleType.FORK else null
    }

    private fun detectPinOrSkewer(pos: ChessPosition, move: Move, isWhite: Boolean): PuzzleType? {
        // Simple heuristic: if piece moves to align with enemy king, it's potentially a pin/skewer
        val piece = Math.abs(pos.pieceAt(move.from))
        val toFile = move.to.file; val toRank = move.to.rank

        // Find enemy king
        val enemyKing = if (isWhite) B_KING else W_KING
        for (rank in 0..7) for (file in 0..7) {
            if (pos.board[rank][file] == enemyKing) {
                // Check if the move aligns on same rank, file, or diagonal
                val sameFile = toFile == file
                val sameRank = toRank == rank
                val sameDiag = Math.abs(toFile - file) == Math.abs(toRank - rank)

                if ((sameFile || sameRank) && (piece == Math.abs(W_ROOK) || piece == Math.abs(W_QUEEN))) {
                    return PuzzleType.PIN
                }
                if (sameDiag && (piece == Math.abs(W_BISHOP) || piece == Math.abs(W_QUEEN))) {
                    return PuzzleType.PIN
                }
            }
        }
        return null
    }

    private fun isDiscoveredAttack(pos: ChessPosition, moves: List<String>, isWhite: Boolean): Boolean {
        // If first move uncovers an attack by a piece behind it
        val move1 = Move.fromUci(moves[0]) ?: return false
        val pos1 = MoveApplier.applyMove(pos, move1) ?: return false
        val move2 = Move.fromUci(moves[1]) ?: return false
        val captured = pos1.pieceAt(move2.to)
        val capturedAbs = Math.abs(captured)
        return capturedAbs == Math.abs(W_QUEEN) || capturedAbs == Math.abs(W_ROOK) || capturedAbs == Math.abs(B_KNIGHT)
    }

    private fun buildDescription(type: PuzzleType, moveCount: Int, side: String): String {
        val moves = if (moveCount == 1) "1 move" else "$moveCount moves"
        return when (type) {
            PuzzleType.CHECKMATE -> "Find the checkmate in $moves!"
            PuzzleType.FORK -> "Find the fork to win material!"
            PuzzleType.PIN -> "Find the pin to gain advantage!"
            PuzzleType.SKEWER -> "Find the skewer!"
            PuzzleType.DISCOVERED_ATTACK -> "Find the discovered attack!"
            PuzzleType.HANGING_PIECE -> "Find the hanging piece!"
            PuzzleType.BACK_RANK -> "Exploit the back rank weakness!"
            PuzzleType.DEFLECTION -> "Find the deflection!"
            PuzzleType.DECOY -> "Use the decoy!"
            PuzzleType.ZWISCHENZUG -> "Find the zwischenzug!"
            PuzzleType.WINNING_MATERIAL -> "Win material with the best move!"
            PuzzleType.COMBINATION -> "Find the winning combination!"
        }
    }
}
