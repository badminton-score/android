package com.badminton.score

import com.badminton.score.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ───────────────────────── 单局胜负判定 ─────────────────────────

class GameWinnerTest {

    @Test
    fun `21 分制先到 21 分且领先 2 分即获胜`() {
        val r = ScoringMode.BWF21.rules
        assertNull(r.winner(20, 15))
        assertEquals(Side.RED, r.winner(21, 15))
        assertEquals(Side.BLUE, r.winner(15, 21))
    }

    @Test
    fun `20 平后需要净胜 2 分，21_20 不算获胜`() {
        val r = ScoringMode.BWF21.rules
        assertTrue(r.isAtDeuce(20, 20))
        assertNull(r.winner(21, 20))
        assertNull(r.winner(20, 21))
        assertEquals(Side.RED, r.winner(22, 20))
        assertEquals(Side.BLUE, r.winner(20, 22))
        assertNull(r.winner(25, 24))
        assertEquals(Side.RED, r.winner(26, 24))
    }

    @Test
    fun `29 平后先到 30 分者获胜（封顶）`() {
        val r = ScoringMode.BWF21.rules
        assertNull(r.winner(29, 29))
        assertEquals(Side.RED, r.winner(30, 29))
        assertEquals(Side.BLUE, r.winner(29, 30))
    }

    @Test
    fun `21 分长盘没有封顶，必须有 2 分差距`() {
        val r = ScoringMode.CLASSIC21.rules
        assertNull(r.cap)
        assertNull(r.winner(30, 29))
        assertEquals(Side.RED, r.winner(31, 29))
        assertNull(r.winner(40, 39))
    }

    @Test
    fun `15 分制 14 平后需净胜 2 分，21 分封顶`() {
        val r = ScoringMode.TRADITIONAL15.rules
        assertEquals(Side.RED, r.winner(15, 12))
        assertNull(r.winner(15, 14))
        assertEquals(Side.RED, r.winner(16, 14))
        assertEquals(Side.RED, r.winner(21, 20))
    }

    @Test
    fun `11 分制 10 平后需净胜 2 分，15 分封顶`() {
        val r = ScoringMode.TRADITIONAL11.rules
        assertEquals(Side.RED, r.winner(11, 9))
        assertNull(r.winner(11, 10))
        assertEquals(Side.RED, r.winner(12, 10))
        assertEquals(Side.RED, r.winner(15, 14))
    }
}

// ───────────────────────── 局点与赛点 ─────────────────────────

class PointSituationTest {

    @Test
    fun `20_19 是局点；平分规则从双方都到 20 才开始算`() {
        var s = MatchState(mode = ScoringMode.BWF21).copy(redPoints = 20, bluePoints = 19)
        assertTrue(s.isGamePoint(Side.RED))
        // 蓝方在 20:19 得分只是追成 20:20，不是局点
        assertFalse(s.isGamePoint(Side.BLUE))

        // 21:20：红方再得一分是 22:20，净胜 2 分，所以是局点
        s = s.copy(redPoints = 21, bluePoints = 20)
        assertTrue(s.isGamePoint(Side.RED))
        assertFalse(s.isGamePoint(Side.BLUE))

        // 22:20 这一局已经结束了
        s = s.copy(redPoints = 22, bluePoints = 20)
        assertEquals(Side.RED, s.gameWinner)
        assertFalse(s.isGamePoint(Side.RED))

        // 29 平之后封顶使这一分直接决定胜负
        s = s.copy(redPoints = 29, bluePoints = 29)
        assertTrue(s.isGamePoint(Side.RED))
        assertTrue(s.isGamePoint(Side.BLUE))
    }

    @Test
    fun `第三局 20_19 时是赛点`() {
        var s = MatchState(mode = ScoringMode.BWF21)
            .copy(redGames = 1, blueGames = 1, currentGame = 3, redPoints = 20, bluePoints = 19)
        assertTrue(s.isMatchPoint(Side.RED))
        assertTrue(s.isGamePoint(Side.RED))

        s = s.copy(redPoints = 20, bluePoints = 20)
        assertFalse(s.isMatchPoint(Side.RED))
    }

