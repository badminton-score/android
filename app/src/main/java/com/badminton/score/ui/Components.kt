package com.badminton.score.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badminton.score.ui.theme.Palette

/** 通用的玻璃卡片。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier
        .clip(RoundedCornerShape(18.dp))
        .background(Palette.card.copy(alpha = 0.72f))
        .border(1.dp, Palette.line, RoundedCornerShape(18.dp))
    if (onClick != null) m = m.clickable { onClick() }
    Column(m.padding(16.dp), content = content)
}

/** 小节标题。 */
@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = Palette.textDim,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/** 红/蓝渐变背景，用在选中态。 */
fun sideGradient(isRed: Boolean): Brush = Brush.horizontalGradient(
    listOf(
        if (isRed) Palette.red else Palette.blue,
        if (isRed) Palette.redDeep else Palette.blueDeep,
    )
)

/** 面板渐变（比分面板的底）。 */
fun panelGradient(isRed: Boolean): Brush = Brush.verticalGradient(
    listOf(Palette.panel(isRed), Palette.panelDeep(isRed))
)

/** 主按钮。 */
@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.3f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Palette.bg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** 一行「标题 —— 值」。 */
@Composable
fun InfoRow(label: String, value: String, valueColor: Color = Palette.text) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.textDim, fontSize = 15.sp)
        Text(value, color = valueColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 小的圆形图标按钮。 */
@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Int = 44,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(size.dp / 2))
            .background(Color.White.copy(alpha = if (enabled) 0.08f else 0.03f))
            .border(1.dp, Palette.lineSoft, RoundedCornerShape(size.dp / 2))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) { content() }
}
