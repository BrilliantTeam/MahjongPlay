package com.mahjongplay.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

interface TextFormatting {
    fun toText(): Component
}

object MJColor {
    val GREEN: TextColor = TextColor.color(0xCFFFC0)
    val RED: TextColor = TextColor.color(0xFF7A7A)
    val DARK_RED: TextColor = TextColor.color(0xFF5555)
    val YELLOW: TextColor = TextColor.color(0xF6E0A4)
    val GOLD: TextColor = TextColor.color(0xF6C236)
    val LIGHT_PURPLE: TextColor = TextColor.color(0xE6BBF6)
    val WHITE: TextColor = TextColor.color(0xFAEDE7)
    val AQUA: TextColor = TextColor.color(0x7AD3FF)
    val BLUE: TextColor = TextColor.color(0x7A8CFF)
    val DARK_PURPLE: TextColor = TextColor.color(0xC27AFF)

    val GRAY: TextColor = NamedTextColor.GRAY
    val DARK_GRAY: TextColor = NamedTextColor.DARK_GRAY
    val DARK_AQUA: TextColor = NamedTextColor.DARK_AQUA
    val DARK_GREEN: TextColor = NamedTextColor.DARK_GREEN
}

private val MM = MiniMessage.miniMessage()

const val SEP = "<dark_gray>｜</dark_gray>"

val MESSAGE_PREFIX: Component = MM.deserialize("<gray>｜<gold>系統<gray>｜<white>飯娘：")

fun styled(text: String, color: TextColor): Component = MM.deserialize(text).colorIfAbsent(color)

fun chatLine(text: String, color: TextColor): Component =
    MESSAGE_PREFIX.append(styled(if (MM.stripTags(text).takeLast(1) in "。！？：") text else "$text。", color))

fun machiText(machi: Map<com.mahjongplay.model.MahjongTile, Boolean>): Component {
    var text = Component.empty()
    machi.entries.forEachIndexed { index, (tile, winnable) ->
        text = text.append(
            if (winnable) com.mahjongplay.interaction.ActionBarHUD.tile(tile)
            else com.mahjongplay.interaction.ActionBarHUD.tile(tile, MJColor.DARK_GRAY)
        )
    }
    if (machi.isNotEmpty() && machi.values.none { it }) {
        text = text.append(Component.text("（無役）", MJColor.RED))
    }
    return text
}

fun machiText(machi: List<com.mahjongplay.model.MahjongTile>): Component =
    machiText(machi.associateWith { true })

fun Player.msg(text: String, color: TextColor) {
    sendMessage(chatLine(text, color))
}

fun Player.actionBarMsg(msg: Component) {
    sendActionBar(msg)
}
