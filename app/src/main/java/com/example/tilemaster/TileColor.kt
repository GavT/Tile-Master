package com.example.tilemaster

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

val TriangleShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

val DiamondShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width / 2f, size.height)
            lineTo(0f, size.height / 2f)
            close()
        }
        return Outline.Generic(path)
    }
}

enum class TileColor(
    val displayName: String,
    val color: Color,
    val highlight: Color,
    val shadow: Color,
    val tileShape: Shape
) {
    RED("Red", Color(0xFFFF1744), Color(0xFFFF8A80), Color(0xFFC62828), CircleShape),
    GREEN("Green", Color(0xFF00E676), Color(0xFF69F0AE), Color(0xFF2E7D32), TriangleShape),
    BLUE("Blue", Color(0xFF2979FF), Color(0xFF82B1FF), Color(0xFF1565C0), RoundedCornerShape(4.dp)),
    YELLOW("Yellow", Color(0xFFFFAB00), Color(0xFFFFD54F), Color(0xFFF57F17), DiamondShape)
}
