package com.badminton.score.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 比赛过程中需要浮层展示的内容。 */
sealed interface MatchPresentation {
    /** 整场比赛结束，展示胜利画面。 */
    data class MatchResult(val side: Side) : MatchPresentation

    /** 一局结束，询问是否开始下一局。 */
    data class NextGame(val side: Side, val red: Int, val blue: Int, val game: Int) : MatchPresentation
}

/** 短暂提示。 */
data class ToastMessage(val text: String, val symbol: String, val token: Long = System.nanoTime())

/**
 * 比赛存档的读写。抽成接口是为了让单元测试能用内存实现，
 * 不用跑 Robolectric 也能测会话逻辑。
 */
interface MatchStorage {
    fun save(state: MatchState, undo: List<MatchState>, redo: List<MatchState>)
    fun load(): MatchStore.Archive?
    fun clear()
}

@Serializable
data class ArchivedMatch(val state: MatchState, val undo: List<MatchState>, val redo: List<MatchState>)

/**
 * 一次比赛的完整会话状态：比分、撤销/重做栈、提示与弹窗，以及本地持久化。
 *
 * 刻意做成**不带 Android 依赖**的普通类 —— 界面层把它包成 ViewModel，
 * 测试里直接 new 一个就能测。
 */
class MatchStore(
    initial: MatchState = MatchState(mode = ScoringMode.BWF21),
    private val storage: MatchStorage? = null,
) {
    private var historyOverride: MatchHistoryStore? = null

    /**
     * 打完的比赛往这儿写。
     *
     * **默认就是共享实例**，不需要谁记得注入；测试里可以换成自己的。
     * 用 getter 而不是构造参数，免得在 `install()` 之前就把默认值固化了。
     */
    var history: MatchHistoryStore
        get() = historyOverride ?: MatchHistoryStore.shared
        set(value) { historyOverride = value }

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<MatchState> = _state.asStateFlow()

    private val _lastEvent = MutableStateFlow<ScoreEvent>(ScoreEvent.None)
    val lastEvent: StateFlow<ScoreEvent> = _lastEvent.asStateFlow()

    private val _presentation = MutableStateFlow<MatchPresentation?>(null)
    val presentation: StateFlow<MatchPresentation?> = _presentation.asStateFlow()

    private val _toast = MutableStateFlow<ToastMessage?>(null)
    val toast: StateFlow<ToastMessage?> = _toast.asStateFlow()

    private val _lastUndoneSide = MutableStateFlow<Side?>(null)
    val lastUndoneSide: StateFlow<Side?> = _lastUndoneSide.asStateFlow()

    var isShowingSettings = false
    var isShowingHistory = false

    private val undoStack = ArrayDeque<MatchState>()
    private val redoStack = ArrayDeque<MatchState>()

    /** 这一场从什么时候开始的，用来算用时。 */
    private var startedAt = System.currentTimeMillis()

    /** 本场是否已经记过一笔，避免同一场重复记录。 */
    private var didRecord = false

    /** 当前状态的快照（内部用，保证和 state.value 一致）。 */
    private var s: MatchState
        get() = _state.value
        set(value) { _state.value = value }

    init {
        // 恢复上次未完成的比赛
        storage?.load()?.let { archive ->
            _state.value = archive.state
            undoStack.clear(); undoStack.addAll(archive.undo)
            redoStack.clear(); redoStack.addAll(archive.redo)
        }
    }

    private fun persist() {
        storage?.save(s, undoStack.toList(), redoStack.toList())
    }

    // MARK: - 派生信息

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val isLocked: Boolean get() = s.isMatchOver || s.gameWinner != null || _presentation.value != null

    fun isMatchPoint(side: Side): Boolean = s.isMatchPoint(side)
    fun isGamePoint(side: Side): Boolean = s.isGamePoint(side)

    // MARK: - 计分

    fun addPoint(side: Side) {
        if (isLocked) return
        pushUndo()
        val (next, event) = ScoreEngine.applyPoint(side, s)
        s = next
        _lastEvent.value = event
        _lastUndoneSide.value = null
        _toast.value = null

        when (event) {
            is ScoreEvent.MatchWon -> {
                _presentation.value = MatchPresentation.MatchResult(event.side)
                recordIfNeeded()
            }
            is ScoreEvent.GameWon -> {
                _presentation.value = MatchPresentation.NextGame(event.side, event.red, event.blue, event.game)
            }
            else -> {}
        }
        persist()
    }

    /** 减分：撤销最近一次计分（和撤销按钮等价）。 */
    fun removePoint(side: Side) = undo(side)

    fun undo(preferredSide: Side? = null) {
        if (!canUndo || _presentation.value != null) return
        val previous = undoStack.last()
        val side = preferredSide ?: run {
            if (previous.redPoints != s.redPoints) Side.RED
            else if (previous.bluePoints != s.bluePoints) Side.BLUE
            else null
        }
        pushRedo()
        s = undoStack.removeLast()
        _lastEvent.value = ScoreEvent.Undone
        _lastUndoneSide.value = side
        val who = side?.let { s.name(it) } ?: "一"
        _toast.value = ToastMessage("已撤销 $who 1 分", "arrow.uturn.backward")
        persist()
    }

    fun redo() {
        if (!canRedo || _presentation.value != null) return
        // 这里**不能**走 pushUndo()：它会 redoStack.clear()，
        // 而下一行就要从 redoStack 里取状态，栈被清空就会抛 NoSuchElementException。
        // （iOS 那边同样的写法导致点「重做」必闪退，移植时一并避开。）
        undoStack.addLast(s)
        while (undoStack.size > 200) undoStack.removeFirst()
        s = redoStack.removeLast()
        _lastEvent.value = ScoreEvent.Point(s.server)
        _toast.value = ToastMessage("已恢复 1 分", "arrow.uturn.forward")
        persist()
    }

    private fun pushUndo() {
        undoStack.addLast(s)
        while (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun pushRedo() {
        redoStack.addLast(s)
    }

    // MARK: - 局与场

    fun startNextGame() {
        s = ScoreEngine.startNextGame(s)
        _presentation.value = null
        undoStack.clear()
        redoStack.clear()
        _lastEvent.value = ScoreEvent.None
        _lastUndoneSide.value = null
        persist()
    }

    /** 整场重来：保持队名与赛制，清零比分。 */
    fun rematch() {
        startedAt = System.currentTimeMillis()
        didRecord = false
        val fresh = MatchState(mode = s.mode)
            .copy(
                redName = s.players(Side.RED).first(),
                blueName = s.players(Side.BLUE).first(),
                customRules = s.customRules,
                format = s.format,
            )
            .setPlayers(s.players(Side.RED), Side.RED)
            .setPlayers(s.players(Side.BLUE), Side.BLUE)
        s = fresh
        undoStack.clear(); redoStack.clear()
        _presentation.value = null
        _toast.value = null
        _lastEvent.value = ScoreEvent.None
        _lastUndoneSide.value = null
        persist()
    }

    fun clearPersisted() {
        storage?.clear()
    }

    // MARK: - 编辑

    fun changeMode(mode: ScoringMode) {
        if (mode == s.mode) return
        startedAt = System.currentTimeMillis()
        didRecord = false
        s = MatchState(mode = mode)
            .copy(
                redName = s.players(Side.RED).first(),
                blueName = s.players(Side.BLUE).first(),
                customRules = s.customRules,
                format = s.format,
            )
            .setPlayers(s.players(Side.RED), Side.RED)
            .setPlayers(s.players(Side.BLUE), Side.BLUE)
        undoStack.clear(); redoStack.clear()
        _presentation.value = null
        _toast.value = null
        _lastEvent.value = ScoreEvent.None
        persist()
    }

    /** 切换单打 / 双打。比分保留，但撤销栈清掉（队伍构成变了）。 */
    fun setFormat(format: MatchFormat) {
        if (format == s.format) return
        s = s.copy(format = format).normalizePlayers()
        undoStack.clear(); redoStack.clear()
        persist()
    }

    /**
     * 改自定义规则。
     *
     * 规则一变，之前打的比分就按新规则重新判定了，
     * 所以保留比分、但清掉撤销栈，避免撤销回旧规则下的状态。
     */
    fun setCustomRules(points: Int, capBonus: Int?, maxGames: Int) {
        val rules = BadmintonRules.custom(points, capBonus, maxGames)
        s = s.copy(customRules = rules)
        undoStack.clear(); redoStack.clear()
        _toast.value = null
        settle()
        persist()
    }

    /** 改完规则后重新判定是否已经分出胜负，并弹出对应浮层。 */
    private fun settle() {
        val cur = s
        if (cur.isMatchOver) {
            _presentation.value = MatchPresentation.MatchResult(cur.matchWinner ?: Side.RED)
            recordIfNeeded()
            return
        }
        val winner = cur.gameWinner ?: run { _presentation.value = null; return }
        if (cur.games(winner) >= cur.rules.gamesToWin) {
            s = cur.copy(isMatchOver = true, matchWinner = winner)
            _presentation.value = MatchPresentation.MatchResult(winner)
            recordIfNeeded()
        } else if (cur.gameScores.none { it.game == cur.currentGame }) {
            _presentation.value = MatchPresentation.NextGame(winner, cur.redPoints, cur.bluePoints, cur.currentGame)
        }
    }

    fun rename(side: Side, to: String) {
        val trimmed = to.trim()
        val value = if (trimmed.isEmpty()) side.defaultName else trimmed.take(10)
        val list = s.players(side).toMutableList().also { it[0] = value }
        s = s.setPlayers(list, side)
        persist()
    }

    fun renamePlayer(side: Side, index: Int, to: String) {
        val list = s.players(side).toMutableList()
        if (index !in list.indices) return
        val trimmed = to.trim()
        list[index] = if (trimmed.isEmpty()) (if (index == 0) side.defaultName else "队友") else trimmed.take(10)
        s = s.setPlayers(list, side)
        persist()
    }

    fun setFirstServer(side: Side) {
        s = s.copy(server = side, startedByRed = side == Side.RED)
        persist()
    }

    fun dismissPresentation() {
        _presentation.value = null
        persist()
    }

    fun clearToast() {
        _toast.value = null
    }

    // MARK: - 对战记录

    private fun recordIfNeeded() {
        val cur = s
        if (didRecord || !cur.isMatchOver) return
        val winner = cur.matchWinner ?: return
        didRecord = true
        history.add(
            MatchRecord(
                mode = cur.mode,
                format = cur.format,
                redName = cur.name(Side.RED),
                blueName = cur.name(Side.BLUE),
                games = cur.gameScores,
                winner = winner,
                durationSeconds = (System.currentTimeMillis() - startedAt) / 1000.0,
            )
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encodeArchive(state: MatchState, undo: List<MatchState>, redo: List<MatchState>): String =
            json.encodeToString(ArchivedMatch(state, undo, redo))

        fun decodeArchive(text: String): Archive? = runCatching {
            val a = json.decodeFromString<ArchivedMatch>(text)
            Archive(a.state, a.undo, a.redo)
        }.getOrNull()
    }

    /** 存档的内存表示。 */
    data class Archive(val state: MatchState, val undo: List<MatchState>, val redo: List<MatchState>)
}