    @Test
    fun `第一局永远不是赛点`() {
        val s = MatchState(mode = ScoringMode.BWF21).copy(redPoints = 20, bluePoints = 5)
        assertFalse(s.isMatchPoint(Side.RED))
    }
}

// ───────────────────────── 得分与发球权 ─────────────────────────

class ScoringFlowTest {

    @Test
    fun `每球得分制：得分方获得发球权`() {
        var state = MatchState(mode = ScoringMode.BWF21, server = Side.RED)
        var (next, event) = ScoreEngine.applyPoint(Side.BLUE, state)
        assertEquals(Side.BLUE, next.server)
        assertEquals(1, next.bluePoints)
        assertEquals(ScoreEvent.Point(Side.BLUE), event)

        state = next
        val r2 = ScoreEngine.applyPoint(Side.RED, state)
        assertEquals(Side.RED, r2.first.server)
    }

    @Test
    fun `发球方得分为偶数时发右区，奇数时发左区`() {
        var s = MatchState(mode = ScoringMode.BWF21, server = Side.RED)
        assertEquals("右区", s.serveBox)
        s = s.copy(redPoints = 1)
        assertEquals("左区", s.serveBox)
        s = s.copy(redPoints = 10)
        assertEquals("右区", s.serveBox)
    }

    @Test
    fun `旧制发球得分制：接发球方得分不换发球`() {
        val state = MatchState(mode = ScoringMode.TRADITIONAL15, server = Side.RED)
            .copy(redPoints = 3, bluePoints = 3)
        val (next, _) = ScoreEngine.applyPoint(Side.BLUE, state)
        assertEquals(4, next.bluePoints)
        assertEquals(3, next.redPoints)
        assertEquals(Side.RED, next.server)

        // 发球方得分则继续保持发球权
        val served = ScoreEngine.applyPoint(Side.RED, next).first
        assertEquals(4, served.redPoints)
        assertEquals(Side.RED, served.server)
    }

    @Test
    fun `拿到 21 分触发本局结束`() {
        val state = MatchState(mode = ScoringMode.BWF21, server = Side.RED)
            .copy(redPoints = 20, bluePoints = 15)
        val (next, event) = ScoreEngine.applyPoint(Side.RED, state)
        assertEquals(1, next.redGames)
        assertEquals(1, next.gameScores.size)
        assertEquals(21, next.gameScores[0].red)
        assertEquals(15, next.gameScores[0].blue)
        assertEquals(ScoreEvent.GameWon(Side.RED, 21, 15, 1), event)
        assertFalse(next.isMatchOver)
    }

    @Test
    fun `比分到 20_20 时比赛继续，不会误判结束`() {
        val state = MatchState(mode = ScoringMode.BWF21, server = Side.RED)
            .copy(redPoints = 20, bluePoints = 19)
        val (next, event) = ScoreEngine.applyPoint(Side.BLUE, state)
        assertNull(next.gameWinner)
        assertTrue(next.gameScores.isEmpty())
        assertEquals(ScoreEvent.Point(Side.BLUE), event)
        assertEquals(20, next.redPoints)
        assertEquals(20, next.bluePoints)
    }

    @Test
    fun `赢下两局结束整场比赛`() {
        val state = MatchState(mode = ScoringMode.BWF21, server = Side.RED)
            .copy(redGames = 1, currentGame = 2, redPoints = 20, bluePoints = 10)
        val (next, event) = ScoreEngine.applyPoint(Side.RED, state)
        assertTrue(next.isMatchOver)
        assertEquals(Side.RED, next.matchWinner)
        assertEquals(ScoreEvent.MatchWon(Side.RED, 2, 0), event)
    }

    @Test
    fun `比赛结束后不再计分`() {
        val state = MatchState(mode = ScoringMode.BWF21)
            .copy(isMatchOver = true, matchWinner = Side.RED)
        val (next, event) = ScoreEngine.applyPoint(Side.BLUE, state)
        assertEquals(0, next.bluePoints)
        assertEquals(ScoreEvent.None, event)
    }

    @Test
    fun `一局制赢一局即结束比赛`() {
        val state = MatchState(mode = ScoringMode.SINGLE21, server = Side.BLUE).copy(bluePoints = 20)
        val (next, event) = ScoreEngine.applyPoint(Side.BLUE, state)
        assertTrue(next.isMatchOver)
        assertEquals(ScoreEvent.MatchWon(Side.BLUE, 0, 1), event)
    }

