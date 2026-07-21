package com.mahjongplay.model

import com.mahjongplay.util.TextFormatting
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import com.mahjongplay.util.MJColor

@Serializable
data class MahjongRule(
    var length: GameLength = GameLength.TWO_WIND,
    var playerCount: Int = 4,
    var thinkingTime: ThinkingTime = ThinkingTime.NORMAL,
    var startingPoints: Int = 25000,
    var minPointsToWin: Int = 30000,
    var minimumHan: MinimumHan = MinimumHan.ONE,
    var spectate: Boolean = true,
    var redFive: RedFive = RedFive.THREE,
    var openTanyao: Boolean = true,
    var localYaku: Boolean = false
) {
    val isSanma: Boolean get() = playerCount == 3
    fun toJsonString(): String = Json.encodeToString(serializer(), this)

    fun toComponents(): List<Component> {
        val enable = Component.text("開啟").color(MJColor.GREEN)
        val disable = Component.text("關閉").color(MJColor.GREEN)
        return listOf(
            Component.text("規則").color(MJColor.RED).decorate(TextDecoration.BOLD),
            Component.text(" - 局數：").color(MJColor.YELLOW)
                .append(length.toText().color(MJColor.GREEN)),
            Component.text(" - 思考時間：").color(MJColor.YELLOW)
                .append(Component.text("${thinkingTime.base}").color(MJColor.AQUA))
                .append(Component.text(" + ").color(MJColor.RED))
                .append(Component.text("${thinkingTime.extra}").color(MJColor.AQUA))
                .append(Component.text(" 秒").color(MJColor.GREEN)),
            Component.text(" - 起始點數：").color(MJColor.YELLOW)
                .append(Component.text("$startingPoints").color(MJColor.GREEN)),
            Component.text(" - 1位必要點數：").color(MJColor.YELLOW)
                .append(Component.text("$minPointsToWin").color(MJColor.GREEN)),
            Component.text(" - 翻縛：").color(MJColor.YELLOW)
                .append(Component.text("${minimumHan.han}").color(MJColor.GREEN)),
            Component.text(" - 旁觀：").color(MJColor.YELLOW)
                .append(if (spectate) enable else disable),
            Component.text(" - 赤寶牌：").color(MJColor.YELLOW)
                .append(Component.text("${redFive.quantity}").color(MJColor.GREEN)),
            Component.text(" - 食斷：").color(MJColor.YELLOW)
                .append(if (openTanyao) enable else disable)
        )
    }

    companion object {
        fun fromJsonString(jsonString: String): MahjongRule = Json.decodeFromString(serializer(), jsonString)
        const val MAX_POINTS = 200000
        const val MIN_POINTS = 100
    }

    enum class GameLength(
        private val startingWind: Wind,
        val rounds: Int,
        val finalRound: Pair<Wind, Int>,
        val displayText: String
    ) : TextFormatting {
        ONE_GAME(Wind.EAST, 1, Wind.EAST to 3, "一局"),
        EAST(Wind.EAST, 4, Wind.SOUTH to 3, "東風"),
        TWO_WIND(Wind.EAST, 8, Wind.WEST to 3, "半莊");

        fun getStartingRound(): MahjongRound = MahjongRound(wind = startingWind)

        fun getRounds(playerCount: Int): Int = when (this) {
            ONE_GAME -> 1
            else -> (rounds / 4) * playerCount
        }

        fun getFinalRound(playerCount: Int): Pair<Wind, Int> = when (this) {
            ONE_GAME -> finalRound
            else -> finalRound.first to (playerCount - 1)
        }

        override fun toText(): Component = Component.text(displayText)
    }

    enum class MinimumHan(val han: Int) : TextFormatting {
        ONE(1),
        TWO(2),
        FOUR(4),
        YAKUMAN(13);

        override fun toText(): Component = Component.text(han.toString())
    }

    enum class ThinkingTime(
        val base: Int,
        val extra: Int
    ) : TextFormatting {
        VERY_SHORT(3, 5),
        SHORT(5, 10),
        NORMAL(5, 20),
        LONG(60, 0),
        VERY_LONG(300, 0);

        override fun toText(): Component = Component.text("$base + $extra s")
    }

    enum class RedFive(val quantity: Int) : TextFormatting {
        NONE(0),
        THREE(3),
        FOUR(4);

        override fun toText(): Component = Component.text(quantity.toString())
    }
}
