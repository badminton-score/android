package com.badminton.score.data

import kotlinx.serialization.Serializable

// MARK: - 双方

@Serializable
enum class Side {
    RED, BLUE;

    val opponent: Side get() = if (this == RED) BLUE else RED

    /** 默认队名，可在计分界面里改。 */
    val defaultName: String get() = if (this == RED) "红方" else "蓝方"
}

// MARK: - 单打 / 双打

@Serializable
enum class MatchFormat(val title: String, val subtitle: String) {
    SINGLES("单打", "一人对一人"),
    DOUBLES("双打", "两人对两人 · 发球轮转");

    /** 每方几个人。 */
    val playersPerSide: Int get() = if (this == SINGLES) 1 else 2
}

// MARK: - 预设赛制

/** 应用内置的几种羽毛球常见计分规则。 */
@Serializable
enum class ScoringMode {
    /** 现行 BWF 规则：21 分制，20 平后需净胜 2 分，29 平后 30 分封顶。 */
    BWF21,

    /** 21 分制，但取消封顶，必须净胜 2 分（业余长盘）。 */
    CLASSIC21,

    /** 旧制 15 分制，发球得分，14 平后需净胜 2 分，21 分封顶。 */
    TRADITIONAL15,

    /** 旧制 11 分制，发球得分，10 平后需净胜 2 分，15 分封顶。 */
    TRADITIONAL11,

    /** 一局定胜负的 21 分制（不加局）。 */
    SINGLE21,

    /** 自己定：每局几分、要不要封顶、打几局。 */
    CUSTOM;

    val title: String
        get() = when (this) {
            BWF21 -> "21 分制"
            CLASSIC21 -> "21 分长盘"
            TRADITIONAL15 -> "15 分制"
            TRADITIONAL11 -> "11 分制"
            SINGLE21 -> "一局 21 分"
            CUSTOM -> "自定义"
        }

    val subtitle: String
        get() = when (this) {
            BWF21 -> "正式比赛 · 三局两胜"
            CLASSIC21 -> "无封顶 · 必须净胜 2 分"
            TRADITIONAL15 -> "旧制 · 两局三胜"
            TRADITIONAL11 -> "旧制 · 三局两胜"
            SINGLE21 -> "快速对战 · 一局定胜负"
            CUSTOM -> "自己定分数与局数"
        }

    /** 规则概要，界面上显示一行说明用。 */
    val detail: String
        get() {
            if (this == CUSTOM) return "自己定每局几分、要不要封顶、打几局"
            val r = rules
            var text = "${r.gamesToWin} 局获胜 · ${r.pointsToWin} 分"
            text += when {
                r.cap != null && r.deuceAt != null -> " · ${r.deuceAt} 平后净胜 2 分 · ${r.cap} 分封顶"
                r.deuceAt != null -> " · ${r.deuceAt} 平后净胜 2 分 · 无封顶"
                else -> " · 先到即胜"
            }
            return text
        }

    val rules: BadmintonRules
        get() = when (this) {
            BWF21 -> BadmintonRules(this, 21, 30, 20, 2, 3, true)
            CLASSIC21 -> BadmintonRules(this, 21, null, 20, 2, 3, true)
            TRADITIONAL15 -> BadmintonRules(this, 15, 21, 14, 2, 3, false)
            TRADITIONAL11 -> BadmintonRules(this, 11, 15, 10, 2, 3, false)
            SINGLE21 -> BadmintonRules(this, 21, 30, 20, 1, 1, true)
            CUSTOM -> BadmintonRules.defaultCustom
        }

    /** 该赛制下整场比赛是否可能打到 15 分以上（界面自适应用）。 */
    val isLongForm: Boolean get() = rules.pointsToWin >= 21
}

// MARK: - 赛制参数

