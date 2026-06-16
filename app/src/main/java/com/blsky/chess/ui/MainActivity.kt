package com.blsky.chess.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.blsky.chess.R
import com.blsky.chess.data.ChessPuzzle
import com.blsky.chess.data.PuzzleType
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel

    private lateinit var tilUsername: TextInputLayout
    private lateinit var etUsername: TextInputEditText
    private lateinit var btnFetch: MaterialButton
    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var tvStatus: TextView
    private lateinit var cardStats: View
    private lateinit var tvTotalPuzzles: TextView
    private lateinit var tvSolvedCount: TextView
    private lateinit var tvFailedCount: TextView
    private lateinit var btnStartPractice: MaterialButton
    private lateinit var btnViewGames: MaterialButton
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var tvLastFetched: TextView
    private lateinit var tvWelcome: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        bindViews()
        setupClickListeners()
        observeViewModel()
    }

    private fun bindViews() {
        tilUsername = findViewById(R.id.tilUsername)
        etUsername = findViewById(R.id.etUsername)
        btnFetch = findViewById(R.id.btnFetch)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        cardStats = findViewById(R.id.cardStats)
        tvTotalPuzzles = findViewById(R.id.tvTotalPuzzles)
        tvSolvedCount = findViewById(R.id.tvSolvedCount)
        tvFailedCount = findViewById(R.id.tvFailedCount)
        btnStartPractice = findViewById(R.id.btnStartPractice)
        btnViewGames = findViewById(R.id.btnViewGames)
        chipGroupFilter = findViewById(R.id.chipGroupFilter)
        tvLastFetched = findViewById(R.id.tvLastFetched)
        tvWelcome = findViewById(R.id.tvWelcome)

        // Pre-fill username if saved
        val savedUsername = viewModel.getUsername()
        if (savedUsername.isNotEmpty()) {
            etUsername.setText(savedUsername)
        }
    }

    private fun setupClickListeners() {
        btnFetch.setOnClickListener {
            val username = etUsername.text?.toString()?.trim() ?: ""
            viewModel.fetchGames(username)
        }

        btnStartPractice.setOnClickListener {
            startActivity(Intent(this, PuzzleActivity::class.java))
        }

        btnViewGames.setOnClickListener {
            startActivity(Intent(this, GamesListActivity::class.java))
        }

        // Filter chips
        val types = listOf("All") + PuzzleType.values().map { it.displayName }
        chipGroupFilter.removeAllViews()
        types.forEachIndexed { index, type ->
            val chip = Chip(this).apply {
                text = type
                isCheckable = true
                isChecked = index == 0
            }
            chipGroupFilter.addView(chip)
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is UiState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    btnFetch.isEnabled = false
                    tvStatus.text = "Fetching games from Lichess..."
                    tvStatus.visibility = View.VISIBLE
                    cardStats.visibility = View.GONE
                }
                is UiState.Success -> {
                    progressBar.visibility = View.GONE
                    btnFetch.isEnabled = true
                    tvStatus.visibility = View.GONE
                    cardStats.visibility = View.VISIBLE

                    val stats = viewModel.getStats()
                    tvTotalPuzzles.text = "${stats["total"]}"
                    tvSolvedCount.text = "${stats["solved"]}"
                    tvFailedCount.text = "${stats["failed"]}"

                    tvWelcome.text = "Welcome, ${state.username}!"

                    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    tvLastFetched.text = "Updated: ${sdf.format(Date())}"

                    btnStartPractice.isEnabled = (stats["total"] ?: 0) > 0
                }
                is UiState.Error -> {
                    progressBar.visibility = View.GONE
                    btnFetch.isEnabled = true
                    tvStatus.text = "Error: ${state.message}"
                    tvStatus.visibility = View.VISIBLE
                }
                is UiState.Empty -> {
                    progressBar.visibility = View.GONE
                    btnFetch.isEnabled = true
                    tvStatus.text = "Enter your Lichess username to fetch games"
                    tvStatus.visibility = View.VISIBLE
                }
            }
        }
    }
}
