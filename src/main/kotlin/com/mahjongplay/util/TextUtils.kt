package com.mahjongplay.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player

interface TextFormatting {
    fun toText(): Component
}

object MJColor {
    val GREEN: TextColor = TextColor.color(0xCFFFC0)
    val RED: TextColor = TextColor.color(0xFF7A7A)
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

val MESSAGE_PREFIX: Component =
    LegacyComponentSerializer.legacySection().deserialize("§7｜§6系統§7｜§f飯娘：§7")

fun Player.msg(text: String, color: TextColor) {
    sendMessage(MESSAGE_PREFIX.append(Component.text(text, color)))
}

fun Player.actionBarMsg(msg: Component) {
    sendActionBar(MESSAGE_PREFIX.append(msg))
}
