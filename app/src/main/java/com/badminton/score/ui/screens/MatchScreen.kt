package com.badminton.score.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badminton.score.data.*
import com.badminton.score.ui.*
import com.badminton.score.ui.theme.Palette

@Composable
fun MatchScreen(store: MatchStore, onExit: () -> Unit) {
    val state by store.state.collectAsState()
    val presentation by store.presentation.collectAsState()
    val toast by store.toast.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var cardSignal by remember { mutableStateOf<CardSignal?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // 顶栏
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircleIconButton(onClick = onExit, size = 40) {
                    Text("‹", color = Palette.blueBright, fontSize = 22.sp)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("第 ${state.currentGame} 局", color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(state.mode.title, color = Palette.textDim, fontSize = 11.sp)
                }
                UndoMenu(
                    store = store,
                    canUndoScore = store.canUndo && !store.isLocked,
                    canUndoRed = !store.isLocked && state.cardEvents.any { it.type == CardType.RED },
                    canUndoYellow = !store.isLocked && state.cardEvents.any { it.type == CardType.YELLOW },
                )
                CircleIconButton(onClick = { store.redo() }, enabled = store.canRedo, size = 40) {
                    Text("↻", color = if (store.canRedo) Palette.blueBright else Color.White.copy(alpha = 0.2f), fontSize = 18.sp)
                }
                CircleIconButton(onClick = { showLog = true }, size = 40) {
                    Text("☰", color = Palette.blueBright, fontSize = 16.sp)
                }
                CircleIconButton(onClick = { showSettings = true }, size = 40) {
                    Text("⚙", color = Palette.blueBright, fontSize = 17.sp)
                }
            }

            // 两个比分面板
            Side.entries.forEach { side ->
                val isRed = side == Side.RED
                ScorePanel(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    store = store,
                    state = state,
                    side = side,
                    isRed = isRed,
                    onCard = { type ->
                        store.addCard(type, side)
                        cardSignal = CardSignal(side, type)
                    },
                )
            }

            Spacer(Modifier.height(10.dp))
        }

        // 撤销提示
        toast?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier.padding(bottom = 90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) { Text(it.text, color = Color.White, fontSize = 13.sp) }
            }
            LaunchedEffect(it.token) {
                kotlinx.coroutines.delay(1400)
                store.clearToast()
            }
        }

        cardSignal?.let { signal ->
            CardSignalOverlay(
                sideName = state.name(signal.side),
                type = signal.type,
                onClose = { cardSignal = null },
            )
        }

        if (showSettings) {
            SettingsScreen(store = store, onBack = { showSettings = false })
        }
        if (showLog) {
            RallyLogDialog(state = state, onClose = { showLog = false })
        }
    }

    // 一局结束 / 整场结束
    when (val p = presentation) {
        is MatchPresentation.NextGame -> AlertDialog(
            onDismissRequest = {},
            title = { Text("${state.name(p.side)} 拿下第 ${p.game} 局", fontWeight = FontWeight.Bold) },
            text = { Text("${p.red} : ${p.blue}　大比分 ${state.gamesLine}") },
            confirmButton = { TextButton(onClick = { store.startNextGame() }) { Text("开始下一局") } },
        )
        is MatchPresentation.MatchResult -> AlertDialog(
            onDismissRequest = {},
            title = { Text("🏆 ${state.name(p.side)} 获胜", fontWeight = FontWeight.Bold) },
            text = { Text("大比分 ${state.gamesLine}　${state.historyLine}") },
            confirmButton = { TextButton(onClick = { store.rematch() }) { Text("再来一场") } },
            dismissButton = {
                TextButton(onClick = { store.dismissPresentation(); onExit() }) { Text("返回首页") }
            },
        )
        null -> {}
    }
}

