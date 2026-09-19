package com.badminton.score.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.badminton.score.data.MatchStore
import com.badminton.score.data.PrefsMatchStorage
import com.badminton.score.data.PrefsSettings
import com.badminton.score.ui.screens.HomeScreen
import com.badminton.score.ui.screens.MatchScreen
import com.badminton.score.ui.screens.RecordsScreen
import com.badminton.score.ui.screens.SettingsScreen

/**
 * 顶层界面切换。
 *
 * **没用 Navigation Compose**，就是一个状态变量手写切换。
 * （周目那边用 NavHost 出过「返回栈被弹空 → 整屏空白」，
 * 后来改成状态切换根治。本 App 层级也浅，同样不值得上导航库。）
 */
@Composable
fun ScoreApp() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { MatchStore(storage = PrefsMatchStorage(ctx)) }
    val prefs = remember { PrefsSettings(ctx) }

    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var inMatch by rememberSaveable { mutableStateOf(false) }
    var showRecords by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
    BackHandler(enabled = screen == Screen.Home && inMatch) { inMatch = false }

    when {
        screen == Screen.Settings -> SettingsScreen(
            store = store,
            onBack = { screen = Screen.Home },
        )

        inMatch -> MatchScreen(store = store, onExit = { inMatch = false })

        else -> HomeScreen(
            store = store,
            prefs = prefs,
            onStart = { inMatch = true },
            onResume = { inMatch = true },
            onOpenSettings = { screen = Screen.Settings },
            onOpenRecords = { showRecords = true },
        )
    }

    if (showRecords) {
        RecordsScreen(onClose = { showRecords = false })
    }
}

enum class Screen { Home, Settings }
