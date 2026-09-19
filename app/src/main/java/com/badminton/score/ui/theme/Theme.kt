package com.badminton.score.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

private val scheme = darkColorScheme(
    primary = Palette.blue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    background = Palette.bg,
    onBackground = Palette.text,
    surface = Palette.card,
    onSurface = Palette.text,
    surfaceVariant = Palette.cardSoft,
    onSurfaceVariant = Palette.textDim,
    error = Palette.destructive,
)

@Composable
fun BadmintonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme) {
        // 整个 App 铺一层背景。光靠各页面自己的背景不够稳，
        // 万一某个界面没画出来，露出来的是窗口底色。（周目那次的教训）
        Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(Palette.bgGradient))
            ) { content() }
        }
    }
}
