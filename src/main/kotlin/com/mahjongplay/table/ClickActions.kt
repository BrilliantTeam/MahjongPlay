package com.mahjongplay.table

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.event.player.PlayerCustomClickEvent
import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.api.BinaryTagHolder
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ClickActions : Listener {

    private val KEY = Key.key("mahjongplay", "click")

    private class Stored(val action: (Player) -> Unit, options: ClickCallback.Options) {
        val startedAt = System.nanoTime()
        val lifetime = try {
            options.lifetime().toNanos()
        } catch (e: ArithmeticException) {
            Long.MAX_VALUE
        }
        var remainingUses = options.uses()

        fun valid(now: Long): Boolean =
            (remainingUses == ClickCallback.UNLIMITED_USES || remainingUses > 0) &&
                (lifetime == Long.MAX_VALUE || now - startedAt < lifetime)
    }

    private val actions = ConcurrentHashMap<String, Stored>()

    fun create(options: ClickCallback.Options, action: (Player) -> Unit): ClickEvent {
        val now = System.nanoTime()
        actions.values.removeIf { !it.valid(now) }
        val token = "t" + UUID.randomUUID().toString().replace("-", "")
        actions[token] = Stored(action, options)
        return ClickEvent.custom(KEY, BinaryTagHolder.binaryTagHolder("\"$token\""))
    }

    @EventHandler
    fun onCustomClick(event: PlayerCustomClickEvent) {
        if (event.identifier != KEY) return
        val player = (event.commonConnection as? PlayerGameConnection)?.player ?: return
        val token = event.tag?.string()?.trim('"', '\'') ?: return

        var action: ((Player) -> Unit)? = null
        actions.computeIfPresent(token) { _, stored ->
            if (!stored.valid(System.nanoTime())) return@computeIfPresent null
            action = stored.action
            if (stored.remainingUses != ClickCallback.UNLIMITED_USES) stored.remainingUses--
            if (stored.remainingUses == 0) null else stored
        }
        action?.invoke(player)
    }
}