@Serializable
data class BadmintonRules(
    val mode: ScoringMode,
    /** 一局的基本获胜分数。 */
    val pointsToWin: Int,
    /** 封顶分（null 表示必须净胜 2 分，无封顶）。 */
    val cap: Int?,
    /** 从该分数起进入平分（deuce），需要净胜 2 分。 */
    val deuceAt: Int?,
    /** 赢下几局即赢得整场比赛。 */
    val gamesToWin: Int,
    /** 这场比赛最多进行几局。 */
    val maximumGames: Int,
    /** 是否每球得分制。旧制为发球得分制。 */
    val rallyPoint: Boolean,
) {
    /** 达到该分数即锁定胜局（无论对手多少分）。 */
    val absoluteWin: Int get() = cap ?: Int.MAX_VALUE

    /** 是否进入平分：**双方都**到 deuceAt 才算。 */
    fun isAtDeuce(red: Int, blue: Int): Boolean {
        val d = deuceAt ?: return false
        return red >= d && blue >= d
    }

    /**
     * 判定这一局的胜方。
     *
     * 关键在「平分」是从**双方都到 deuceAt** 才开始算的：
     * - 20:19 时红方再得一分是 21:19，直接赢（对手还没到 20，不触发平分）
     * - 20:20 之后才必须净胜 2 分
     * - 先到 absoluteWin 直接赢
     */
    fun winner(red: Int, blue: Int): Side? {
        val mine = maxOf(red, blue)
        val theirs = minOf(red, blue)
        if (mine < pointsToWin) return null
        // 封顶：到顶即胜
        if (mine >= absoluteWin) return if (red > blue) Side.RED else Side.BLUE
        if (!isAtDeuce(red, blue)) return if (red > blue) Side.RED else Side.BLUE
        return if (mine - theirs >= 2) (if (red > blue) Side.RED else Side.BLUE) else null
    }

    companion object {
        val pointsRange = 5..50
        val capRange = 1..20
        val gameOptions = listOf(1, 3, 5)

        /** 「自定义」的初始值：一套最常见的 21 分制。 */
        val defaultCustom = BadmintonRules(
            mode = ScoringMode.CUSTOM,
            pointsToWin = 21, cap = 30, deuceAt = 20,
            gamesToWin = 2, maximumGames = 3, rallyPoint = true,
        )

        /**
         * 按「目标分 / 封顶加成 / 总局数」造一套自定义规则，并把各项夹到合法范围。
         *
         * @param capBonus 封顶比目标分高多少分；传 null 表示不封顶
         */
        fun custom(points: Int, capBonus: Int?, maxGames: Int): BadmintonRules {
            val p = points.coerceIn(pointsRange)
            val games = if (gameOptions.contains(maxGames)) maxGames else 3
            val toWin = games / 2 + 1
            val cap = capBonus?.let { p + it.coerceIn(capRange) }
            return BadmintonRules(
                mode = ScoringMode.CUSTOM,
                pointsToWin = p,
                cap = cap,
                // 平分从「目标分 - 1」开始，和 BWF 的 20/21 关系一致
                deuceAt = maxOf(1, p - 1),
                gamesToWin = toWin,
                maximumGames = games,
                rallyPoint = true,
            )
        }
    }

    /** 封顶相对目标分高多少分；无封顶返回 null。 */
    val capBonus: Int? get() = cap?.let { it - pointsToWin }
}

// MARK: - 一次计分动作的记录

@Serializable
data class MatchLogEntry(
    val side: Side,
    val redPoints: Int,
    val bluePoints: Int,
    val game: Int,
    val isCorrection: Boolean,
) {
    val text: String get() = "${game}局  $redPoints : $bluePoints"
}

// MARK: - 一局结束后的比分

@Serializable
data class GameScore(
    val game: Int,
    val red: Int,
    val blue: Int,
) {
    val winner: Side get() = if (red > blue) Side.RED else Side.BLUE
}

// MARK: - 比赛状态

