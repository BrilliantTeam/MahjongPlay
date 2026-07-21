package com.mahjongplay.interaction

import com.mahjongplay.game.MahjongGame
import com.mahjongplay.game.MahjongPlayer
import com.mahjongplay.game.MahjongPlayerBase
import com.mahjongplay.model.MahjongGameBehavior
import com.mahjongplay.model.MahjongRound
import com.mahjongplay.model.MahjongTile
import com.mahjongplay.util.actionBarMsg
import net.kyori.adventure.text.Component
import com.mahjongplay.util.MJColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

object ActionBarHUD {

    fun sendUpdate(game: MahjongGame) {
        val round = game.round
        val wallSize = game.wallSize

        game.realPlayers.forEach { mjPlayer ->
            val player = Bukkit.getPlayer(UUID.fromString(mjPlayer.uuid)) ?: return@forEach

            val riichiOverride = (mjPlayer as? MahjongPlayer)?.riichiActionBarOverride
            if (riichiOverride != null) {
                player.actionBarMsg(riichiOverride)
                return@forEach
            }

            val seatWind = seatWindOf(game, mjPlayer)
            val doraStr = game.doraIndicators.joinToString(",") { it.doraFromIndicator(game.rule.isSanma).displayName }

            var bar = Component.text("${round.displayName()}", MJColor.GOLD)
                .append(Component.text("｜本場${round.honba}", MJColor.YELLOW))
                .append(Component.text("｜$seatWind", MJColor.AQUA))
                .append(Component.text("｜牌山$wallSize", MJColor.GREEN))
                .append(Component.text("｜${mjPlayer.points}點", MJColor.WHITE))
                .append(Component.text("｜寶牌：$doraStr", MJColor.RED))

            val previewMachi = mjPlayer.previewMachiTiles
            if (previewMachi.isNotEmpty()) {
                val machiStr = previewMachi
                    .distinctBy { it.mahjong4jTile }
                    .filterNot { it.isRed }
                    .joinToString(",") { it.displayName }
                bar = bar.append(Component.text("｜聽：$machiStr", MJColor.LIGHT_PURPLE))
            }

            player.sendActionBar(bar)
        }
    }

    private fun seatWindOf(game: MahjongGame, player: MahjongPlayerBase): String {
        val pc = game.rule.playerCount
        val seatOrder = List(pc) { game.seat[(game.round.round + it) % pc] }
        val windNames = if (pc == 3) listOf("東（莊）", "南", "西") else listOf("東（莊）", "南", "西", "北")
        val idx = seatOrder.indexOf(player)
        return if (idx in windNames.indices) windNames[idx] else "?"
    }
}
