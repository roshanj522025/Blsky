package com.blsky.chess.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.blsky.chess.data.*
import com.blsky.chess.engine.*

sealed class PuzzleState {
    object Idle : PuzzleState()
    data class Thinking(val message: String) : PuzzleState()
    object WaitingForMove : PuzzleState()
    data class CorrectMove(val message: String, val isComplete: Boolean) : PuzzleState()
    data class WrongMove(val message: String) : PuzzleState()
    object Solved : PuzzleState()
    object Failed : PuzzleState()
    object ShowingSolution : PuzzleState()
}

class PuzzleViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = com.blsky.chess.data.ChessRepository(app)

    private val _puzzle = MutableLiveData<ChessPuzzle?>()
    val puzzle: LiveData<ChessPuzzle?> = _puzzle

    private val _boardPosition = MutableLiveData<ChessPosition?>()
    val boardPosition: LiveData<ChessPosition?> = _boardPosition

    private val _puzzleState = MutableLiveData<PuzzleState>(PuzzleState.Idle)
    val puzzleState: LiveData<PuzzleState> = _puzzleState

    private val _moveHistory = MutableLiveData<List<String>>(emptyList())
    val moveHistory: LiveData<List<String>> = _moveHistory

    private var currentPosition: ChessPosition? = null
    private var currentMoveIndex = 0
    private var solutionMoves: List<Move> = emptyList()
    private var playerMoveCount = 0
    private var puzzleIndex = 0
    private var allPuzzles: List<ChessPuzzle> = emptyList()

    fun loadPuzzle(puzzle: ChessPuzzle) {
        _puzzle.value = puzzle
        currentMoveIndex = 0
        playerMoveCount = 0
        solutionMoves = puzzle.moves.mapNotNull { Move.fromUci(it) }
        _moveHistory.value = emptyList()

        val pos = FenParser.parse(puzzle.fen)
        currentPosition = pos
        _boardPosition.value = pos
        _puzzleState.value = PuzzleState.WaitingForMove
    }

    fun loadAllPuzzles(index: Int = 0) {
        allPuzzles = repository.loadPuzzles()
        puzzleIndex = index
        if (allPuzzles.isNotEmpty()) {
            loadPuzzle(allPuzzles[index])
        }
    }

    fun nextPuzzle() {
        if (puzzleIndex < allPuzzles.size - 1) {
            puzzleIndex++
            loadPuzzle(allPuzzles[puzzleIndex])
        }
    }

    fun previousPuzzle() {
        if (puzzleIndex > 0) {
            puzzleIndex--
            loadPuzzle(allPuzzles[puzzleIndex])
        }
    }

    fun onPlayerMove(move: Move) {
        val pos = currentPosition ?: return
        val puzzle = _puzzle.value ?: return
        val state = _puzzleState.value

        if (state !is PuzzleState.WaitingForMove) return
        if (currentMoveIndex >= solutionMoves.size) return

        val expectedMove = solutionMoves[currentMoveIndex]

        if (move.from == expectedMove.from && move.to == expectedMove.to) {
            // Correct player move
            val newPos = MoveApplier.applyMove(pos, move) ?: return
            currentPosition = newPos
            _boardPosition.value = newPos
            currentMoveIndex++
            playerMoveCount++

            val history = _moveHistory.value?.toMutableList() ?: mutableListOf()
            history.add(move.uci)
            _moveHistory.value = history

            if (currentMoveIndex >= solutionMoves.size) {
                // Puzzle solved!
                _puzzleState.value = PuzzleState.Solved
                repository.updatePuzzle(puzzle.copy(solved = true))
                return
            }

            // Make opponent's response
            _puzzleState.value = PuzzleState.CorrectMove("✓ Correct! Keep going...", false)
            makeOpponentMove()
        } else {
            // Wrong move
            _puzzleState.value = PuzzleState.WrongMove("✗ That's not right. Try again!")
            // Apply the wrong move temporarily to show it, then revert
            val wrongPos = MoveApplier.applyMove(pos, move)
            if (wrongPos != null) {
                _boardPosition.value = wrongPos
                // Revert after brief pause (handled in activity)
            }
        }
    }

    fun revertLastMove() {
        _boardPosition.value = currentPosition
        _puzzleState.value = PuzzleState.WaitingForMove
    }

    private fun makeOpponentMove() {
        if (currentMoveIndex >= solutionMoves.size) return
        val opponentMove = solutionMoves[currentMoveIndex]
        val pos = currentPosition ?: return

        val newPos = MoveApplier.applyMove(pos, opponentMove) ?: return
        currentPosition = newPos
        _boardPosition.value = newPos
        currentMoveIndex++

        val history = _moveHistory.value?.toMutableList() ?: mutableListOf()
        history.add(opponentMove.uci)
        _moveHistory.value = history

        if (currentMoveIndex >= solutionMoves.size) {
            _puzzleState.value = PuzzleState.Solved
            val puzzle = _puzzle.value
            if (puzzle != null) repository.updatePuzzle(puzzle.copy(solved = true))
        } else {
            _puzzleState.value = PuzzleState.WaitingForMove
        }
    }

    fun showSolution() {
        _puzzleState.value = PuzzleState.ShowingSolution
        val puzzle = _puzzle.value ?: return
        repository.updatePuzzle(puzzle.copy(failed = true))
    }

    fun giveUp() {
        val puzzle = _puzzle.value ?: return
        repository.updatePuzzle(puzzle.copy(failed = true))
        _puzzleState.value = PuzzleState.Failed
    }

    fun getPuzzleIndex() = puzzleIndex
    fun getTotalPuzzles() = allPuzzles.size
}
