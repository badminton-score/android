package com.badminton.score.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
            CircleIconButton(onClick = { store.undo() }, enabled = store.canUndo, size = 40) {
                Text("↺", color = if (store.canUndo) Palette.blueBright else Color.White.copy(alpha = 0.2f), fontSize = 18.sp)
            }
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
                store = store, state = state, side = side, isRed = isRed,
            )
        }

        Spacer(Modifier.height(10.dp))
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

    if (showSettings) {
        SettingsScreen(store = store, onBack = { showSettings = false })
    }
    if (showLog) {
        RallyLogDialog(state = state, onClose = { showLog = false })
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
}

@Composable
private fun ScorePanel(
    modifier: Modifier,
    store: MatchStore,
    state: MatchState,
    side: Side,
    isRed: Boolean,
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
                // 减分 = 撤回上一次加分
                CircleIconButton(onClick = { store.removePoint(side) }, enabled = store.canUndo, size = 40) {
                    Text("−", color = if (store.canUndo) Palette.text else Color.White.copy(alpha = 0.2f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RallyLogDialog(state: MatchState, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("逐分记录", fontWeight = FontWeight.Bold) },
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
