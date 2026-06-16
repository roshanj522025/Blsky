package com.blsky.chess.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blsky.chess.R
import com.blsky.chess.data.ChessPuzzle

class GamesListActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_games_list)

        supportActionBar?.apply {
            title = "All Puzzles"
            setDisplayHomeAsUpEnabled(true)
        }

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        viewModel.puzzles.observe(this) { puzzles ->
            recyclerView.adapter = PuzzleAdapter(puzzles) { puzzle, index ->
                val intent = Intent(this, PuzzleActivity::class.java)
                intent.putExtra("puzzle_index", index)
                startActivity(intent)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

class PuzzleAdapter(
    private val puzzles: List<ChessPuzzle>,
    private val onClick: (ChessPuzzle, Int) -> Unit
) : RecyclerView.Adapter<PuzzleAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tvPuzzleTypeItem)
        val tvDescription: TextView = view.findViewById(R.id.tvDescriptionItem)
        val tvStatus: TextView = view.findViewById(R.id.tvStatusItem)
        val tvDifficulty: TextView = view.findViewById(R.id.tvDifficultyItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_puzzle, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val puzzle = puzzles[position]
        holder.tvType.text = "${puzzle.puzzleType.emoji} ${puzzle.puzzleType.displayName}"
        holder.tvDescription.text = puzzle.description
        holder.tvDifficulty.text = "★".repeat(puzzle.difficulty)
        holder.tvStatus.text = when {
            puzzle.solved -> "✓ Solved"
            puzzle.failed -> "✗ Failed"
            else -> "○ Unsolved"
        }
        holder.tvStatus.setTextColor(
            holder.itemView.context.getColor(
                when {
                    puzzle.solved -> android.R.color.holo_green_dark
                    puzzle.failed -> android.R.color.holo_red_dark
                    else -> android.R.color.darker_gray
                }
            )
        )
        holder.itemView.setOnClickListener { onClick(puzzle, position) }
    }

    override fun getItemCount() = puzzles.size
}
