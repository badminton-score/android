package com.badminton.score.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badminton.score.data.MatchHistoryStore
import com.badminton.score.data.MatchRecord
import com.badminton.score.data.Side
import com.badminton.score.ui.CircleIconButton
import com.badminton.score.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordsScreen(onClose: () -> Unit) {
    val history = MatchHistoryStore.shared
    val records by history.records.collectAsState()
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    val fmt = remember { SimpleDateFormat("M月d日 HH:mm", Locale.CHINA) }
    val allSelected = records.isNotEmpty() && selected.size == records.size

    Box(Modifier.fillMaxSize().background(Palette.bg)) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) {
                    TextButton(onClick = {
                        selected = if (allSelected) emptySet() else records.map { it.id }.toSet()
                    }) {
                        Text(if (allSelected) "取消全选" else "全选", color = Palette.blue, fontSize = 15.sp)
                    }
                } else {
                    CircleIconButton(onClick = onClose, size = 40) {
                        Text("‹", color = Palette.blueBright, fontSize = 22.sp)
                    }
                }

                Text(
                    "对战记录",
                    color = Palette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                if (selecting) {
                    // 删除按钮：显式用 destructive，不然会跟着主题变成蓝的
                    TextButton(
                        onClick = {
                            history.delete(selected)
                            selected = emptySet()
                            selecting = false
                        },
                        enabled = selected.isNotEmpty(),
                    ) {
                        Text(
                            if (selected.isEmpty()) "删除" else "删除 ${selected.size}",
                            color = if (selected.isEmpty()) Palette.textFaint else Palette.destructive,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                } else if (records.isNotEmpty()) {
                    TextButton(onClick = { selecting = true }) {
                        Text("选择", color = Palette.blue, fontSize = 15.sp)
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }

            if (records.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📋", fontSize = 42.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("还没有打完的比赛", color = Palette.textDim, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("打完一整场就会自动记在这里", color = Palette.textFaint, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { StatsCard(history) }

                    items(records, key = { it.id }) { record ->
                        val isSelected = record.id in selected
                        if (selecting) {
                            // 选择模式下点行勾选，不做滑动
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        selected = if (isSelected) selected - record.id else selected + record.id
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = Palette.blue),
                                )
                                RecordCard(record, fmt, Modifier.weight(1f))
                            }
                        } else {
                            SwipeToDelete(onDelete = { history.delete(record) }) {
                                RecordCard(record, fmt, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }

}

/** 向左滑，右侧露出红色的删除。 */
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val dm = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        },
    )
    SwipeToDismissBox(
        state = dm,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Palette.destructive),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("🗑 删除", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                     modifier = Modifier.padding(end = 22.dp))
            }
        },
    ) { content() }
}

@Composable
private fun RecordCard(record: MatchRecord, fmt: SimpleDateFormat, modifier: Modifier = Modifier) {
    val isRedWin = record.winner == Side.RED
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.card.copy(alpha = 0.7f))
            .border(
                1.dp,
                (if (isRedWin) Palette.red else Palette.blue).copy(alpha = 0.28f),
                RoundedCornerShape(18.dp),
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmt.format(Date(record.timestamp)), color = Palette.textDim, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(Palette.blue.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) { Text(record.mode.title, color = Palette.blueBright, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            if (record.format == com.badminton.score.data.MatchFormat.DOUBLES) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) { Text("双打", color = Palette.textDim, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.weight(1f))
            Text(record.durationText, color = Palette.textFaint, fontSize = 11.sp)
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            SideText(record.redName, record.gamesLine, record.winner == Side.RED, Palette.redBright, Modifier.weight(1f))
            Text("vs", color = Palette.textFaint, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
            SideText(record.blueName, record.gamesLine, record.winner == Side.BLUE, Palette.blueBright, Modifier.weight(1f))
        }

        if (record.scoreLine.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(record.scoreLine, color = Palette.textDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SideText(name: String, score: String, winner: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (winner) {
            Text("👑 ", fontSize = 10.sp)
        }
        Text(
            name,
            color = if (winner) Palette.text else Palette.textDim,
            fontSize = 14.sp,
            fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text(score, color = if (winner) tint else Palette.textFaint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatsCard(history: MatchHistoryStore) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.card.copy(alpha = 0.6f))
            .border(1.dp, Palette.line, RoundedCornerShape(20.dp))
            .padding(vertical = 16.dp),
    ) {
        Stat("总场次", "${history.total}", Palette.text, Modifier.weight(1f))
        Stat("红方胜", "${history.redWins}", Palette.redBright, Modifier.weight(1f))
        Stat("蓝方胜", "${history.blueWins}", Palette.blueBright, Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = tint, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Palette.textDim, fontSize = 11.sp)
    }
}
