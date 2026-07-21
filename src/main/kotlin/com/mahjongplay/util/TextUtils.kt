package com.mahjongplay.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player

/**
 * Formatting interface for game elements that can be represented as text.
 * Replaces the original Fabric mod's TextFormatting interface.
 */
interface TextFormatting {
    fun toText(): Component
}

val MESSAGE_PREFIX: Component =
    LegacyComponentSerializer.legacySection().deserialize("§7｜§6系統§7｜§f飯娘：§7")

fun Player.msg(text: String, color: NamedTextColor) {
    sendMessage(MESSAGE_PREFIX.append(Component.text(text, color)))
}

fun Player.actionBarMsg(msg: Component) {
    sendActionBar(MESSAGE_PREFIX.append(msg))
}
