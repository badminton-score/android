package com.badminton.score.data

import android.content.Context

/** 用 SharedPreferences 存。比分和记录都很小，用不着数据库。 */
private class Prefs(context: Context) {
    val sp = context.applicationContext.getSharedPreferences("badminton", Context.MODE_PRIVATE)
}

class PrefsMatchStorage(context: Context) : MatchStorage {
    private val sp = Prefs(context).sp
    override fun save(state: MatchState, undo: List<MatchState>, redo: List<MatchState>) {
        sp.edit().putString(KEY, MatchStore.encodeArchive(state, undo, redo)).apply()
    }
    override fun load(): MatchStore.Archive? =
        sp.getString(KEY, null)?.let { MatchStore.decodeArchive(it) }
    override fun clear() { sp.edit().remove(KEY).apply() }
    private companion object { const val KEY = "match.archive.v1" }
}

class PrefsHistoryStorage(context: Context) : HistoryStorage {
    private val sp = Prefs(context).sp
    override fun load(): List<MatchRecord> =
        sp.getString(KEY, null)?.let { MatchHistoryStore.decode(it) } ?: emptyList()
    override fun save(records: List<MatchRecord>) {
        sp.edit().putString(KEY, MatchHistoryStore.encode(records)).apply()
    }
    private companion object { const val KEY = "history.v1" }
}

/** 一些零散偏好（选中的赛制等）。 */
class PrefsSettings(context: Context) {
    private val sp = Prefs(context).sp

    var selectedMode: ScoringMode
        get() = runCatching { ScoringMode.valueOf(sp.getString("selectedMode", null) ?: "") }
            .getOrDefault(ScoringMode.BWF21)
        set(v) { sp.edit().putString("selectedMode", v.name).apply() }
}
