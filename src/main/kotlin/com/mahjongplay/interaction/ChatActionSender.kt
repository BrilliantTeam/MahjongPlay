package com.mahjongplay.interaction

import com.mahjongplay.game.*
import com.mahjongplay.model.*
import com.mahjongplay.util.MESSAGE_PREFIX
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

object ChatActionSender {

    fun sendDiscardPrompt(player: Player, gameId: UUID, cannotDiscard: List<MahjongTile>, hands: List<MahjongTile>) {
        val msg = MESSAGE_PREFIX
            .append(Component.text("請出牌（點擊手牌實體）", NamedTextColor.YELLOW))
        player.sendMessage(msg)
    }

    fun sendActionPrompt(player: Player, gameId: UUID, actions: List<MahjongGameBehavior>, tile: MahjongTile?) {
        var msg = MESSAGE_PREFIX

        actions.filter { it != MahjongGameBehavior.SKIP }.forEach { action ->
            val label = action.toText()
            val cmd = "/mahjong action ${action.name} ${tile?.code ?: ""}"
            val button = Component.text("[", NamedTextColor.GRAY)
                .append(label.color(actionColor(action)).decorate(TextDecoration.BOLD))
                .append(Component.text("]", NamedTextColor.GRAY))
                .clickEvent(ClickEvent.runCommand(cmd))
                .append(Component.text(" "))
            msg = msg.append(button)
        }

        val skipCmd = "/mahjong action SKIP"
        val skipButton = Component.text("[", NamedTextColor.GRAY)
            .append(Component.text("跳過", NamedTextColor.DARK_GRAY))
            .append(Component.text("]", NamedTextColor.GRAY))
            .clickEvent(ClickEvent.runCommand(skipCmd))
        msg = msg.append(skipButton)

        player.sendMessage(msg)
    }

    fun sendChiiOptions(player: Player, gameId: UUID, tile: MahjongTile, pairs: List<Pair<MahjongTile, MahjongTile>>) {
        var msg = MESSAGE_PREFIX.append(Component.text("選擇吃的組合：", NamedTextColor.GOLD))
        pairs.forEach { (a, b) ->
            val cmd = "/mahjong action CHII ${a.code},${b.code}"
            val label = "${a.displayName}+${b.displayName}"
            val button = Component.text("[$label]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand(cmd))
                .append(Component.text(" "))
            msg = msg.append(button)
        }
        val skipCmd = "/mahjong action SKIP"
        msg = msg.append(Component.text("[跳過]", NamedTextColor.DARK_GRAY).clickEvent(ClickEvent.runCommand(skipCmd)))
        player.sendMessage(msg)
    }

    fun sendAnkanKakanOptions(player: Player, tiles: Set<MahjongTile>, kanType: String) {
        var msg = MESSAGE_PREFIX.append(Component.text("選擇${if (kanType == "ankan") "暗槓" else "加槓"}的牌：", NamedTextColor.GOLD))
        tiles.forEach { tile ->
            val cmd = "/mahjong action ANKAN_OR_KAKAN ${tile.code}"
            val button = Component.text("[${tile.displayName}]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand(cmd))
                .append(Component.text(" "))
            msg = msg.append(button)
        }
        msg = msg.append(Component.text("[跳過]", NamedTextColor.DARK_GRAY).clickEvent(ClickEvent.runCommand("/mahjong action SKIP")))
        player.sendMessage(msg)
    }

    fun sendRiichiOptions(player: Player, tilePairs: List<Pair<MahjongTile, List<MahjongTile>>>) {
        var msg = MESSAGE_PREFIX.append(Component.text("立直 - 選擇打出的牌：", NamedTextColor.GOLD))
        tilePairs.forEach { (tile, machi) ->
            val machiStr = machi.joinToString(",") { it.displayName }
            val cmd = "/mahjong action RIICHI ${tile.code}"
            val button = Component.text("[${tile.displayName}]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand(cmd))
                .append(Component.text("→$machiStr ", NamedTextColor.YELLOW))
            msg = msg.append(button)
        }
        msg = msg.append(Component.text("[跳過]", NamedTextColor.DARK_GRAY).clickEvent(ClickEvent.runCommand("/mahjong action SKIP")))
        player.sendMessage(msg)
    }

    private fun actionColor(action: MahjongGameBehavior): NamedTextColor = when (action) {
        MahjongGameBehavior.CHII -> NamedTextColor.GREEN
        MahjongGameBehavior.PON, MahjongGameBehavior.PON_OR_CHII -> NamedTextColor.BLUE
        MahjongGameBehavior.KAN, MahjongGameBehavior.MINKAN, MahjongGameBehavior.ANKAN, MahjongGameBehavior.KAKAN, MahjongGameBehavior.ANKAN_OR_KAKAN -> NamedTextColor.AQUA
        MahjongGameBehavior.RON -> NamedTextColor.RED
        MahjongGameBehavior.TSUMO -> NamedTextColor.GOLD
        MahjongGameBehavior.RIICHI, MahjongGameBehavior.DOUBLE_RIICHI -> NamedTextColor.LIGHT_PURPLE
        else -> NamedTextColor.WHITE
    }
}
