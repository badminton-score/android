package com.badminton.score

import android.app.Application
import com.badminton.score.data.MatchHistoryStore
import com.badminton.score.data.PrefsHistoryStorage

class BadmintonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 把对战记录仓库换成真正落盘的实现。
        // MatchStore 那边默认就读这个共享实例，所以不用再往哪儿注入。
        MatchHistoryStore.install(PrefsHistoryStorage(this))
    }
}