    @Test
    fun `下一局由上一局胜方先发球，比分清零`() {
        val state = MatchState(mode = ScoringMode.BWF21, server = Side.RED)
            .copy(redPoints = 20, bluePoints = 18)
        val (ended, _) = ScoreEngine.applyPoint(Side.RED, state)
        val next = ScoreEngine.startNextGame(ended)
        assertEquals(2, next.currentGame)
        assertEquals(0, next.redPoints)
        assertEquals(0, next.bluePoints)
        assertEquals(Side.RED, next.server)
        assertEquals(1, next.gameScores.size)
    }
}

// ───────────────────────── 赛制预设 ─────────────────────────

class ScoringModeTest {

    @Test
    fun `六种预设规则的参数`() {
        assertEquals(6, ScoringMode.entries.size)

        val bwf = ScoringMode.BWF21.rules
        assertTrue(bwf.pointsToWin == 21 && bwf.cap == 30 && bwf.deuceAt == 20 && bwf.gamesToWin == 2)

        val long = ScoringMode.CLASSIC21.rules
        assertTrue(long.cap == null && long.gamesToWin == 2)

        val old15 = ScoringMode.TRADITIONAL15.rules
        assertTrue(old15.pointsToWin == 15 && old15.cap == 21 && old15.deuceAt == 14)
        assertFalse(old15.rallyPoint)

        val old11 = ScoringMode.TRADITIONAL11.rules
        assertTrue(old11.pointsToWin == 11 && old11.cap == 15 && old11.deuceAt == 10)
        assertFalse(old11.rallyPoint)

        val single = ScoringMode.SINGLE21.rules
        assertTrue(single.gamesToWin == 1 && single.maximumGames == 1)

        // 「自定义」在用户没调之前是一套 21 分制默认值
        val custom = ScoringMode.CUSTOM.rules
        assertTrue(custom.pointsToWin == 21 && custom.cap == 30)
        assertEquals(ScoringMode.CUSTOM, custom.mode)
    }

    @Test
    fun `每种赛制都能在合理比分下结束`() {
        for (mode in ScoringMode.entries) {
            val store = MatchStore(MatchState(mode = mode))
            var guard = 0
            while (!store.state.value.isMatchOver && guard < 400) {
                store.addPoint(Side.RED)
                guard++
                if (store.state.value.gameWinner != null && !store.state.value.isMatchOver) {
                    store.startNextGame()
                }
            }
            assertTrue("${mode.title} 应当可以结束比赛", store.state.value.isMatchOver)
            assertEquals(Side.RED, store.state.value.matchWinner)
        }
    }
}

// ───────────────────────── 自定义赛制 ─────────────────────────

class CustomRulesTest {

    @Test
    fun `按目标分 封顶加成 局数造出规则`() {
        val r = BadmintonRules.custom(points = 11, capBonus = 5, maxGames = 3)
        assertEquals(11, r.pointsToWin)
        assertEquals(16, r.cap)
        assertEquals(10, r.deuceAt)
        assertEquals(3, r.maximumGames)
        assertEquals(2, r.gamesToWin)
        assertTrue(r.rallyPoint)
    }

    @Test
    fun `局数决定要赢几局`() {
        assertEquals(1, BadmintonRules.custom(21, null, 1).gamesToWin)
        assertEquals(2, BadmintonRules.custom(21, null, 3).gamesToWin)
        assertEquals(3, BadmintonRules.custom(21, null, 5).gamesToWin)
    }

    @Test
    fun `不封顶时 cap 为 null，且必须净胜 2 分`() {
        val r = BadmintonRules.custom(points = 15, capBonus = null, maxGames = 3)
        assertNull(r.cap)
        assertEquals(Int.MAX_VALUE, r.absoluteWin)
        assertNull(r.winner(15, 14))
        assertEquals(Side.RED, r.winner(16, 14))
    }

    @Test
    fun `目标分被夹到 5 到 50，非法局数回落到 3 局`() {
        assertEquals(5, BadmintonRules.custom(1, null, 3).pointsToWin)
        assertEquals(50, BadmintonRules.custom(999, null, 3).pointsToWin)
        assertEquals(3, BadmintonRules.custom(21, null, 4).maximumGames)
    }

