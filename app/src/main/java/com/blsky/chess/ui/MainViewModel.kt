package com.blsky.chess.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.blsky.chess.data.ChessRepository
import com.blsky.chess.data.ChessPuzzle
import kotlinx.coroutines.launch

sealed class UiState {
    object Loading : UiState()
    data class Success(val puzzles: List<ChessPuzzle>, val username: String) : UiState()
    data class Error(val message: String) : UiState()
    object Empty : UiState()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ChessRepository(app)

    private val _uiState = MutableLiveData<UiState>(UiState.Empty)
    val uiState: LiveData<UiState> = _uiState

    private val _puzzles = MutableLiveData<List<ChessPuzzle>>()
    val puzzles: LiveData<List<ChessPuzzle>> = _puzzles

    init {
        loadSavedPuzzles()
    }

    fun loadSavedPuzzles() {
        val saved = repository.loadPuzzles()
        val username = repository.getUsername()
        if (saved.isNotEmpty()) {
            _puzzles.value = saved
            _uiState.value = UiState.Success(saved, username)
        }
    }

    fun fetchGames(username: String) {
        if (username.isBlank()) {
            _uiState.value = UiState.Error("Please enter a Lichess username")
            return
        }
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val result = repository.fetchGamesAndExtractPuzzles(username.trim())
            result.fold(
                onSuccess = { puzzles ->
                    _puzzles.value = puzzles
                    _uiState.value = UiState.Success(puzzles, username.trim())
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }

    fun getStats() = repository.getStats()
    fun getUsername() = repository.getUsername()
}
