package com.badminton.score.data

/**
 * 一次得分操作后产生的副作用，界面据此播放触感与动画。
 */
sealed interface ScoreEvent {
    data object None : ScoreEvent

    /** 普通得分。 */
    data class Point(val side: Side) : ScoreEvent

    /** 本局结束。 */
    data class GameWon(val side: Side, val red: Int, val blue: Int, val game: Int) : ScoreEvent

    /** 整场比赛结束。 */
    data class MatchWon(val side: Side, val redGames: Int, val blueGames: Int) : ScoreEvent

    /** 撤销或取消了一分。 */
    data object Undone : ScoreEvent
}

/**
 * 纯函数式计分引擎：所有规则变更都在这里完成，界面只负责展示与调用。
 *
 * 全部是纯函数（输入状态 → 输出新状态），所以规则可以脱离界面单独测试。
 */
object ScoreEngine {

    /**
     * 给某一方加一分，返回新状态与产生的副作用。
     */
    fun applyPoint(side: Side, state: MatchState): Pair<MatchState, ScoreEvent> {
        if (state.isMatchOver || state.gameWinner != null) return state to ScoreEvent.None

        var next = state.copy(
            redPoints = state.redPoints + if (side == Side.RED) 1 else 0,
            bluePoints = state.bluePoints + if (side == Side.BLUE) 1 else 0,
        )
        next = next.copy(
            log = next.log + MatchLogEntry(
                side = side,
                redPoints = next.redPoints,
                bluePoints = next.bluePoints,
                game = next.currentGame,
                isCorrection = false,
            )
        )

        // 发球权。
        // 每球得分制：得分方获得发球权；旧制发球得分制：发球方得分才换发球。
        val wasServing = next.server == side
        if (next.rules.rallyPoint || wasServing) {
            // 双打：接发球方夺回发球权时，换这对里的另一个人发球。
            // （发球方自己连续得分时，还是同一个人发，只是左右发球区轮换。）
            if (next.format == MatchFormat.DOUBLES && !wasServing) {
                next = if (side == Side.RED) next.copy(redServeIndex = 1 - next.redServeIndex)
                else next.copy(blueServeIndex = 1 - next.blueServeIndex)
            }
            next = next.copy(server = side)
        }

        val winner = next.rules.winner(next.redPoints, next.bluePoints)
            ?: return next to ScoreEvent.Point(side)

        // 本局结束：由本局胜方在下一局先发球。
        next = next.copy(
            gameScores = next.gameScores + GameScore(next.currentGame, next.redPoints, next.bluePoints),
            redGames = next.redGames + if (winner == Side.RED) 1 else 0,
            blueGames = next.blueGames + if (winner == Side.BLUE) 1 else 0,
        )

        val event = ScoreEvent.GameWon(winner, next.redPoints, next.bluePoints, next.currentGame)

        if (next.games(winner) >= next.rules.gamesToWin) {
            next = next.copy(isMatchOver = true, matchWinner = winner)
            return next to ScoreEvent.MatchWon(winner, next.redGames, next.blueGames)
        }
        return next to event
    }

    /**
     * 清空本局比分，进入下一局（逐分记录保留，按局号分组）。
     */
    fun startNextGame(state: MatchState): MatchState {
        val winner = state.gameWinner ?: return state
        if (state.isMatchOver) return state
        return state.copy(
            currentGame = state.currentGame + 1,
            redPoints = 0,
            bluePoints = 0,
            server = winner,
        )
    }
}