    @Test
    fun `自定义规则真的影响到判定：11 分制到 11 就结束`() {
        var state = MatchState(
            mode = ScoringMode.CUSTOM,
            customRules = BadmintonRules.custom(11, 5, 3),
        )
        repeat(11) { state = ScoreEngine.applyPoint(Side.RED, state).first }
        assertEquals(Side.RED, state.gameWinner)
        assertEquals(1, state.redGames)
        assertFalse(state.isMatchOver)   // 三局两胜，才赢一局
    }

    @Test
    fun `自定义 11 分制下 10_10 不结束，12_10 才结束`() {
        val s = MatchState(
            mode = ScoringMode.CUSTOM,
            customRules = BadmintonRules.custom(11, 5, 3),
        ).copy(redPoints = 10, bluePoints = 10)
        assertNull(s.rules.winner(11, 10))
        assertEquals(Side.RED, s.rules.winner(12, 10))
        assertEquals(Side.RED, s.rules.winner(16, 15))   // 封顶 16
    }

    @Test
    fun `存档里没有 customRules 时回落到默认`() {
        val s = MatchState(mode = ScoringMode.CUSTOM)
        // Kotlin 里这个字段有默认值，所以永远是有的；规则也要能正常取到
        assertEquals(21, s.rules.pointsToWin)
    }
}

// ───────────────────────── 单打与双打 ─────────────────────────

class FormatTest {

    @Test
    fun `单打每方 1 人，双打每方 2 人`() {
        var s = MatchState(mode = ScoringMode.BWF21).setPlayers(listOf("林丹"), Side.RED)
        assertEquals(1, s.players(Side.RED).size)
        assertEquals("林丹", s.name(Side.RED))

        s = s.copy(format = MatchFormat.DOUBLES).normalizePlayers()
        assertEquals(2, s.players(Side.RED).size)
        assertEquals("林丹 / 队友", s.name(Side.RED))
    }

    @Test
    fun `切到双打会补上第二个人的位置`() {
        var s = MatchState(mode = ScoringMode.BWF21, redName = "甲", blueName = "乙")
        assertEquals(1, s.players(Side.RED).size)
        s = s.copy(format = MatchFormat.DOUBLES).normalizePlayers()
        assertEquals(listOf("甲", "队友"), s.players(Side.RED))
        assertEquals(listOf("乙", "队友"), s.players(Side.BLUE))
    }

    @Test
    fun `双打：发球方连续得分，同一个人继续发`() {
        var s = MatchState(mode = ScoringMode.BWF21)
            .setPlayers(listOf("A1", "A2"), Side.RED)
            .setPlayers(listOf("B1", "B2"), Side.BLUE)
        s = s.copy(format = MatchFormat.DOUBLES, server = Side.RED, redServeIndex = 0)

        val after = ScoreEngine.applyPoint(Side.RED, s).first
        assertEquals(Side.RED, after.server)
        assertEquals(0, after.redServeIndex)   // 还是 A1 发
    }

    @Test
    fun `双打：接发球方赢球后换人发`() {
        var s = MatchState(mode = ScoringMode.BWF21)
            .setPlayers(listOf("A1", "A2"), Side.RED)
            .setPlayers(listOf("B1", "B2"), Side.BLUE)
        s = s.copy(
            format = MatchFormat.DOUBLES,
            server = Side.RED, redServeIndex = 0, blueServeIndex = 0,
        )

        // 蓝方得分 → 蓝方拿到发球权，且换 B2 发
        val after = ScoreEngine.applyPoint(Side.BLUE, s).first
        assertEquals(Side.BLUE, after.server)
        assertEquals(1, after.blueServeIndex)
        assertEquals(0, after.redServeIndex)   // 红方不受影响

        // 红方再拿回发球权 → 换 A2 发
        val back = ScoreEngine.applyPoint(Side.RED, after).first
        assertEquals(Side.RED, back.server)
        assertEquals(1, back.redServeIndex)
    }

