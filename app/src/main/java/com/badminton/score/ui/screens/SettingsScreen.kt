package com.badminton.score.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badminton.score.data.*
import com.badminton.score.ui.*
import com.badminton.score.ui.theme.Palette

@Composable
fun SettingsScreen(store: MatchStore, onBack: () -> Unit) {
    val state by store.state.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(onClick = onBack, size = 40) {
                Text("‹", color = Palette.blueBright, fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text("赛制与设置", color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column {
            SectionTitle("计分模式")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ScoringMode.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (m == state.mode) Palette.blue.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
                            .border(1.dp, if (m == state.mode) Palette.blue.copy(alpha = 0.5f) else Palette.lineSoft, RoundedCornerShape(16.dp))
                            .clickable(enabled = m != state.mode) { store.changeMode(m) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.title, color = Palette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(m.subtitle, color = Palette.textDim, fontSize = 12.sp)
                        }
                        if (m == state.mode) Text("✓", color = Palette.blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (state.mode == ScoringMode.CUSTOM) CustomRulesPanel(store)
            }
        }

        Column {
            SectionTitle("规则说明")
            GlassCard(Modifier.fillMaxWidth()) {
                val r = state.rules
                listOf(
                    "每局 ${r.pointsToWin} 分，" + if (r.gamesToWin == 1) "一局定胜负" else "赢下 ${r.gamesToWin} 局者胜",
                    when {
                        r.deuceAt != null && r.cap != null -> "${r.deuceAt} 平后需净胜 2 分，${r.cap} 分封顶"
                        r.deuceAt != null -> "${r.deuceAt} 平后需净胜 2 分，无封顶"
                        else -> "先到 ${r.pointsToWin} 分者胜"
                    },
                    if (r.rallyPoint) "每球得分制：得分方获得发球权" else "发球得分制：只有发球方得分才计分",
                    "每局结束后交换场地，胜方下一局先发球",
                ).forEach {
                    Text("· $it", color = Palette.textDim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }

        Column {
            SectionTitle("本局先发球")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Side.entries.forEach { side ->
                    val isRed = side == Side.RED
                    val sel = state.server == side
                    Box(
                        Modifier.weight(1f).height(46.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (sel) sideGradient(isRed) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f))))
                            .clickable { store.setFirstServer(side) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(state.name(side), color = if (sel) Color.White else Palette.textDim, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        PlayersPanel(store, state)

        // 破坏性操作：显式染红（顶层有主色，不写颜色会是蓝的）
        Box(
            Modifier.fillMaxWidth().height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.destructive.copy(alpha = 0.14f))
                .border(1.dp, Palette.destructive.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .clickable { confirmReset = true },
            contentAlignment = Alignment.Center,
        ) {
            Text("重新开始 / 返回首页", color = Palette.destructive, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (confirmReset) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("要清空当前比分并重新开始吗？", fontWeight = FontWeight.Bold) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { store.rematch(); confirmReset = false }) {
                    Text("重新开始", color = Palette.destructive)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { store.clearPersisted(); confirmReset = false; onBack() }) {
                    Text("返回首页", color = Palette.destructive)
                }
            },
        )
    }
}
