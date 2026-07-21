package com.mahjongplay.model

import com.mahjongplay.util.TextFormatting
import net.kyori.adventure.text.Component

/**
 * 流局類型
 * 不包含三家和 (sanchahou)
 */
enum class ExhaustiveDraw(
    private val displayName: String
) : TextFormatting {
    NORMAL("流局"),
    KYUUSHU_KYUUHAI("九種九牌"),
    SUUFON_RENDA("四風連打"),
    SUUCHA_RIICHI("四家立直"),
    SUUKAIKAN("四開槓");

    override fun toText(): Component = Component.text(displayName)
}