    @Test
    fun `单打不轮转，永远是 0 号`() {
        var s = MatchState(mode = ScoringMode.BWF21, server = Side.RED)
        s = ScoreEngine.applyPoint(Side.BLUE, s).first
        s = ScoreEngine.applyPoint(Side.RED, s).first
        assertEquals(0, s.serveIndex(Side.RED))
        assertEquals(0, s.serveIndex(Side.BLUE))
    }

    @Test
    fun `isServing 只在双打且轮到该队员时为真`() {
        var s = MatchState(mode = ScoringMode.BWF21)
            .setPlayers(listOf("A1"), Side.RED)
            .copy(server = Side.RED)
        assertFalse(s.isServing(Side.RED, 0))   // 单打恒为 false

        s = s.copy(format = MatchFormat.DOUBLES).setPlayers(listOf("A1", "A2"), Side.RED)
            .copy(redServeIndex = 1)
        assertTrue(s.isServing(Side.RED, 1))
        assertFalse(s.isServing(Side.RED, 0))
    }
}

// ───────────────────────── 完整比赛流程 ─────────────────────────

class FullMatchTest {

    /** 交替加分直到指定比分（领先方先到分，不会误触发平分延长）。 */
    private fun playGame(store: MatchStore, red: Int, blue: Int) {
        repeat(maxOf(red, blue)) {
            if (store.state.value.redPoints < red) store.addPoint(Side.RED)
            if (store.state.value.bluePoints < blue) store.addPoint(Side.BLUE)
        }
    }

    @Test
    fun `21 分制三局两胜：2-1 结束比赛`() {
        val store = MatchStore(MatchState(mode = ScoringMode.BWF21))

        playGame(store, 21, 19)
        assertEquals(listOf("21-19"), store.state.value.gameScores.map { "${it.red}-${it.blue}" })
        store.startNextGame()

        playGame(store, 15, 21)
        assertEquals(1, store.state.value.blueGames)
        store.startNextGame()

        playGame(store, 21, 18)
        assertTrue(store.state.value.isMatchOver)
        assertEquals(Side.RED, store.state.value.matchWinner)
        assertEquals("2-1", store.state.value.gamesLine)
        assertEquals("21-19 / 15-21 / 21-18", store.state.value.historyLine)
    }

    @Test
    fun `30 分封顶决胜`() {
        val store = MatchStore(MatchState(mode = ScoringMode.BWF21))
        playGame(store, 29, 29)
        assertNull(store.state.value.gameWinner)
        assertTrue(store.isGamePoint(Side.RED))

        store.addPoint(Side.RED)
        assertEquals(30, store.state.value.gameScores.first().red)
        assertEquals(29, store.state.value.gameScores.first().blue)
        assertEquals(1, store.state.value.redGames)
    }

    @Test
    fun `逐分记录可按局分组`() {
        val store = MatchStore(MatchState(mode = ScoringMode.BWF21))
        playGame(store, 21, 14)
        store.startNextGame()
        store.addPoint(Side.BLUE)
        val gameOne = store.state.value.log.filter { it.game == 1 }
        val gameTwo = store.state.value.log.filter { it.game == 2 }
        assertEquals(35, gameOne.size)
        assertEquals(1, gameTwo.size)
        assertEquals(Side.BLUE, gameTwo[0].side)
    }
}

// ───────────────────────── 比赛会话与撤销 ─────────────────────────

class MatchSessionTest {

    @Test
    fun `减分撤销最近一次得分`() {
        val store = MatchStore(MatchState(mode = ScoringMode.BWF21))
        store.addPoint(Side.RED)
        store.addPoint(Side.BLUE)
        store.addPoint(Side.RED)
        assertEquals(2, store.state.value.redPoints)
        assertEquals(1, store.state.value.bluePoints)

        store.removePoint(Side.RED)
        assertEquals(1, store.state.value.redPoints)
        assertEquals(1, store.state.value.bluePoints)
        assertTrue(store.canUndo)
        assertTrue(store.canRedo)
    }

    @Test
    fun `撤销后可以重做`() {
        val store = MatchStore(MatchState(mode = ScoringMode.BWF21))
        store.addPoint(Side.RED)
        store.undo()
        assertEquals(0, store.state.value.redPoints)
        store.redo()
        assertEquals(1, store.state.value.redPoints)
    }

