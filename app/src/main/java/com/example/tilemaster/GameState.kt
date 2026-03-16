package com.example.tilemaster

data class GameState(
    val piles: List<List<Tile>> = emptyList(),
    val selectedTile: Tile? = null,
    val selectedPileIndex: Int? = null,
    val tilesPlaced: Int = 0,
    val totalTiles: Int = 45,
    val level: Int = 1,
    val gameOver: Boolean = false,
    val timeUp: Boolean = false, // true = lost (ran out of time), false = won (cleared all tiles)
    val timerStarted: Boolean = false,
    val elapsedMillis: Long = 0L,
    val startTimeMillis: Long = 0L,
    val timeLimitMillis: Long = 60_000L,
    val hazardSpreadMs: Long = 2000L,
    val maxLevel: Int = 10,
    val colorTotals: Map<TileColor, Int> = emptyMap(),
    // Animation triggers
    val wrongShakeKey: Int = 0,
    val wrongShakePileIndex: Int = -1,
    val correctPopKey: Int = 0,
    val correctPopColor: TileColor? = null,
    val correctPopPileIndex: Int = -1,
    val correctPopTile: Tile? = null,
    // Hazard tile timers: pileIndex -> timestamp when hazard became top tile
    val hazardTimers: Map<Int, Long> = emptyMap(),
    // Bomb explosion animation
    val bombExplosionKey: Int = 0,
    val bombExplosionPileIndex: Int = -1,
    val bombExplodedTiles: List<Pair<Int, Tile>> = emptyList() // (pileIndex, tile) pairs
) {
    val remainingMillis: Long get() = (timeLimitMillis - elapsedMillis).coerceAtLeast(0L)
}

enum class SoundEvent {
    CLICK, CORRECT, WRONG, FANFARE, EXPLOSION, HAZARD_SPREAD, COUNTDOWN_BEEP, TIME_UP
}
