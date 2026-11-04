package com.mahjongplay.model

import com.mahjongplay.util.TextFormatting
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
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
    var localYaku: Boolean = false,
    var taiwan: Boolean = false
) {
    val isSanma: Boolean get() = playerCount == 3
    fun toJsonString(): String = Json.encodeToString(serializer(), this)

    fun toComponents(): List<Component> {
        val thinking = "${thinkingTime.base} + ${thinkingTime.extra} 秒"
        val spectating = toggleRow("旁觀", spectate)
        if (taiwan) return listOf(
            Component.text("規則（台灣十六張）", MJColor.WHITE),
            row("局數", length.displayText),
            row("思考時間", thinking),
            row("起始點數", "$startingPoints"),
            row("底／台", "$TAIWAN_BASE／$TAIWAN_PER_TAI"),
            row("花牌", "8 張，摸到自動補牌"),
            row("留牌", "$TAIWAN_RESERVE 張"),
            row("莊家", "莊家胡牌或流局連莊，每連一莊多 2 台"),
            spectating
        )
        return listOf(
            Component.text("規則", MJColor.WHITE),
            row("局數", length.displayText),
            row("思考時間", thinking),
            row("起始點數", "$startingPoints"),
            row("1 位必要點數", "$minPointsToWin"),
            row("翻縛", "${minimumHan.han}"),
            spectating,
            row("赤寶牌", "${redFive.quantity}"),
            toggleRow("食斷", openTanyao)
        )
    }

    private fun row(label: String, value: String, color: TextColor = MJColor.WHITE): Component =
        Component.text(" - ", MJColor.DARK_GRAY)
            .append(Component.text("$label：", MJColor.GRAY))
            .append(Component.text(value, color))

    private fun toggleRow(label: String, enabled: Boolean): Component =
        row(label, if (enabled) "開啟" else "關閉", if (enabled) MJColor.GREEN else MJColor.RED)

    companion object {
        fun fromJsonString(jsonString: String): MahjongRule = Json.decodeFromString(serializer(), jsonString)
        const val MAX_POINTS = 200000
        const val MIN_POINTS = 100
        const val TAIWAN_BASE = 500
        const val TAIWAN_PER_TAI = 200
        const val TAIWAN_RESERVE = 16
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
