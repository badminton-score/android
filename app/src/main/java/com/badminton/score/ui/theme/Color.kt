package com.badminton.score.ui.theme

import androidx.compose.ui.graphics.Color

/** 和 iOS 版 Theme.swift 里那套红蓝一致。 */
object Palette {
    val bg = Color(0xFF07080C)
    val bgGradient = listOf(Color(0xFF0B0C0F), Color(0xFF07080B), Color(0xFF040406))
    val card = Color(0xFF12141C)
    val cardSoft = Color(0xFF1B1E28)

    val text = Color(0xFFFFFFFF)
    val textDim = Color(0xFF9AA1B4)
    val textFaint = Color(0xFF5F6678)

    // 红方
    val red = Color(0xFFFF3D4D)
    val redDeep = Color(0xFF9E0A21)
    val redBright = Color(0xFFFF8C84)
    val redPanel = Color(0xFF2B0810)
    val redPanelDeep = Color(0xFF120608)

    // 蓝方
    val blue = Color(0xFF3385FF)
    val blueDeep = Color(0xFF0854A8)
    val blueBright = Color(0xFF8CD1FF)
    val bluePanel = Color(0xFF071432)
    val bluePanelDeep = Color(0xFF040916)

    val line = Color(0x1FFFFFFF)
    val lineSoft = Color(0x12FFFFFF)

    /** 删除 / 重新开始这类破坏性操作的红。 */
    val destructive = Color(0xFFFF453A)

    fun accent(isRed: Boolean) = if (isRed) red else blue
    fun bright(isRed: Boolean) = if (isRed) redBright else blueBright
    fun panel(isRed: Boolean) = if (isRed) redPanel else bluePanel
    fun panelDeep(isRed: Boolean) = if (isRed) redPanelDeep else bluePanelDeep
}
