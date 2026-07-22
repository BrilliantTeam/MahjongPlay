package com.mahjongplay.interaction

import com.mahjongplay.game.*
import com.mahjongplay.model.*
import com.mahjongplay.util.MESSAGE_PREFIX
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import com.mahjongplay.util.MJColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

object ChatActionSender {

    fun sendDiscardPrompt(player: Player, gameId: UUID, cannotDiscard: List<MahjongTile>, hands: List<MahjongTile>) {
        val msg = MESSAGE_PREFIX
            .append(Component.text("請出牌（點擊自己的手牌）", MJColor.YELLOW))
        player.sendMessage(msg)
    }

    fun sendActionPrompt(player: Player, gameId: UUID, actions: List<MahjongGameBehavior>, tile: MahjongTile?) {
        var msg = MESSAGE_PREFIX

        actions.filter { it != MahjongGameBehavior.SKIP }.forEach { action ->
            val label = action.toText()
            val cmd = "/mahjong action ${action.name} ${tile?.code ?: ""}"
            val button = Component.text("[", MJColor.GRAY)
                .append(label.color(actionColor(action)).decorate(TextDecoration.BOLD))
                .append(Component.text("]", MJColor.GRAY))
                .hoverEvent(hover(actionHint(action, tile)))
                .clickEvent(ClickEvent.runCommand(cmd))
                .append(Component.text(" "))
            msg = msg.append(button)
        }

        val skipCmd = "/mahjong action SKIP"
        val skipButton = Component.text("[", MJColor.GRAY)
            .append(Component.text("跳過", MJColor.DARK_GRAY))
            .append(Component.text("]", MJColor.GRAY))
            .hoverEvent(hover("不執行任何動作，讓這張牌過去"))
            .clickEvent(ClickEvent.runCommand(skipCmd))
        msg = msg.append(skipButton)

        player.sendMessage(msg)
    }

    private fun hover(text: String) = HoverEvent.showText(Component.text(text, MJColor.YELLOW))

    private fun actionHint(action: MahjongGameBehavior, tile: MahjongTile?): String {
        val target = tile?.displayName ?: "這張牌"
        return when (action) {
            MahjongGameBehavior.CHII -> "用手牌兩張與 $target 組成順子（副露後可能失去門清）"
            MahjongGameBehavior.PON -> "用手牌兩張相同的牌碰下 $target"
            MahjongGameBehavior.PON_OR_CHII -> "碰或吃 $target，點擊後再選要哪一種"
            MahjongGameBehavior.KAN, MahjongGameBehavior.MINKAN -> "用手牌三張相同的牌明槓 $target"
            MahjongGameBehavior.ANKAN -> "用手中四張相同的牌暗槓，不失門清"
            MahjongGameBehavior.KAKAN -> "把 $target 加到已經碰出的刻子上"
            MahjongGameBehavior.ANKAN_OR_KAKAN -> "暗槓或加槓，點擊後再選要槓哪張"
            MahjongGameBehavior.CHAN_KAN -> "搶槓，胡下對方正要加槓的 $target"
            MahjongGameBehavior.RIICHI -> "宣告立直，扣 1000 點並固定手牌"
            MahjongGameBehavior.DOUBLE_RIICHI -> "第一巡宣告立直，算雙立直"
            MahjongGameBehavior.KITA -> "拔北，把北風抽出當寶牌計算"
            MahjongGameBehavior.RON -> "榮和，胡下 $target 結束這局"
            MahjongGameBehavior.TSUMO -> "自摸和牌，結束這局"
            MahjongGameBehavior.KYUUSHU_KYUUHAI -> "宣告九種九牌，本局流局重來"
            else -> "執行「${PlainTextComponentSerializer.plainText().serialize(action.toText())}」"
        }
    }

