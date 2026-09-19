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
fun HomeScreen(
    store: MatchStore,
    prefs: PrefsSettings,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val state by store.state.collectAsState()
    var mode by remember { mutableStateOf(prefs.selectedMode) }
    val history = MatchHistoryStore.shared
    val historyItems by history.records.collectAsState()
    val hasProgress = state.hasUnfinished

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // 顶部
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("羽毛球计分器", color = Palette.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            CircleIconButton(onClick = onOpenSettings) {
                Text("⚙", fontSize = 20.sp, color = Palette.blueBright)
            }
        }

        Hero()

        // 计分模式
        Column {
            SectionTitle("计分模式")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ScoringMode.entries.forEach { m ->
                    ModeCard(m, m == mode) {
                        if (m != mode) {
                            mode = m
                            prefs.selectedMode = m
                            store.changeMode(m)
                        }
                    }
                }
                if (mode == ScoringMode.CUSTOM) {
                    CustomRulesPanel(store)
                }
            }
        }

        // 上场人数
        Column {
            SectionTitle("上场人数")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MatchFormat.entries.forEach { f ->
                    val selected = state.format == f
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) sideGradient(false) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f))))
                            .clickable(enabled = !selected) { store.setFormat(f) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(f.title, color = if (selected) Color.White else Palette.textDim, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(f.subtitle, color = (if (selected) Color.White else Palette.textDim).copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // 队员名
        PlayersPanel(store, state)

        // 先发球
        Column {
            SectionTitle("开局先发球")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Side.entries.forEach { side ->
                    val isRed = side == Side.RED
                    val selected = state.server == side
                    Box(
                        Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) sideGradient(isRed) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f))))
                            .clickable { store.setFirstServer(side) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            state.name(side),
                            color = if (selected) Color.White else Palette.textDim,
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (hasProgress) {
            // 上一场没打完：给一个「继续」入口，和「开始比赛」区分开
            GlassCard(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏱", fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("继续上一场比赛", color = Palette.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("${state.redPoints} : ${state.bluePoints}　第 ${state.currentGame} 局", color = Palette.textDim, fontSize = 12.sp)
                    }
                    Text("  ›", color = Palette.textFaint, fontSize = 16.sp)
                }
            }
        }

        PrimaryButton("▶  开始比赛") {
            // 「开始比赛」永远从头开始，不然同一个赛制没法连着用两次
            // （之前不重置，而 changeMode(同模式) 是空操作，比分就留着了）
            store.changeMode(mode)
            store.rematch()
            onStart()
        }

        // 对战记录入口
        GlassCard(onClick = onOpenRecords, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📋", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text("对战记录", color = Palette.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (historyItems.isNotEmpty()) {
                    Text("${history.redWins} : ${history.blueWins}", color = Palette.textDim, fontSize = 15.sp)
                }
                Text("  ›", color = Palette.textFaint, fontSize = 16.sp)
            }
        }

        Text(
            mode.detail,
            color = Palette.textFaint,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 上一场是否还有没打完的比分。 */
private val MatchState.hasUnfinished: Boolean
    get() = (redPoints > 0 || bluePoints > 0 || gameScores.isNotEmpty()) && !isMatchOver

@Composable
private fun Hero() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.radialGradient(listOf(Palette.blue.copy(alpha = 0.35f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) { Text("🏸", fontSize = 52.sp) }
        Spacer(Modifier.height(12.dp))
        Text("红蓝对抗 · 规则内置 · 一指计分", color = Palette.textDim, fontSize = 13.sp)
    }
}

@Composable
private fun ModeCard(mode: ScoringMode, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Palette.blue.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (selected) Palette.blue.copy(alpha = 0.55f) else Palette.lineSoft, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(mode.title, color = Palette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(mode.subtitle, color = Palette.textDim, fontSize = 12.sp)
        }
        if (selected) Text("✓", color = Palette.blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** 自定义规则编辑面板，就地展开。 */
@Composable
fun CustomRulesPanel(store: MatchStore) {
    val state by store.state.collectAsState()
    val r = state.rules

    GlassCard(Modifier.fillMaxWidth()) {
        RuleStepperRow("每局几分", r.pointsToWin, BadmintonRules.pointsRange, "分") {
            store.setCustomRules(it, r.capBonus, r.maximumGames)
        }
        Spacer(Modifier.height(10.dp))
        RuleStepperRow(
            if (r.cap == null) "不封顶" else "封顶",
            r.cap ?: 0,
            (r.pointsToWin + 1)..(r.pointsToWin + 20),
            "分",
            enabled = r.cap != null,
        ) { store.setCustomRules(r.pointsToWin, it - r.pointsToWin, r.maximumGames) }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("开启封顶", color = Palette.textDim, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.Switch(
                checked = r.cap != null,
                onCheckedChange = { on -> store.setCustomRules(r.pointsToWin, if (on) 9 else null, r.maximumGames) },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Palette.blue),
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("打几局", color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BadmintonRules.gameOptions.forEach { n ->
                    val sel = r.maximumGames == n
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) sideGradient(false) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))))
                            .clickable(enabled = !sel) { store.setCustomRules(r.pointsToWin, r.capBonus, n) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(if (n == 1) "一局" else "$n 局", color = if (sel) Color.White else Palette.textDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        val summary = buildString {
            append(if (r.maximumGames == 1) "一局定胜负" else "${r.maximumGames} 局 ${r.gamesToWin} 胜")
            append(" · ${r.pointsToWin} 分")
            r.deuceAt?.let { append(" · $it 平后净胜 2 分") }
            append(if (r.cap == null) " · 无封顶" else " · ${r.cap} 分封顶")
        }
        Text(summary, color = Palette.blueBright, fontSize = 12.sp)
    }
}

@Composable
private fun RuleStepperRow(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    enabled: Boolean = true,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepBtn("−", enabled && value > range.first) { onChange(value - 1) }
            Text(
                "$value $unit",
                color = Palette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 62.dp).padding(horizontal = 6.dp),
            )
            StepBtn("+", enabled && value < range.last) { onChange(value + 1) }
        }
    }
}

@Composable
private fun StepBtn(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp, 36.dp).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = if (enabled) Palette.blueBright else Color.White.copy(alpha = 0.2f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** 队员名编辑。 */
@Composable
fun PlayersPanel(store: MatchStore, state: MatchState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(if (state.format == MatchFormat.DOUBLES) "队员" else "名字")
        Side.entries.forEach { side ->
            val isRed = side == Side.RED
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    if (isRed) "红方" else "蓝方",
                    color = Palette.bright(isRed), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                state.players(side).forEachIndexed { i, name ->
                    var draft by remember(name) { mutableStateOf(name) }
                    androidx.compose.material3.OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(if (i == 0) side.defaultName else "队友", color = Palette.textFaint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Palette.text,
                            unfocusedTextColor = Palette.text,
                            focusedBorderColor = Palette.accent(isRed),
                            unfocusedBorderColor = Palette.line,
                        ),
                    )
                    // 失焦或改名时写回
                    LaunchedEffect(draft) {
                        if (draft != name) store.renamePlayer(side, i, draft)
                    }
                }
            }
        }
    }
}