    @Test
    fun `撤销会回滚比分 发球权与逐分记录`() {
        val store = MatchStore(MatchState(mode = ScoringMode.BWF21))
        store.addPoint(Side.BLUE)
        assertEquals(Side.BLUE, store.state.value.server)
        assertEquals(1, store.state.value.log.size)
        store.undo()
        assertEquals(Side.RED, store.state.value.server)
        assertTrue(store.state.value.log.isEmpty())
    }

    @Test
    fun `本局结束后加减分按钮锁定`() {
        val store = MatchStore(MatchState(mode = ScoringMode.BWF21))
        repeat(21) { store.addPoint(Side.RED) }
        assertTrue(store.isLocked)
        store.addPoint(Side.BLUE)
        assertEquals(0, store.state.value.bluePoints)
    }

    @Test
    fun `开始下一局后解锁并保留大比分`() {
        val store = MatchStore(MatchState(mode = ScoringMode.BWF21))
        repeat(21) { store.addPoint(Side.RED) }
        val p = store.presentation.value
        assertTrue("应当进入下一局提示", p is MatchPresentation.NextGame)
        p as MatchPresentation.NextGame
        assertEquals(Side.RED, p.side)
        assertEquals(1, p.game)

        store.startNextGame()
        assertEquals(2, store.state.value.currentGame)
        assertEquals(1, store.state.value.redGames)
        assertFalse(store.isLocked)
        assertNull(store.presentation.value)
    }

    @Test
    fun `整场结束时展示胜利方`() {
        val store = MatchStore(MatchState(mode = ScoringMode.BWF21))
        repeat(21) { store.addPoint(Side.RED) }
        store.startNextGame()
        repeat(21) { store.addPoint(Side.RED) }
        val p = store.presentation.value
        assertTrue("应当展示胜利方", p is MatchPresentation.MatchResult)
        assertEquals(Side.RED, (p as MatchPresentation.MatchResult).side)
        assertTrue(store.state.value.isMatchOver)
        assertEquals("2-0", store.state.value.gamesLine)
    }

    @Test
    fun `切换赛制会重置比分`() {
        val store = MatchStore(MatchState(mode = ScoringMode.BWF21))
        store.addPoint(Side.RED)
        store.changeMode(ScoringMode.TRADITIONAL15)
        assertEquals(0, store.state.value.redPoints)
        assertEquals(ScoringMode.TRADITIONAL15, store.state.value.mode)
        assertFalse(store.canUndo)
        assertEquals(15, store.state.value.rules.pointsToWin)
    }

    @Test
    fun `再来一场保留队名与赛制，清零比分`() {
        val store = MatchStore(MatchState(mode = ScoringMode.BWF21, redName = "甲", blueName = "乙"))
        store.addPoint(Side.RED)
        store.rematch()
        assertEquals(0, store.state.value.redPoints)
        assertEquals("甲", store.state.value.redName)
        assertEquals("乙", store.state.value.blueName)
        assertEquals(ScoringMode.BWF21, store.state.value.mode)
    }

    @Test
    fun `队名为空时回落到默认名`() {
        val store = MatchStore(MatchState(mode = ScoringMode.BWF21))
        store.rename(Side.RED, "   ")
        assertEquals("红方", store.state.value.redName)
        store.rename(Side.BLUE, "蓝队")
        assertEquals("蓝队", store.state.value.blueName)
    }
}

// ───────────────────────── 对战记录 ─────────────────────────

class MatchHistoryTest {

    private fun freshHistory() = MatchHistoryStore().also { it.clear() }

    private fun store(history: MatchHistoryStore, state: MatchState = MatchState(mode = ScoringMode.BWF21)) =
        MatchStore(state).also { it.history = history }

    /** 交替加分直到指定比分。 */
    private fun playGame(s: MatchStore, red: Int, blue: Int) {
        repeat(maxOf(red, blue)) {
            if (s.state.value.redPoints < red) s.addPoint(Side.RED)
            if (s.state.value.bluePoints < blue) s.addPoint(Side.BLUE)
        }
    }

