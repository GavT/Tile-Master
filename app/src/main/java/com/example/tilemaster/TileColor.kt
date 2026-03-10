package com.example.tilemaster

import androidx.compose.ui.graphics.Color

enum class TileColor(
    val displayName: String,
    val color: Color,
    val highlight: Color,
    val shadow: Color
) {
    RED("Red", Color(0xFFFF1744), Color(0xFFFF8A80), Color(0xFFC62828)),
    GREEN("Green", Color(0xFF00E676), Color(0xFF69F0AE), Color(0xFF2E7D32)),
    BLUE("Blue", Color(0xFF2979FF), Color(0xFF82B1FF), Color(0xFF1565C0)),
    YELLOW("Yellow", Color(0xFFFFAB00), Color(0xFFFFD54F), Color(0xFFF57F17))
}