    fun sendChiiOptions(player: Player, gameId: UUID, tile: MahjongTile, pairs: List<Pair<MahjongTile, MahjongTile>>) {
        var msg = MESSAGE_PREFIX.append(Component.text("選擇吃的組合：", MJColor.GOLD))
        pairs.forEach { (a, b) ->
            val cmd = "/mahjong action CHII ${a.code},${b.code}"
            val label = "${a.displayName}+${b.displayName}"
            val button = Component.text("[$label]", MJColor.GREEN)
                .hoverEvent(hover("用手牌的 ${a.displayName} 與 ${b.displayName} 吃下 ${tile.displayName}"))
                .clickEvent(ClickEvent.runCommand(cmd))
                .append(Component.text(" "))
            msg = msg.append(button)
        }
        val skipCmd = "/mahjong action SKIP"
        msg = msg.append(
            Component.text("[跳過]", MJColor.DARK_GRAY)
                .hoverEvent(hover("不吃，讓這張牌過去"))
                .clickEvent(ClickEvent.runCommand(skipCmd))
        )
        player.sendMessage(msg)
    }

    fun sendAnkanKakanOptions(player: Player, tiles: Set<MahjongTile>, kanType: String) {
        var msg = MESSAGE_PREFIX.append(Component.text("選擇${if (kanType == "ankan") "暗槓" else "加槓"}的牌：", MJColor.GOLD))
        val kanName = if (kanType == "ankan") "暗槓" else "加槓"
        tiles.forEach { tile ->
            val cmd = "/mahjong action ANKAN_OR_KAKAN ${tile.code}"
            val button = Component.text("[${tile.displayName}]", MJColor.AQUA)
                .hoverEvent(hover("${kanName} ${tile.displayName}，並從嶺上牌補一張"))
                .clickEvent(ClickEvent.runCommand(cmd))
                .append(Component.text(" "))
            msg = msg.append(button)
        }
        msg = msg.append(
            Component.text("[跳過]", MJColor.DARK_GRAY)
                .hoverEvent(hover("不槓，繼續這一巡"))
                .clickEvent(ClickEvent.runCommand("/mahjong action SKIP"))
        )
        player.sendMessage(msg)
    }

    fun sendRiichiOptions(player: Player, tilePairs: List<Pair<MahjongTile, List<MahjongTile>>>) {
        var msg = MESSAGE_PREFIX.append(Component.text("立直 - 選擇打出的牌：", MJColor.GOLD))
        tilePairs.forEach { (tile, machi) ->
            val machiStr = machi.joinToString(",") { it.displayName }
            val cmd = "/mahjong action RIICHI ${tile.code}"
            val button = Component.text("[${tile.displayName}]", MJColor.RED)
                .hoverEvent(hover("立直並打出 ${tile.displayName}，聽 $machiStr"))
                .clickEvent(ClickEvent.runCommand(cmd))
                .append(Component.text("→$machiStr ", MJColor.YELLOW))
            msg = msg.append(button)
        }
        msg = msg.append(
            Component.text("[跳過]", MJColor.DARK_GRAY)
                .hoverEvent(hover("不立直，正常出牌"))
                .clickEvent(ClickEvent.runCommand("/mahjong action SKIP"))
        )
        player.sendMessage(msg)
    }

    private fun actionColor(action: MahjongGameBehavior): TextColor = when (action) {
        MahjongGameBehavior.CHII -> MJColor.GREEN
        MahjongGameBehavior.PON, MahjongGameBehavior.PON_OR_CHII -> MJColor.BLUE
        MahjongGameBehavior.KAN, MahjongGameBehavior.MINKAN, MahjongGameBehavior.ANKAN, MahjongGameBehavior.KAKAN, MahjongGameBehavior.ANKAN_OR_KAKAN -> MJColor.AQUA
        MahjongGameBehavior.RON -> MJColor.RED
        MahjongGameBehavior.TSUMO -> MJColor.GOLD
        MahjongGameBehavior.RIICHI, MahjongGameBehavior.DOUBLE_RIICHI -> MJColor.LIGHT_PURPLE
        else -> MJColor.WHITE
    }
}