    @Test
    fun `打完一整场自动记一条`() {
        val history = freshHistory()
        val s = store(history, MatchState(mode = ScoringMode.BWF21, redName = "甲", blueName = "乙"))

        repeat(21) { s.addPoint(Side.RED) }
        s.startNextGame()
        repeat(21) { s.addPoint(Side.RED) }

        assertTrue(s.state.value.isMatchOver)
        assertEquals(1, history.total)

        val r = history.items[0]
        assertEquals("甲", r.redName)
        assertEquals("乙", r.blueName)
        assertEquals(Side.RED, r.winner)
        assertEquals("2-0", r.gamesLine)
        assertEquals("21-0 / 21-0", r.scoreLine)
        assertEquals(ScoringMode.BWF21, r.mode)
        history.clear()
    }

    @Test
    fun `同一场不会重复记录`() {
        val history = freshHistory()
        val s = store(history)
        repeat(21) { s.addPoint(Side.RED) }
        s.startNextGame()
        repeat(21) { s.addPoint(Side.RED) }

        s.addPoint(Side.BLUE)
        s.addPoint(Side.BLUE)
        assertEquals(1, history.total)
        history.clear()
    }

    @Test
    fun `没打完不记录`() {
        val history = freshHistory()
        val s = store(history)
        repeat(21) { s.addPoint(Side.RED) }
        assertEquals(Side.RED, s.state.value.gameWinner)
        assertEquals(0, history.total)

        s.startNextGame()
        s.addPoint(Side.RED)
        assertEquals(0, history.total)
        history.clear()
    }

    @Test
    fun `再来一场之后可以再记一条`() {
        val history = freshHistory()
        val s = store(history)

        repeat(21) { s.addPoint(Side.RED) }
        s.startNextGame()
        repeat(21) { s.addPoint(Side.RED) }
        assertEquals(1, history.total)

        s.rematch()
        repeat(21) { s.addPoint(Side.BLUE) }
        s.startNextGame()
        repeat(21) { s.addPoint(Side.BLUE) }

        assertEquals(2, history.total)
        assertEquals(Side.BLUE, history.items[0].winner)   // 新的排前面
        assertEquals(1, history.blueWins)
        assertEquals(1, history.redWins)
        history.clear()
    }

    @Test
    fun `自定义赛制下也能记，并记下赛制与双打`() {
        val history = freshHistory()
        val state = MatchState(
            mode = ScoringMode.CUSTOM,
            customRules = BadmintonRules.custom(5, null, 1),
        ).copy(format = MatchFormat.DOUBLES)
            .setPlayers(listOf("A1", "A2"), Side.RED)
            .setPlayers(listOf("B1", "B2"), Side.BLUE)

        val s = store(history, state)
        repeat(5) { s.addPoint(Side.RED) }

        assertTrue(s.state.value.isMatchOver)     // 一局定胜负，5 分就结束
        assertEquals(1, history.total)
        val r = history.items[0]
        assertEquals(ScoringMode.CUSTOM, r.mode)
        assertEquals(MatchFormat.DOUBLES, r.format)
        assertEquals("A1 / A2", r.redName)
        history.clear()
    }

    @Test
    fun `记录里各方显示自己赢的局数，不是同一个大比分`() {
        val history = freshHistory()
        val s = store(history)

        // 2-1：红方赢两局、蓝方赢一局
        playGame(s, 21, 19); s.startNextGame()
        playGame(s, 15, 21); s.startNextGame()
        playGame(s, 21, 18)

        assertEquals(1, history.total)
        val r = history.items[0]
        assertEquals("2-1", r.gamesLine)
        assertEquals(2, r.gamesOf(Side.RED))
        assertEquals(1, r.gamesOf(Side.BLUE))
        assertEquals(2, r.redGames)
        assertEquals(1, r.blueGames)
        history.clear()
    }

    @Test
    fun `统计数字对得上`() {
        val history = freshHistory()
        for ((red, blue) in listOf(21 to 5, 5 to 21, 21 to 19)) {
            val s = store(history, MatchState(mode = ScoringMode.SINGLE21))
            repeat(maxOf(red, blue)) {
                if (s.state.value.redPoints < red) s.addPoint(Side.RED)
                if (s.state.value.bluePoints < blue) s.addPoint(Side.BLUE)
            }
        }
        assertEquals(3, history.total)
        assertEquals(2, history.redWins)
        assertEquals(1, history.blueWins)
        history.clear()
    }
}
