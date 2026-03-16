package com.example.tilemaster

data class Tile(
    val color: TileColor,
    val isBomb: Boolean = false,
    val isHazard: Boolean = false,
    val hazardMarks: Int = 0  // how many times it must be homed (1-3)
)
