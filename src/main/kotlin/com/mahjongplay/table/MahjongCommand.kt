package com.mahjongplay.table

import com.mahjongplay.game.MahjongPlayer
import com.mahjongplay.model.MahjongGameBehavior
import com.mahjongplay.util.MESSAGE_PREFIX
import com.mahjongplay.util.msg
import net.kyori.adventure.text.Component
import com.mahjongplay.util.MJColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class MahjongCommand(private val manager: MahjongTableManager) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(MESSAGE_PREFIX.append(Component.text("Only players can use this command.", MJColor.RED)))
            return true
        }

        if (args.firstOrNull()?.lowercase() == "action") {
            if (!sender.hasPermission("mahjongplay.command.action")) {
                sender.msg("你沒有權限執行麻將操作", MJColor.RED)
                return true
            }
            handleAction(sender, args)
            return true
        }

        MahjongPanel.open(manager, sender)
        return true
    }

    private fun handleAction(player: Player, args: Array<out String>) {
        if (args.size < 2) return
        val session = manager.getSessionForPlayer(player.uniqueId.toString()) ?: return
        val mjPlayer = session.game.realPlayers.find { it.uuid == player.uniqueId.toString() } as? MahjongPlayer ?: return

        val behavior = try { MahjongGameBehavior.valueOf(args[1].uppercase()) } catch (_: Exception) { return }
        val data = if (args.size > 2) args.drop(2).joinToString(" ") else ""

        mjPlayer.resolveAction(behavior, data)
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> =
        emptyList()
}
