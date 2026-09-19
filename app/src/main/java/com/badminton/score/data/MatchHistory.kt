package com.badminton.score.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 一场打完的比赛。 */
@Serializable
data class MatchRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    /** 打完的时间（毫秒）。 */
    val timestamp: Long = System.currentTimeMillis(),
    val mode: ScoringMode,
    val format: MatchFormat,
    val redName: String,
    val blueName: String,
    /** 各局比分。 */
    val games: List<GameScore>,
    /** 谁赢了。 */
    val winner: Side?,
    /** 整场用了多久（秒）。 */
    val durationSeconds: Double,
) {
    /** 红方赢了几局。 */
    val redGames: Int get() = games.count { it.winner == Side.RED }

    /** 蓝方赢了几局。 */
    val blueGames: Int get() = games.count { it.winner == Side.BLUE }

    /** 某方赢的局数。记录列表里各方显示各自的，不是同一个大比分。 */
    fun gamesOf(side: Side): Int = if (side == Side.RED) redGames else blueGames

    /** 大比分，例如 "2-1"。 */
    val gamesLine: String get() = "$redGames-$blueGames"

    /** 各局小分，例如 "21-19 / 18-21 / 21-15"。 */
    val scoreLine: String get() = games.joinToString(" / ") { "${it.red}-${it.blue}" }

    val winnerName: String?
        get() = winner?.let { if (it == Side.RED) redName else blueName }

    /** 用时文案，例如 "12 分 30 秒"。 */
    val durationText: String
        get() {
            val total = durationSeconds.toInt()
            val m = total / 60
            val s = total % 60
            return when {
                m == 0 -> "$s 秒"
                s == 0 -> "$m 分"
                else -> "$m 分 $s 秒"
            }
        }
}

/** 对战记录的读写。抽成接口，测试里用内存实现。 */
interface HistoryStorage {
    fun load(): List<MatchRecord>
    fun save(records: List<MatchRecord>)
}

private object NoopHistoryStorage : HistoryStorage {
    private var mem: List<MatchRecord> = emptyList()
    override fun load() = mem
    override fun save(records: List<MatchRecord>) { mem = records }
}

/** 对战记录仓库。最多留 200 场，新的排前面。 */
class MatchHistoryStore(private val storage: HistoryStorage = NoopHistoryStorage) {

    private val _records = kotlinx.coroutines.flow.MutableStateFlow(loadFrom(storage))
    val records: kotlinx.coroutines.flow.StateFlow<List<MatchRecord>> = _records

    val items: List<MatchRecord> get() = _records.value

    private fun loadFrom(storage: HistoryStorage) = storage.load()

    private fun persist() = storage.save(_records.value)

    fun add(record: MatchRecord) {
        val list = (listOf(record) + _records.value).take(LIMIT)
        _records.value = list
        persist()
    }

    fun delete(record: MatchRecord) = delete(setOf(record.id))

    /** 批量删除（选择模式用）。 */
    fun delete(ids: Set<String>) {
        if (ids.isEmpty()) return
        _records.value = _records.value.filterNot { it.id in ids }
        persist()
    }

    fun clear() {
        _records.value = emptyList()
        persist()
    }

    // MARK: 统计

    val total: Int get() = _records.value.size
    val redWins: Int get() = _records.value.count { it.winner == Side.RED }
    val blueWins: Int get() = _records.value.count { it.winner == Side.BLUE }
    val totalDuration: Double get() = _records.value.sumOf { it.durationSeconds }

    companion object {
        private const val LIMIT = 200
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * 全局共享实例。Application 启动时用 `install` 换成真正落盘的实现。
         *
         * 默认就指向它 —— 这样 MatchStore 不需要谁记得注入。
         * （iOS 版一开始写成外部注入，结果注入那行没写进去，
         * 对战记录一条都存不下来。）
         */
        @Volatile
        var shared: MatchHistoryStore = MatchHistoryStore()
            private set

        fun install(storage: HistoryStorage) {
            shared = MatchHistoryStore(storage)
        }

        fun encode(records: List<MatchRecord>): String = json.encodeToString(records)
        fun decode(text: String): List<MatchRecord> =
            runCatching { json.decodeFromString<List<MatchRecord>>(text) }.getOrDefault(emptyList())
    }
}
