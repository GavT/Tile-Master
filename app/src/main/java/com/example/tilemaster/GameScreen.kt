package com.example.tilemaster

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

fun formatCountdown(millis: Long): String {
    val clamped = millis.coerceAtLeast(0L)
    val totalSeconds = clamped / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centiseconds = (clamped % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, centiseconds)
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centiseconds = (millis % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, centiseconds)
}

// Candy Crush palette
private val BgGradientTop = Color(0xFF1A0533)
private val BgGradientBottom = Color(0xFF0D47A1)
private val AccentGold = Color(0xFFFFD740)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFFB39DDB)
private val PileCardBg = Color(0xFF2A1B3D)
private val PileCardEmpty = Color(0xFF1A1028)
private val BombOrange = Color(0xFFFF6D00)
private val BombHighlight = Color(0xFFFFAB40)
private val BombShadow = Color(0xFFBF360C)

data class FlyingTileInfo(
    val tile: Tile,
    val startPos: Offset,
    val endPos: Offset,
    val key: Int
)

data class ExplodingTileInfo(
    val tile: Tile,
    val pileIndex: Int,
    val startPos: Offset,      // pile position
    val flyOutPos: Offset,     // outward burst position
    val homePos: Offset        // color home position
)

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsState()
    val soundManager = remember { SoundManager() }
    DisposableEffect(Unit) {
        onDispose { soundManager.release() }
    }

    // Position tracking for fly animation
    val pilePositions = remember { mutableStateMapOf<Int, Offset>() }
    val homePositions = remember { mutableStateMapOf<TileColor, Offset>() }
    var flyingTile by remember { mutableStateOf<FlyingTileInfo?>(null) }
    var explodingTiles by remember { mutableStateOf<List<ExplodingTileInfo>>(emptyList()) }
    var explosionKey by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.soundEvent.collect { event ->
            when (event) {
                SoundEvent.CLICK -> soundManager.playClick()
                SoundEvent.CORRECT -> soundManager.playSuckHome()
                SoundEvent.WRONG -> soundManager.playWrong()
                SoundEvent.FANFARE -> soundManager.playFanfare()
                SoundEvent.EXPLOSION -> soundManager.playExplosion()
                SoundEvent.HAZARD_SPREAD -> soundManager.playWrong()
                SoundEvent.COUNTDOWN_BEEP -> soundManager.playCountdownBeep()
                SoundEvent.TIME_UP -> soundManager.playTimeUp()
                SoundEvent.COLOR_COMPLETE -> soundManager.playThunkComplete()
            }
        }
    }

    // Trigger fly animation on correct placement
    LaunchedEffect(state.correctPopKey) {
        val popKey = state.correctPopKey
        val popPileIndex = state.correctPopPileIndex
        val popColor = state.correctPopColor
        val popTile = state.correctPopTile

        if (popKey > 0 && popPileIndex >= 0 && popColor != null && popTile != null) {
            val start = pilePositions[popPileIndex]
            val end = homePositions[popColor]
            if (start != null && end != null) {
                flyingTile = FlyingTileInfo(
                    tile = popTile,
                    startPos = start,
                    endPos = end,
                    key = popKey
                )
            }
        }
    }

    // Trigger bomb explosion animation
    LaunchedEffect(state.bombExplosionKey) {
        val key = state.bombExplosionKey
        val bombPile = state.bombExplosionPileIndex
        val tiles = state.bombExplodedTiles

        if (key > 0 && bombPile >= 0 && tiles.isNotEmpty()) {
            val bombPos = pilePositions[bombPile] ?: return@LaunchedEffect
            val infos = tiles.mapNotNull { (pileIdx, tile) ->
                val startPos = pilePositions[pileIdx] ?: return@mapNotNull null
                val homePos = homePositions[tile.color] ?: return@mapNotNull null
                // Calculate fly-out direction: away from bomb center
                val dx = startPos.x - bombPos.x
                val dy = startPos.y - bombPos.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val flyOutOffset = 150f // pixels to fly outward
                val flyOutPos = Offset(
                    startPos.x + (dx / dist) * flyOutOffset,
                    startPos.y + (dy / dist) * flyOutOffset
                )
                ExplodingTileInfo(
                    tile = tile,
                    pileIndex = pileIdx,
                    startPos = startPos,
                    flyOutPos = flyOutPos,
                    homePos = homePos
                )
            }
            explodingTiles = infos
            explosionKey = key
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgGradientTop, BgGradientBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "TileMaster",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0xFF880E4F),
                        offset = Offset(2f, 4f),
                        blurRadius = 6f
                    )
                ),
                color = AccentGold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status bar
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF311B55)),
                border = BorderStroke(2.dp, Color(0xFF7C4DFF).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lvl ${state.level}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Text(
                        text = "${state.tilesPlaced} / ${state.totalTiles}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold
                    )
                    val timerColor = when {
                        !state.timerStarted -> Color(0xFF80D8FF)
                        state.remainingMillis <= 10_000L -> Color(0xFFFF1744) // Red in last 10s
                        state.remainingMillis <= 20_000L -> Color(0xFFFFAB00) // Amber in last 20s
                        else -> Color(0xFF80D8FF)
                    }
                    Text(
                        text = formatCountdown(state.remainingMillis),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = timerColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Instruction text
            val instructionText = when {
                state.selectedTile != null && state.selectedTile!!.isHazard ->
                    "Tap the ${state.selectedTile!!.color.displayName} home! (${state.selectedTile!!.hazardMarks} left)"
                state.selectedTile != null ->
                    "Tap the ${state.selectedTile!!.color.displayName} home!"
                else -> "Tap a tile to pick it up"
            }
            Text(
                text = instructionText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3x3 grid of piles
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in 0 until 3) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (col in 0 until 3) {
                            val pileIndex = row * 3 + col
                            val staggerDelay = (row * 3 + col) * 60
                            Box(
                                modifier = Modifier
                                    .size(95.dp)
                                    .onGloballyPositioned { coords ->
                                        val pos = coords.positionInRoot()
                                        val newOffset = Offset(
                                            pos.x + coords.size.width / 2f,
                                            pos.y + coords.size.height / 2f
                                        )
                                        if (pilePositions[pileIndex] != newOffset) {
                                            pilePositions[pileIndex] = newOffset
                                        }
                                    }
                            ) {
                                PileView(
                                    pile = state.piles.getOrElse(pileIndex) { emptyList() },
                                    isSelected = state.selectedPileIndex == pileIndex,
                                    shakeKey = if (state.wrongShakePileIndex == pileIndex) state.wrongShakeKey else 0,
                                    entranceDelay = staggerDelay,
                                    onClick = { viewModel.selectTile(pileIndex) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Memoize tile counts — only recomputes when piles actually change, not on timer ticks
            val remainingPerColor = remember(state.piles) {
                TileColor.entries.associateWith { color ->
                    state.piles.sumOf { pile -> pile.count { !it.isBomb && it.color == color } }
                }
            }

            // Homes
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TileColor.entries.forEach { homeColor ->
                    val totalForColor = state.colorTotals[homeColor] ?: 0
                    val remainingForColor = remainingPerColor[homeColor] ?: 0
                    val placedForColor = totalForColor - remainingForColor
                    val isCompleted = remainingForColor == 0 && totalForColor > 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.75f)
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInRoot()
                                val newOffset = Offset(
                                    pos.x + coords.size.width / 2f,
                                    pos.y + coords.size.height / 2f
                                )
                                if (homePositions[homeColor] != newOffset) {
                                    homePositions[homeColor] = newOffset
                                }
                            }
                    ) {
                        HomeView(
                            homeColor = homeColor,
                            isHighlighted = state.selectedTile != null,
                            popKey = if (state.correctPopColor == homeColor) state.correctPopKey else 0,
                            placedCount = placedForColor,
                            totalCount = totalForColor,
                            isCompleted = isCompleted,
                            spinKey = if (state.colorCompletedColor == homeColor) state.colorCompletedKey else 0,
                            onClick = { viewModel.placeInHome(homeColor) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Flying tile overlay
        flyingTile?.let { info ->
            FlyingTileOverlay(
                info = info,
                onComplete = { flyingTile = null }
            )
        }

        // Bomb explosion overlay
        if (explodingTiles.isNotEmpty()) {
            ExplodingTilesOverlay(
                tiles = explodingTiles,
                key = explosionKey,
                onComplete = { explodingTiles = emptyList() }
            )
        }
    }

    // Game over dialog (win or time-up loss)
    if (state.gameOver) {
        if (state.timeUp) {
            // Time-up loss dialog
            AlertDialog(
                onDismissRequest = { },
                containerColor = Color(0xFF1A0533),
                titleContentColor = Color(0xFFFF1744),
                textContentColor = TextPrimary,
                title = {
                    Text(
                        text = "Time's Up!",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "You ran out of time on Level ${state.level}",
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${state.tilesPlaced} / ${state.totalTiles} tiles placed",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = AccentGold
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.retryLevel() }) {
                        Text(
                            "Try Again",
                            color = AccentGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.startNewGame() }) {
                        Text(
                            "Restart",
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            )
        } else {
            val isFinalLevel = state.level >= state.maxLevel
            // Win dialog
            AlertDialog(
                onDismissRequest = { },
                containerColor = Color(0xFF1A0533),
                titleContentColor = AccentGold,
                textContentColor = TextPrimary,
                title = {
                    Text(
                        text = if (isFinalLevel) "You Beat TileMaster!" else "Level ${state.level} Complete!",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isFinalLevel) "Congratulations! You completed all ${state.maxLevel} levels!" else "All tiles are home!",
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatCountdown(state.remainingMillis) + " remaining",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF80D8FF)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Completed in ${formatTime(state.elapsedMillis)}",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = TextSecondary
                        )
                    }
                },
                confirmButton = {
                    if (isFinalLevel) {
                        TextButton(onClick = { viewModel.startNewGame() }) {
                            Text(
                                "Play Again",
                                color = AccentGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        TextButton(onClick = { viewModel.startNextLevel() }) {
                            Text(
                                "Next Level",
                                color = AccentGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                },
                dismissButton = {
                    if (!isFinalLevel) {
                        TextButton(onClick = { viewModel.startNewGame() }) {
                            Text(
                                "Restart",
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun FlyingTileOverlay(
    info: FlyingTileInfo,
    onComplete: () -> Unit
) {
    val progress = remember(info.key) { Animatable(0f) }
    val density = LocalDensity.current
    val tileSizePx = with(density) { 48.dp.toPx() }
    val halfTile = tileSizePx / 2f

    LaunchedEffect(info.key) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        onComplete()
    }

    val p = progress.value
    val x = info.startPos.x + (info.endPos.x - info.startPos.x) * p
    val baseY = info.startPos.y + (info.endPos.y - info.startPos.y) * p
    // Parabolic arc: peaks at p=0.5
    val arcHeight = with(density) { 100.dp.toPx() }
    val arc = arcHeight * 4f * p * (1f - p)
    val y = baseY - arc
    val tileScale = 1f - 0.35f * p

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset((x - halfTile).toInt(), (y - halfTile).toInt()) }
                .scale(tileScale)
        ) {
            if (info.tile.isHazard) {
                CandyTile(
                    color = info.tile.color.color,
                    highlight = info.tile.color.highlight,
                    shadow = info.tile.color.shadow,
                    size = 48,
                    tileShape = info.tile.color.tileShape
                ) {
                    Text(text = "\uD83C\uDFED", fontSize = 18.sp)
                }
            } else {
                CandyTile(
                    color = info.tile.color.color,
                    highlight = info.tile.color.highlight,
                    shadow = info.tile.color.shadow,
                    size = 48,
                    tileShape = info.tile.color.tileShape
                )
            }
        }
    }
}

@Composable
fun ExplodingTilesOverlay(
    tiles: List<ExplodingTileInfo>,
    key: Int,
    onComplete: () -> Unit
) {
    // Phase 1: fly outward (0..1), Phase 2: pause, Phase 3: fly to home (0..1)
    val phase1 = remember(key) { Animatable(0f) }
    val phase3 = remember(key) { Animatable(0f) }
    val density = LocalDensity.current
    val tileSizePx = with(density) { 40.dp.toPx() }
    val halfTile = tileSizePx / 2f

    LaunchedEffect(key) {
        phase1.snapTo(0f)
        phase3.snapTo(0f)
        // Phase 1: fly outward
        phase1.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
        // Phase 2: pause
        delay(200)
        // Phase 3: fly to home with arc
        phase3.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        onComplete()
    }

    val p1 = phase1.value
    val p3 = phase3.value

    Box(modifier = Modifier.fillMaxSize()) {
        for (info in tiles) {
            val x: Float
            val y: Float
            val tileScale: Float

            if (p3 > 0f) {
                // Phase 3: flying from flyOutPos to homePos with arc
                x = info.flyOutPos.x + (info.homePos.x - info.flyOutPos.x) * p3
                val baseY = info.flyOutPos.y + (info.homePos.y - info.flyOutPos.y) * p3
                val arcHeight = with(density) { 80.dp.toPx() }
                val arc = arcHeight * 4f * p3 * (1f - p3)
                y = baseY - arc
                tileScale = 1f - 0.35f * p3
            } else {
                // Phase 1: flying outward from pile
                x = info.startPos.x + (info.flyOutPos.x - info.startPos.x) * p1
                y = info.startPos.y + (info.flyOutPos.y - info.startPos.y) * p1
                tileScale = 1f + 0.15f * p1 // slight scale up on burst
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset((x - halfTile).toInt(), (y - halfTile).toInt()) }
                    .scale(tileScale)
            ) {
                CandyTile(
                    color = info.tile.color.color,
                    highlight = info.tile.color.highlight,
                    shadow = info.tile.color.shadow,
                    size = 40,
                    tileShape = info.tile.color.tileShape
                )
            }
        }
    }
}

@Composable
fun CandyTile(
    color: Color,
    highlight: Color,
    shadow: Color,
    size: Int,
    tileShape: Shape = RoundedCornerShape((size / 4).dp),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    Box(modifier = modifier) {
        // Shadow layer
        Box(
            modifier = Modifier
                .size(size.dp)
                .offset(y = 2.dp)
                .clip(tileShape)
                .background(shadow)
        )
        // Main candy body
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(tileShape)
                .background(color)
        ) {
            // Glossy highlight at top-left
            Box(
                modifier = Modifier
                    .size((size * 0.4).dp)
                    .offset(x = (size * 0.12).dp, y = (size * 0.08).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                highlight.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                content()
            }
        }
    }
}

@Composable
fun PileView(
    pile: List<Tile>,
    isSelected: Boolean,
    shakeKey: Int,
    entranceDelay: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(entranceDelay.toLong())
        visible = true
    }

    // Breathing pulse only when selected (avoids running 9 infinite animations)
    val pulseScale = if (isSelected) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        infiniteTransition.animateFloat(
            initialValue = 1.06f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        ).value
    } else {
        1f
    }

    // Shake animation on wrong match
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey > 0) {
            repeat(3) {
                shakeOffset.animateTo(12f, tween(40))
                shakeOffset.animateTo(-12f, tween(40))
            }
            shakeOffset.animateTo(0f, tween(40))
        }
    }

    val baseScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.93f
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scale"
    )
    val finalScale = if (isSelected) pulseScale else baseScale  // pulseScale already 1f when not selected

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) AccentGold else Color(0xFF7C4DFF).copy(alpha = 0.4f),
        label = "borderColor"
    )

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(animationSpec = tween(200))
    ) {
        Card(
            modifier = modifier
                .scale(finalScale)
                .offset { IntOffset(shakeOffset.value.toInt(), 0) }
                .shadow(
                    elevation = if (isSelected) 12.dp else 4.dp,
                    shape = shape,
                    ambientColor = if (isSelected) AccentGold.copy(alpha = 0.3f) else Color.Black,
                    spotColor = if (isSelected) AccentGold.copy(alpha = 0.5f) else Color.Black
                )
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = pile.isNotEmpty()
                ) { onClick() },
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = if (pile.isNotEmpty()) PileCardBg else PileCardEmpty
            ),
            border = BorderStroke(
                width = if (isSelected) 3.dp else 1.dp,
                color = borderColor
            )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (pile.isNotEmpty()) {
                    val topTile = pile.last()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (topTile.isBomb) {
                            CandyTile(
                                color = BombOrange,
                                highlight = BombHighlight,
                                shadow = BombShadow,
                                size = 48
                            ) {
                                Text(
                                    text = "\uD83D\uDCA3",
                                    fontSize = 22.sp
                                )
                            }
                        } else if (topTile.isHazard) {
                            CandyTile(
                                color = topTile.color.color,
                                highlight = topTile.color.highlight,
                                shadow = topTile.color.shadow,
                                size = 48,
                                tileShape = topTile.color.tileShape
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "\uD83C\uDFED",
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = "!".repeat(topTile.hazardMarks),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        lineHeight = 10.sp
                                    )
                                }
                            }
                        } else {
                            CandyTile(
                                color = topTile.color.color,
                                highlight = topTile.color.highlight,
                                shadow = topTile.color.shadow,
                                size = 48,
                                tileShape = topTile.color.tileShape
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${pile.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }
                } else {
                    Text(
                        text = "Empty",
                        fontSize = 11.sp,
                        color = Color(0xFF4A3660)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeView(
    homeColor: TileColor,
    isHighlighted: Boolean,
    popKey: Int,
    placedCount: Int,
    totalCount: Int,
    isCompleted: Boolean,
    spinKey: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Pop animation on correct placement
    val popScale = remember { Animatable(1f) }
    LaunchedEffect(popKey) {
        if (popKey > 0) {
            // Small delay so the fly animation lands first
            delay(350)
            popScale.animateTo(
                1.3f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
            popScale.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    // Spin animation when color is completed
    val spinRotation = remember { Animatable(0f) }
    LaunchedEffect(spinKey) {
        if (spinKey > 0) {
            delay(400) // wait for fly + pop to finish
            spinRotation.snapTo(0f)
            spinRotation.animateTo(
                360f,
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            )
        }
    }

    // Dim alpha — animates smoothly between bright and dim
    val dimAlpha by animateFloatAsState(
        targetValue = if (isCompleted) 0.4f else 1f,
        animationSpec = tween(500),
        label = "dimAlpha"
    )

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "homePress"
    )
    val combinedScale = popScale.value * pressScale

    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) AccentGold else homeColor.color.copy(alpha = 0.6f),
        label = "homeBorderColor"
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (isHighlighted) 0.35f else 0.15f,
        label = "homeBgAlpha"
    )

    Card(
        modifier = modifier
            .scale(combinedScale)
            .graphicsLayer {
                rotationY = spinRotation.value
                alpha = dimAlpha
            }
            .shadow(
                elevation = if (isHighlighted) 8.dp else 2.dp,
                shape = shape,
                ambientColor = homeColor.color.copy(alpha = 0.3f),
                spotColor = homeColor.color.copy(alpha = 0.5f)
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = homeColor.color.copy(alpha = bgAlpha)
        ),
        border = BorderStroke(
            width = if (isHighlighted) 3.dp else 2.dp,
            color = borderColor
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            CandyTile(
                color = homeColor.color,
                highlight = homeColor.highlight,
                shadow = homeColor.shadow,
                size = 36,
                tileShape = homeColor.tileShape
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$placedCount/$totalCount",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}
