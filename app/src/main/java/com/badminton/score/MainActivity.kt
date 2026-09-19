package com.badminton.score

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.badminton.score.ui.ScoreApp
import com.badminton.score.ui.theme.BadmintonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 个别旧 ROM 上这个调用可能抛异常，包一层别让它拦住启动
        runCatching { enableEdgeToEdge() }
        setContent {
            BadmintonTheme { ScoreApp() }
        }
    }
}
