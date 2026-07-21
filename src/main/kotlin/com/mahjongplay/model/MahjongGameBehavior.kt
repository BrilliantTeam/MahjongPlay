package com.mahjongplay.model

import com.mahjongplay.util.TextFormatting
import net.kyori.adventure.text.Component

enum class MahjongGameBehavior(
    private val displayName: String
) : TextFormatting {
    CHII("吃"),
    PON_OR_CHII("碰/吃"),
    PON("碰"),
    KAN("槓"),
    MINKAN("明槓"),
    ANKAN("暗槓"),
    ANKAN_OR_KAKAN("暗槓/加槓"),
    KAKAN("加槓"),
    CHAN_KAN("搶槓"),
    RIICHI("立直"),
    DOUBLE_RIICHI("雙立直"),
    KITA("拔北"),
    RON("榮"),
    TSUMO("自摸"),
    KYUUSHU_KYUUHAI("九種九牌"),

    EXHAUSTIVE_DRAW("流局"),
    DISCARD("出牌"),
    SKIP("跳過"),
    GAME_START("遊戲開始"),
    GAME_OVER("遊戲結束"),
    SCORE_SETTLEMENT("分數結算"),
    YAKU_SETTLEMENT("役結算"),
    COUNTDOWN_TIME("倒數"),
    AUTO_ARRANGE("自動理牌"),
    MACHI("聽牌");

    override fun toText(): Component = Component.text(displayName)
}