@Serializable
data class MatchState(
    val mode: ScoringMode,
    val redName: String = Side.RED.defaultName,
    val blueName: String = Side.BLUE.defaultName,
    val redPoints: Int = 0,
    val bluePoints: Int = 0,
    val redGames: Int = 0,
    val blueGames: Int = 0,
    val currentGame: Int = 1,
    val server: Side = Side.RED,
    val startedByRed: Boolean = true,
    val isMatchOver: Boolean = false,
    val matchWinner: Side? = null,
    val gameScores: List<GameScore> = emptyList(),
    val log: List<MatchLogEntry> = emptyList(),

    /** 单打还是双打。 */
    val format: MatchFormat = MatchFormat.SINGLES,
    /** 每方的队员名。单打 1 个、双打 2 个。 */
    val redPlayers: List<String> = emptyList(),
    val bluePlayers: List<String> = emptyList(),
    /** 双打里各方当前该谁发球（0 或 1）。 */
    val redServeIndex: Int = 0,
    val blueServeIndex: Int = 0,

    /** 仅 CUSTOM 用：用户自己定的那套规则。旧存档解出来是 null，不会崩。 */
    val customRules: BadmintonRules? = null,
) {
    /** 本场生效的规则。 */
    val rules: BadmintonRules
        get() = if (mode == ScoringMode.CUSTOM) (customRules ?: BadmintonRules.defaultCustom) else mode.rules

    /** 本局胜方；本局还没结束则是 null。 */
    val gameWinner: Side? get() = rules.winner(redPoints, bluePoints)

    fun games(side: Side): Int = if (side == Side.RED) redGames else blueGames
    fun points(side: Side): Int = if (side == Side.RED) redPoints else bluePoints
    val serveBox: String get() = if (points(server) % 2 == 0) "右区" else "左区"

    // MARK: 队员

    /** 某方的队员列表。第一人回落到 redName / blueName，双打第二人没填就叫「队友」。 */
    fun players(side: Side): List<String> {
        val primary = if (side == Side.RED) redName else blueName
        if (format == MatchFormat.SINGLES) return listOf(primary)
        val extra = if (side == Side.RED) redPlayers else bluePlayers
        val second = extra.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "队友"
        return listOf(primary, second)
    }

    /** 某方的队名。单打就是那个人；双打是「A / B」。 */
    fun name(side: Side): String = players(side).joinToString(" / ")

    /** 当前发球的队员在队里的序号。 */
    fun serveIndex(side: Side): Int {
        val i = if (side == Side.RED) redServeIndex else blueServeIndex
        return if (format == MatchFormat.SINGLES) 0 else ((i % 2) + 2) % 2
    }

    /** 某个队员现在是否在发球。 */
    fun isServing(side: Side, index: Int): Boolean =
        format == MatchFormat.DOUBLES && server == side && serveIndex(side) == index

    fun setPlayers(names: List<String>, side: Side): MatchState {
        val cleaned = names.map { it.trim() }
        val primary = cleaned.firstOrNull { it.isNotEmpty() } ?: side.defaultName
        return if (side == Side.RED) copy(redName = primary, redPlayers = cleaned)
        else copy(blueName = primary, bluePlayers = cleaned)
    }

    /** 切换单打/双打时把队员数组对齐到需要的长度。 */
    fun normalizePlayers(): MatchState {
        var s = this
        for (side in listOf(Side.RED, Side.BLUE)) {
            val need = format.playersPerSide
            var list = s.players(side)
            if (list.size > need) list = list.take(need)
            while (list.size < need) list = list + "队友"
            s = s.setPlayers(list, side)
        }
        return s.copy(
            redServeIndex = redServeIndex % maxOf(1, format.playersPerSide),
            blueServeIndex = blueServeIndex % maxOf(1, format.playersPerSide),
        )
    }

    /** 某方是否处于「只差一球就赢下这一局」。 */
    fun isGamePoint(side: Side): Boolean {
        if (gameWinner != null || isMatchOver) return false
        val probe = copy(
            redPoints = redPoints + if (side == Side.RED) 1 else 0,
            bluePoints = bluePoints + if (side == Side.BLUE) 1 else 0,
        )
        return probe.rules.winner(probe.redPoints, probe.bluePoints) == side
    }

    /** 某方是否处于「只差一球就赢下整场」。 */
    fun isMatchPoint(side: Side): Boolean {
        if (gameWinner != null || isMatchOver) return false
        val probe = copy(
            redPoints = redPoints + if (side == Side.RED) 1 else 0,
            bluePoints = bluePoints + if (side == Side.BLUE) 1 else 0,
        )
        if (probe.rules.winner(probe.redPoints, probe.bluePoints) != side) return false
        return games(side) + 1 >= rules.gamesToWin
    }

    // MARK: 文案

    /** 单局比分，例如 "21-19"。 */
    val currentGameLine: String get() = "$redPoints-$bluePoints"

    /** 整场大比分，例如 "2-1"。 */
    val gamesLine: String get() = "$redGames-$blueGames"

    /** 历史各局，例如 "21-19 / 18-21 / 21-15"。 */
    val historyLine: String
        get() = gameScores.joinToString(" / ") { "${it.red}-${it.blue}" }.ifEmpty { "—" }
}
