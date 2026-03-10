package com.example.tilemaster

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val MAX_LEVEL = 96 // Cap at 100 tiles per pile (4 + 96)
    }

    private val highScoreManager = HighScoreManager(application)

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _soundEvent = MutableSharedFlow<SoundEvent>(extraBufferCapacity = 1)
    val soundEvent: SharedFlow<SoundEvent> = _soundEvent.asSharedFlow()

    private var timerJob: Job? = null
    private var hazardJob: Job? = null

    init {
        startNewGame()
    }

    fun startNewGame() {
        startLevel(1)
    }

    fun startNextLevel() {
        startLevel(_state.value.level + 1)
    }

    private fun startLevel(level: Int) {
        timerJob?.cancel()
        hazardJob?.cancel()

        val safeLevel = level.coerceIn(1, MAX_LEVEL)
        val tilesPerPile = 4 + safeLevel  // Level 1 = 5, Level 2 = 6, etc.
        val colors = TileColor.entries.toTypedArray()
        val mutablePiles = MutableList(9) {
            val pile = mutableListOf<Tile>()
            repeat(tilesPerPile) {
                val color = colors.random()
                // ~1 in 5 chance of being mystery, but must be at least 2 tiles apart
                val recentHidden = pile.size >= 1 && pile.last().hidden ||
                        pile.size >= 2 && pile[pile.size - 2].hidden
                val hidden = !recentHidden && (0 until 5).random() == 0
                pile.add(Tile(color = color, hidden = hidden))
            }
            pile
        }

        // Place 5 bombs on random non-mystery tiles
        val eligible = mutableListOf<Pair<Int, Int>>()
        for (p in mutablePiles.indices) {
            for (t in mutablePiles[p].indices) {
                if (!mutablePiles[p][t].hidden) eligible.add(p to t)
            }
        }
        val bombPositions = eligible.shuffled().take(5)
        for ((p, t) in bombPositions) {
            mutablePiles[p][t] = mutablePiles[p][t].copy(isBomb = true)
        }

        // Place hazard tiles (2 + level/2, on non-bomb non-mystery tiles)
        val hazardCount = 2 + safeLevel / 2
        val hazardEligible = mutableListOf<Pair<Int, Int>>()
        for (p in mutablePiles.indices) {
            for (t in mutablePiles[p].indices) {
                val tile = mutablePiles[p][t]
                if (!tile.hidden && !tile.isBomb) hazardEligible.add(p to t)
            }
        }
        val hazardPositions = hazardEligible.shuffled().take(hazardCount)
        for ((p, t) in hazardPositions) {
            val marks = (1..3).random()
            mutablePiles[p][t] = mutablePiles[p][t].copy(isHazard = true, hazardMarks = marks)
        }

        val piles = mutablePiles.map { it.toList() }

        // Count tiles per color (excluding bombs — hazards DO count as they must be homed)
        val colorTotals = mutableMapOf<TileColor, Int>()
        for (pile in piles) {
            for (tile in pile) {
                if (!tile.isBomb) {
                    colorTotals[tile.color] = (colorTotals[tile.color] ?: 0) + 1
                }
            }
        }
        val totalTiles = piles.sumOf { pile -> pile.count { !it.isBomb } }

        _state.value = GameState(
            piles = piles,
            totalTiles = totalTiles,
            level = safeLevel,
            colorTotals = colorTotals,
            highScores = highScoreManager.getHighScores()
        )
    }

    private fun startTimer() {
        timerJob?.cancel()
        val now = System.currentTimeMillis()
        _state.update { it.copy(timerStarted = true, startTimeMillis = now) }

        timerJob = viewModelScope.launch {
            while (true) {
                delay(33L) // ~30fps — sufficient for centisecond display
                _state.update {
                    if (!it.gameOver) {
                        it.copy(elapsedMillis = System.currentTimeMillis() - it.startTimeMillis)
                    } else {
                        it
                    }
                }
            }
        }

        startHazardChecker()
    }

    private fun startHazardChecker() {
        hazardJob?.cancel()
        hazardJob = viewModelScope.launch {
            while (true) {
                delay(200L) // check every 200ms
                val currentState = _state.value
                if (currentState.gameOver) break

                val now = System.currentTimeMillis()
                val newTimers = currentState.hazardTimers.toMutableMap()

                // Track which hazard tiles are currently on top
                for (i in currentState.piles.indices) {
                    val pile = currentState.piles[i]
                    if (pile.isNotEmpty() && pile.last().isHazard) {
                        if (i !in newTimers) {
                            newTimers[i] = now
                        }
                    } else {
                        newTimers.remove(i)
                    }
                }

                // Check if any hazard has been sitting for 2 seconds
                var spread = false
                val newPiles = currentState.piles.map { it.toMutableList() }
                val pilesToSpread = mutableListOf<Int>()

                for ((pileIndex, timestamp) in newTimers) {
                    if (now - timestamp >= 2000L) {
                        pilesToSpread.add(pileIndex)
                    }
                }

                for (pileIndex in pilesToSpread) {
                    val pile = newPiles[pileIndex]
                    if (pile.isNotEmpty() && pile.last().isHazard) {
                        val hazardTile = pile.last()
                        // Spread: add tiles of this color to adjacent piles
                        for (adjIndex in getAdjacentIndices(pileIndex)) {
                            newPiles[adjIndex].add(Tile(color = hazardTile.color))
                        }
                        // Reset the timer for this hazard
                        newTimers[pileIndex] = now
                        spread = true
                    }
                }

                if (spread) {
                    // Recalculate totals since new tiles were added
                    val newColorTotals = mutableMapOf<TileColor, Int>()
                    for (pile in newPiles) {
                        for (tile in pile) {
                            if (!tile.isBomb) {
                                newColorTotals[tile.color] = (newColorTotals[tile.color] ?: 0) + 1
                            }
                        }
                    }
                    val newTotalTiles = newPiles.sumOf { pile -> pile.count { !it.isBomb } }

                    _state.update {
                        it.copy(
                            piles = newPiles.map { p -> p.toList() },
                            hazardTimers = newTimers,
                            colorTotals = newColorTotals,
                            totalTiles = newTotalTiles
                        )
                    }
                    _soundEvent.tryEmit(SoundEvent.HAZARD_SPREAD)
                } else if (newTimers != currentState.hazardTimers) {
                    _state.update { it.copy(hazardTimers = newTimers) }
                }
            }
        }
    }

    fun selectTile(pileIndex: Int) {
        val currentState = _state.value
        if (currentState.gameOver) return

        val pile = currentState.piles[pileIndex]
        if (pile.isEmpty()) return

        // Start timer on first tile selection
        if (!currentState.timerStarted) {
            startTimer()
        }

        val topTile = pile.last()

        // Bomb activates immediately on tap
        if (topTile.isBomb) {
            activateBomb(pileIndex)
            return
        }

        // Tapping the same pile again deselects
        if (currentState.selectedPileIndex == pileIndex) {
            _state.value = _state.value.copy(selectedTile = null, selectedPileIndex = null)
            return
        }

        _state.value = _state.value.copy(selectedTile = topTile, selectedPileIndex = pileIndex)
        _soundEvent.tryEmit(SoundEvent.CLICK)
    }

    private fun activateBomb(pileIndex: Int) {
        val currentState = _state.value
        val newPiles = currentState.piles.map { it.toMutableList() }

        // Remove the bomb itself
        newPiles[pileIndex].removeAt(newPiles[pileIndex].lastIndex)

        // Destroy top tile of each adjacent pile and collect for animation
        var tilesPlaced = 0
        val explodedTiles = mutableListOf<Pair<Int, Tile>>()
        for (adjIndex in getAdjacentIndices(pileIndex)) {
            if (newPiles[adjIndex].isNotEmpty()) {
                val adjTile = newPiles[adjIndex].removeAt(newPiles[adjIndex].lastIndex)
                if (!adjTile.isBomb) {
                    tilesPlaced++
                    explodedTiles.add(adjIndex to adjTile)
                }
            }
        }

        val newCount = currentState.tilesPlaced + tilesPlaced
        val gameOver = newPiles.all { it.isEmpty() }
        val finalTime = if (gameOver) {
            System.currentTimeMillis() - currentState.startTimeMillis
        } else {
            currentState.elapsedMillis
        }
        val isNewHigh = if (gameOver) highScoreManager.addScore(finalTime) else false

        _state.value = currentState.copy(
            piles = newPiles.map { it.toList() },
            selectedTile = null,
            selectedPileIndex = null,
            tilesPlaced = newCount,
            gameOver = gameOver,
            elapsedMillis = finalTime,
            highScores = if (gameOver) highScoreManager.getHighScores() else currentState.highScores,
            isNewHighScore = isNewHigh,
            bombExplosionKey = currentState.bombExplosionKey + 1,
            bombExplosionPileIndex = pileIndex,
            bombExplodedTiles = explodedTiles
        )

        if (gameOver) {
            timerJob?.cancel()
            hazardJob?.cancel()
            _soundEvent.tryEmit(SoundEvent.FANFARE)
        } else {
            _soundEvent.tryEmit(SoundEvent.EXPLOSION)
        }
    }

    private fun getAdjacentIndices(pileIndex: Int): List<Int> {
        val row = pileIndex / 3
        val col = pileIndex % 3
        val adj = mutableListOf<Int>()
        if (row > 0) adj.add((row - 1) * 3 + col)
        if (row < 2) adj.add((row + 1) * 3 + col)
        if (col > 0) adj.add(row * 3 + (col - 1))
        if (col < 2) adj.add(row * 3 + (col + 1))
        return adj
    }

    fun placeInHome(homeColor: TileColor) {
        val currentState = _state.value
        if (currentState.gameOver) return

        val selectedTile = currentState.selectedTile ?: return
        val pileIndex = currentState.selectedPileIndex ?: return

        val newPiles = currentState.piles.map { it.toMutableList() }

        if (selectedTile.color == homeColor) {
            // Correct color match — remove tile from pile
            newPiles[pileIndex].removeAt(newPiles[pileIndex].lastIndex)

            if (selectedTile.isHazard && selectedTile.hazardMarks > 1) {
                // Hazard still has marks remaining: change color, put it back
                val colors = TileColor.entries.filter { it != selectedTile.color }
                val newColor = colors.random()
                val mutatedHazard = selectedTile.copy(
                    color = newColor,
                    hazardMarks = selectedTile.hazardMarks - 1
                )
                newPiles[pileIndex].add(mutatedHazard)

                // Update color totals: the old color lost one, new color gained one
                val newColorTotals = currentState.colorTotals.toMutableMap()
                newColorTotals[selectedTile.color] = (newColorTotals[selectedTile.color] ?: 1) - 1
                newColorTotals[newColor] = (newColorTotals[newColor] ?: 0) + 1

                // Reset hazard timer for this pile
                val newTimers = currentState.hazardTimers.toMutableMap()
                newTimers[pileIndex] = System.currentTimeMillis()

                _state.value = currentState.copy(
                    piles = newPiles.map { it.toList() },
                    selectedTile = null,
                    selectedPileIndex = null,
                    colorTotals = newColorTotals,
                    hazardTimers = newTimers,
                    correctPopKey = currentState.correctPopKey + 1,
                    correctPopColor = homeColor,
                    correctPopPileIndex = pileIndex,
                    correctPopTile = selectedTile
                )
                _soundEvent.tryEmit(SoundEvent.CORRECT)
            } else {
                // Normal tile or final hazard mark: fully homed
                val newCount = currentState.tilesPlaced + 1
                val gameOver = newPiles.all { it.isEmpty() }
                val finalTime = if (gameOver) {
                    System.currentTimeMillis() - currentState.startTimeMillis
                } else {
                    currentState.elapsedMillis
                }

                val isNewHigh = if (gameOver) {
                    highScoreManager.addScore(finalTime)
                } else {
                    false
                }

                _state.value = currentState.copy(
                    piles = newPiles.map { it.toList() },
                    selectedTile = null,
                    selectedPileIndex = null,
                    tilesPlaced = newCount,
                    gameOver = gameOver,
                    elapsedMillis = finalTime,
                    highScores = if (gameOver) highScoreManager.getHighScores() else currentState.highScores,
                    isNewHighScore = isNewHigh,
                    correctPopKey = currentState.correctPopKey + 1,
                    correctPopColor = homeColor,
                    correctPopPileIndex = pileIndex,
                    correctPopTile = selectedTile
                )
                if (gameOver) {
                    timerJob?.cancel()
                    hazardJob?.cancel()
                    _soundEvent.tryEmit(SoundEvent.FANFARE)
                } else {
                    _soundEvent.tryEmit(SoundEvent.CORRECT)
                }
            }
        } else {
            // Wrong: tile stays on pile, no score change
            _state.value = currentState.copy(
                piles = newPiles.map { it.toList() },
                selectedTile = null,
                selectedPileIndex = null,
                wrongShakeKey = currentState.wrongShakeKey + 1,
                wrongShakePileIndex = pileIndex
            )
            _soundEvent.tryEmit(SoundEvent.WRONG)
        }
    }
}
