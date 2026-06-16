package com.blsky.chess.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.blsky.chess.R
import com.blsky.chess.data.Move
import com.blsky.chess.data.PuzzleType
import com.google.android.material.button.MaterialButton

class PuzzleActivity : AppCompatActivity() {
    private lateinit var viewModel: PuzzleViewModel
    private lateinit var boardView: ChessBoardView
    private lateinit var tvPuzzleInfo: TextView
    private lateinit var tvPuzzleType: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvFeedback: TextView
    private lateinit var tvProgress: TextView
    private lateinit var btnNext: MaterialButton
    private lateinit var btnPrev: MaterialButton
    private lateinit var btnHint: MaterialButton
    private lateinit var btnGiveUp: MaterialButton
    private lateinit var tvDifficulty: TextView

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_puzzle)

        viewModel = ViewModelProvider(this)[PuzzleViewModel::class.java]

        bindViews()
        setupBoard()
        setupButtons()
        observeViewModel()

        viewModel.loadAllPuzzles()
    }

    private fun bindViews() {
        boardView = findViewById(R.id.chessBoardView)
        tvPuzzleInfo = findViewById(R.id.tvPuzzleInfo)
        tvPuzzleType = findViewById(R.id.tvPuzzleType)
        tvDescription = findViewById(R.id.tvDescription)
        tvFeedback = findViewById(R.id.tvFeedback)
        tvProgress = findViewById(R.id.tvProgress)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        btnHint = findViewById(R.id.btnHint)
        btnGiveUp = findViewById(R.id.btnGiveUp)
        tvDifficulty = findViewById(R.id.tvDifficulty)

        supportActionBar?.apply {
            title = "Chess Puzzles"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupBoard() {
        boardView.onMoveSelected = { move: Move ->
            viewModel.onPlayerMove(move)
        }
    }

    private fun setupButtons() {
        btnNext.setOnClickListener { viewModel.nextPuzzle() }
        btnPrev.setOnClickListener { viewModel.previousPuzzle() }
        btnGiveUp.setOnClickListener { viewModel.giveUp() }
        btnHint.setOnClickListener {
            tvFeedback.text = "💡 Think about attacking multiple pieces at once!"
            tvFeedback.visibility = View.VISIBLE
        }
    }

    private fun observeViewModel() {
        viewModel.boardPosition.observe(this) { pos ->
            boardView.position = pos
        }

        viewModel.puzzle.observe(this) { puzzle ->
            puzzle ?: return@observe
            boardView.playerColor = puzzle.playerColor
            boardView.interactive = true

            tvPuzzleType.text = "${puzzle.puzzleType.emoji} ${puzzle.puzzleType.displayName}"
            tvDescription.text = puzzle.description

            val stars = "★".repeat(puzzle.difficulty) + "☆".repeat(5 - puzzle.difficulty)
            tvDifficulty.text = "Difficulty: $stars"

            tvProgress.text = "${viewModel.getPuzzleIndex() + 1} / ${viewModel.getTotalPuzzles()}"
            tvPuzzleInfo.text = "Game: ${puzzle.gameId}"
            tvFeedback.visibility = View.GONE
        }

        viewModel.puzzleState.observe(this) { state ->
            when (state) {
                is PuzzleState.WaitingForMove -> {
                    boardView.interactive = true
                    tvFeedback.visibility = View.GONE
                }
                is PuzzleState.CorrectMove -> {
                    tvFeedback.text = state.message
                    tvFeedback.visibility = View.VISIBLE
                    tvFeedback.setBackgroundColor(0xFF4CAF50.toInt())
                }
                is PuzzleState.WrongMove -> {
                    boardView.interactive = false
                    tvFeedback.text = state.message
                    tvFeedback.visibility = View.VISIBLE
                    tvFeedback.setBackgroundColor(0xFFF44336.toInt())
                    handler.postDelayed({
                        viewModel.revertLastMove()
                        tvFeedback.visibility = View.GONE
                    }, 800)
                }
                is PuzzleState.Solved -> {
                    boardView.interactive = false
                    tvFeedback.text = "🎉 Brilliant! Puzzle solved!"
                    tvFeedback.visibility = View.VISIBLE
                    tvFeedback.setBackgroundColor(0xFF4CAF50.toInt())
                }
                is PuzzleState.Failed -> {
                    boardView.interactive = false
                    tvFeedback.text = "Better luck next time. Study the solution."
                    tvFeedback.visibility = View.VISIBLE
                    tvFeedback.setBackgroundColor(0xFFF44336.toInt())
                }
                is PuzzleState.ShowingSolution -> {
                    boardView.interactive = false
                    tvFeedback.text = "Showing solution..."
                    tvFeedback.visibility = View.VISIBLE
                }
                else -> {}
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