@Composable
private fun ScorePanel(
    modifier: Modifier,
    store: MatchStore,
    state: MatchState,
    side: Side,
    isRed: Boolean,
    onCard: (CardType) -> Unit,
) {
    val points = state.points(side)
    val serving = state.server == side
    val isGamePoint = store.isGamePoint(side)
    val isMatchPoint = store.isMatchPoint(side)
    val locked = store.isLocked

    val borderColor by animateColorAsState(
        if (isGamePoint || isMatchPoint) Palette.bright(isRed) else Palette.accent(isRed).copy(alpha = 0.35f),
        label = "border",
    )
    val scoreScale by animateFloatAsState(if (serving) 1f else 0.94f, label = "scale")

    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(panelGradient(isRed))
            .border(1.5.dp, borderColor, RoundedCornerShape(26.dp))
            .clickable(enabled = !locked) { store.addPoint(side) },
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.name(side), color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("已胜 ${state.games(side)} 局", color = Palette.textDim, fontSize = 12.sp)
                }
                if (isMatchPoint || isGamePoint) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Palette.bright(isRed).copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            if (isMatchPoint) "赛点" else "局点",
                            color = Palette.bright(isRed), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (serving) {
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(Palette.accent(isRed).copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        val who = if (state.format == MatchFormat.DOUBLES) {
                            state.players(side)[state.serveIndex(side)] + " "
                        } else ""
                        Text("${who}发球 · ${state.serveBox}", color = Palette.bright(isRed), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "$points",
                    color = Palette.text,
                    fontSize = 88.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.scale(scoreScale),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👆 轻点面板 · 加一分", color = Palette.textFaint, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardButton(
                            side = side,
                            type = CardType.RED,
                            count = state.cardCount(CardType.RED, side),
                            enabled = !locked,
                            onClick = { onCard(CardType.RED) },
                        )
                        CardButton(
                            side = side,
                            type = CardType.YELLOW,
                            count = state.cardCount(CardType.YELLOW, side),
                            enabled = !locked,
                            onClick = { onCard(CardType.YELLOW) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // 减分 = 撤回上一次加分
                    CircleIconButton(onClick = { store.removePoint(side) }, enabled = store.canUndo && !locked, size = 40) {
                        Text(
                            "−",
                            color = if (store.canUndo && !locked) Palette.text else Color.White.copy(alpha = 0.2f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private data class CardSignal(val side: Side, val type: CardType)

@Composable
private fun UndoMenu(
    store: MatchStore,
    canUndoScore: Boolean,
    canUndoRed: Boolean,
    canUndoYellow: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val enabled = canUndoScore || canUndoRed || canUndoYellow

    Box {
        CircleIconButton(onClick = { expanded = true }, enabled = enabled, size = 40) {
            Text(
                "↺",
                color = if (enabled) Palette.blueBright else Color.White.copy(alpha = 0.2f),
                fontSize = 18.sp,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("撤销分数") },
                enabled = canUndoScore,
                onClick = {
                    expanded = false
                    store.undo()
                },
            )
            DropdownMenuItem(
                text = { Text("撤销红牌") },
                enabled = canUndoRed,
                onClick = {
                    expanded = false
                    store.undoCard(CardType.RED)
                },
            )
            DropdownMenuItem(
                text = { Text("撤销黄牌") },
                enabled = canUndoYellow,
                onClick = {
                    expanded = false
                    store.undoCard(CardType.YELLOW)
                },
            )
        }
    }
}

@Composable
private fun CardSignalOverlay(sideName: String, type: CardType, onClose: () -> Unit) {
    val color = Palette.card(type)
    val foreground = if (type == CardType.YELLOW) Color.Black.copy(alpha = 0.86f) else Color.White

    Box(
        Modifier
            .fillMaxSize()
            .background(color)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {},
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = if (type == CardType.YELLOW) 0.30f else 0.18f), Color.Transparent),
                        radius = 900f,
                    )
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(foreground.copy(alpha = 0.12f))
                        .border(1.dp, foreground.copy(alpha = 0.24f), RoundedCornerShape(999.dp))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("×", color = foreground, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("▯", color = foreground, fontSize = 84.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(sideName, color = foreground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(type.title, color = foreground, fontSize = 52.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RallyLogDialog(state: MatchState, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Column {
                Text("逐分记录", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardCountTag(CardType.RED, state.cardCount(CardType.RED, Side.RED) + state.cardCount(CardType.RED, Side.BLUE))
                    CardCountTag(CardType.YELLOW, state.cardCount(CardType.YELLOW, Side.RED) + state.cardCount(CardType.YELLOW, Side.BLUE))
                }
            }
        },
        text = {
            if (state.log.isEmpty()) {
                Text("还没有得分")
            } else {
                Column(
                    Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                ) {
                    state.log.reversed().forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(state.name(e.side), color = Palette.bright(e.side == Side.RED), fontSize = 14.sp)
                            Text(e.text, color = Palette.textDim, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } },
    )
}
