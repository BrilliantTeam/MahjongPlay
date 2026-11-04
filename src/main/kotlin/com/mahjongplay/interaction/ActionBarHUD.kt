package com.mahjongplay.interaction

import com.mahjongplay.display.Props
import com.mahjongplay.game.MahjongGame
import com.mahjongplay.game.MahjongPlayer
import com.mahjongplay.game.MahjongPlayerBase
import com.mahjongplay.model.MahjongGameBehavior
import com.mahjongplay.model.MahjongRound
import com.mahjongplay.model.MahjongTile
import com.mahjongplay.util.actionBarMsg
import com.mahjongplay.util.machiText
import net.kyori.adventure.text.Component
import com.mahjongplay.util.MJColor
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

            val gap = Component.text("   ")
            val taiwan = game.rule.taiwan
            var bar = tile(MahjongTile.valueOf(round.wind.name)).append(Component.text("${round.round + 1}", MJColor.GOLD))
                .append(gap).append(Props.glyph(HONBA)).append(Component.text("${round.honba}", MJColor.YELLOW))
                .append(gap).append(seatWindOf(game, mjPlayer))
                .append(gap).append(Props.glyph(WALL)).append(Component.text("$wallSize", MJColor.GREEN))
                .append(gap).append(Props.glyph(POINTS)).append(Component.text("${mjPlayer.points}", MJColor.WHITE))
            if (taiwan) {
                mjPlayer.nukiDoraTiles.toList().takeIf { it.isNotEmpty() }?.let { flowers ->
                    bar = bar.append(gap)
                    flowers.forEach { bar = bar.append(tile(it)) }
                }
            } else {
                bar = bar.append(gap).append(Props.glyph(DORA))
                game.doraIndicators.forEach { bar = bar.append(tile(it.doraFromIndicator(game.rule.isSanma))) }
            }

            val machi = if (taiwan) {
                game.taiwanMachiPreview(mjPlayer).takeIf { it.isNotEmpty() }?.let { machiText(it) }
            } else if (mjPlayer.hands.size == mjPlayer.waitingHandSize) {
                game.machiWinnable(mjPlayer).takeIf { it.isNotEmpty() }?.let { machiText(it) }
            } else {
                mjPlayer.previewMachiTiles.takeIf { it.isNotEmpty() }?.let { machiText(it) }
            }
            if (machi != null) bar = bar.append(gap).append(Props.glyph(WAIT)).append(machi)

            player.sendActionBar(bar)
        }
    }

    private fun seatWindOf(game: MahjongGame, player: MahjongPlayerBase): Component {
        val pc = game.rule.playerCount
        val idx = List(pc) { game.seat[(game.round.round + it) % pc] }.indexOf(player)
        if (idx < 0) return Component.text("?")
        val wind = tile(MahjongTile.entries[MahjongTile.EAST.ordinal + idx])
        return if (idx == 0) wind.append(Props.glyph(DEALER)) else wind
    }

    fun tile(tile: MahjongTile, color: net.kyori.adventure.text.format.TextColor = net.kyori.adventure.text.format.NamedTextColor.WHITE): Component =
        Props.glyph(0xE900 + tile.ordinal, color)

    private const val WALL = 0xE942
    private const val POINTS = 0xE943
    private const val HONBA = 0xE944
    private const val DEALER = 0xE945
    private const val WAIT = 0xE946
    private const val DORA = 0xE947
}
