package com.mahjongplay.interaction

import com.mahjongplay.display.BoardRenderer
import com.mahjongplay.game.*
import com.mahjongplay.model.*
import com.mahjongplay.ui.TurnTimerBar
import com.mahjongplay.util.CancelTask
import com.mahjongplay.util.MESSAGE_PREFIX
import com.mahjongplay.util.ScheduleUtil
import com.mahjongplay.util.YakuNameChinese
import net.kyori.adventure.text.Component
import com.mahjongplay.util.MJColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.time.Duration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.util.UUID

class PaperGameBridge(
    val game: MahjongGame,
    val renderer: BoardRenderer,
    val tableManager: com.mahjongplay.table.MahjongTableManager,
) : GameEventListener, PendingActionListener {

    private var hudTask: CancelTask? = null
    private val turnTimerBar = TurnTimerBar(game)

    override fun onGameStart(game: MahjongGame) {
        renderer.onGameStart(game)
        game.realPlayers.forEach {
            it.gameId = game.tableId
            it.pendingActionListener = this
        }
        tableManager.getSession(game.tableId)?.let { tableManager.updateTableDisplay(it) }
        broadcast(MESSAGE_PREFIX.append(Component.text("開局！", MJColor.GOLD)))
        sound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.0f)
        startHudUpdates()
        turnTimerBar.cleanup()
        turnTimerBar.show()
    }

    override fun onRoundStart(game: MahjongGame, round: MahjongRound) {
        renderer.onRoundStart(game, round)
        sound(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.0f)
        val title = Title.title(
            Component.text(round.displayName(), MJColor.GOLD),
            Component.text("本場${round.honba}", MJColor.YELLOW),
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))
        )
        forEachPlayer { it.showTitle(title) }
    }

    override fun onTileDrawn(player: MahjongPlayerBase, tile: MahjongTile) {
        renderer.onTileDrawn(player, tile)
        sound(Sound.BLOCK_BAMBOO_PLACE, 0.3f, 1.6f)
    }

    override fun onTileDiscarded(player: MahjongPlayerBase, tile: MahjongTile) {
        renderer.onTileDiscarded(player, tile)
        sound(Sound.BLOCK_BAMBOO_WOOD_PLACE, 0.6f, 1.0f)
        updateHud()
    }

    override fun onHandsUpdated(player: MahjongPlayerBase) {
        renderer.onHandsUpdated(player)
        updateHud()
    }

    private val quickTitleTimes = Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800), Duration.ofMillis(200))

    private fun showEventTitle(main: Component, subtitle: Component) {
        val title = Title.title(main, subtitle, quickTitleTimes)
        forEachPlayer { it.showTitle(title) }
    }

    private fun sound(sound: Sound, volume: Float = 0.8f, pitch: Float = 1.0f) {
        val center = renderer.tableCenter
        ScheduleUtil.region(center, Runnable { center.world.playSound(center, sound, volume, pitch) })
    }

    override fun onChii(player: MahjongPlayerBase, claimedTile: MahjongTile, from: MahjongPlayerBase) {
        renderer.onChii(player, claimedTile, from)
        sound(Sound.BLOCK_NOTE_BLOCK_HARP, 0.8f, 1.3f)
        showEventTitle(
            Component.text("吃！", MJColor.GREEN),
            Component.text(player.displayName, MJColor.AQUA)
        )
    }

    override fun onPon(player: MahjongPlayerBase, claimedTile: MahjongTile, from: MahjongPlayerBase) {
        renderer.onPon(player, claimedTile, from)
        sound(Sound.BLOCK_NOTE_BLOCK_HARP, 0.9f, 0.9f)
        showEventTitle(
            Component.text("碰！", MJColor.BLUE),
            Component.text(player.displayName, MJColor.AQUA)
        )
    }

    override fun onKan(player: MahjongPlayerBase, tile: MahjongTile, kanType: String, from: MahjongPlayerBase?) {
        renderer.onKan(player, tile, kanType, from)
        sound(Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f)
        showEventTitle(
            Component.text("槓！", MJColor.DARK_AQUA),
            Component.text(player.displayName, MJColor.AQUA)
        )
    }

    override fun onRiichi(player: MahjongPlayerBase, tile: MahjongTile) {
        renderer.onRiichi(player, tile)
        sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f)
        showEventTitle(
            Component.text("立直！", MJColor.LIGHT_PURPLE),
            Component.text(player.displayName, MJColor.AQUA)
        )
    }

    override fun onTsumo(player: MahjongPlayerBase, tile: MahjongTile, settlement: YakuSettlement) {
        ScheduleUtil.region(renderer.tableCenter, Runnable {
            renderer.revealHands(player)
        })
        sound(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f)
        showEventTitle(
            Component.text("自摸！", MJColor.GOLD),
            Component.text(player.displayName, MJColor.AQUA)
        )
        sendYakuSummary(settlement)
    }

    override fun onRon(winners: List<MahjongPlayerBase>, loser: MahjongPlayerBase, tile: MahjongTile, settlements: List<YakuSettlement>) {
        ScheduleUtil.region(renderer.tableCenter, Runnable {
            winners.forEach { renderer.revealHands(it) }
        })
        sound(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f)
        val names = winners.joinToString(", ") { it.displayName }
        showEventTitle(
            Component.text("榮和！", MJColor.RED),
            Component.text(names, MJColor.AQUA)
        )
        settlements.forEach { sendYakuSummary(it) }
    }

    override fun onDraw(draw: ExhaustiveDraw, settlement: ScoreSettlement) {
        if (draw == ExhaustiveDraw.NORMAL) {
            ScheduleUtil.region(renderer.tableCenter, Runnable {
                game.players.filter { it.isTenpai }.forEach { renderer.revealHands(it) }
            })
        }
        sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f)
        showEventTitle(
            draw.toText().color(MJColor.YELLOW),
            Component.text("流局", MJColor.GRAY)
        )
    }

    override fun onScoreSettlement(settlement: ScoreSettlement) {
        settlement.rankedScoreList.forEachIndexed { index, ranked ->
            val line = Component.text("  ${index + 1}. ", MJColor.YELLOW)
                .append(Component.text(ranked.scoreItem.displayName, MJColor.AQUA))
                .append(Component.text("  ${ranked.scoreTotal}點", MJColor.WHITE))
                .append(Component.text("（${ranked.scoreChangeText}）", MJColor.GRAY))
            broadcast(line)
        }
    }

    override fun onGameEnd(game: MahjongGame, scoreList: List<ScoreItem>) {
        val task = Runnable {
            renderer.onGameEnd(game, scoreList)
            stopHudUpdates()
            turnTimerBar.cleanup()
            tableManager.getSession(game.tableId)?.let {
                tableManager.updateTableDisplay(it)
                it.table.showActionButtons()
                tableManager.registerJoinInteraction(it)
            }
            broadcast(MESSAGE_PREFIX.append(Component.text("牌局結束！", MJColor.GOLD)))
            sound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 0.8f)
            val sorted = scoreList.sortedByDescending { it.scoreOrigin }
            sorted.forEachIndexed { index, item ->
                broadcast(Component.text("  ${index + 1}. ${item.displayName}  ${item.scoreOrigin}點", MJColor.YELLOW))
            }
        }
        ScheduleUtil.region(renderer.tableCenter, task)
    }

    private fun sendYakuSummary(settlement: YakuSettlement) {
        val yakuNames = buildList {
            addAll(settlement.yakuList.map { YakuNameChinese.getName(it) })
            addAll(settlement.yakumanList.map { YakuNameChinese.getName(it) })
            addAll(settlement.doubleYakumanList.map { PlainTextComponentSerializer.plainText().serialize(it.toText()) })
            if (settlement.nagashiMangan) add("流局滿貫")
            if (settlement.nukiDoraCount > 0) add("拔北×${settlement.nukiDoraCount}")
        }
        if (yakuNames.isEmpty()) return

        val scoreLine = buildString {
            val isYakuman = settlement.yakumanList.isNotEmpty() || settlement.doubleYakumanList.isNotEmpty()
            if (!isYakuman && !settlement.nagashiMangan) {
                append(" ${settlement.han}翻${settlement.fu}符")
            }
            append(" ${settlement.score}點")
            val alias = when {
                settlement.nagashiMangan -> "滿貫"
                isYakuman -> {
                    val rate = (settlement.yakumanList.size + settlement.doubleYakumanList.size * 2).coerceAtMost(6)
                    when (rate) {
                        1 -> "役滿"; 2 -> "二倍役滿"; 3 -> "三倍役滿"
                        4 -> "四倍役滿"; 5 -> "五倍役滿"; else -> "六倍役滿"
                    }
                }
                settlement.han >= 13 -> "累計役滿"
                settlement.han >= 11 -> "三倍滿"
                settlement.han >= 8 -> "倍滿"
                settlement.han >= 6 -> "跳滿"
                settlement.han >= 5 || (settlement.fu >= 40 && settlement.han == 4) || (settlement.fu >= 70 && settlement.han == 3) -> "滿貫"
                else -> null
            }
            if (alias != null) append("  !!$alias!!")
        }

        broadcast(Component.text("  役：${yakuNames.joinToString(", ")}", MJColor.GREEN)
            .append(Component.text(scoreLine, MJColor.YELLOW)))
    }

    private fun startHudUpdates() {
        hudTask = ScheduleUtil.globalTimer(0L, 20L) { updateHud() }
    }

    private fun stopHudUpdates() {
        hudTask?.invoke()
        hudTask = null
    }

    private fun updateHud() {
        ActionBarHUD.sendUpdate(game)
    }

    private fun broadcast(msg: Component) {
        forEachPlayer { it.sendMessage(msg) }
    }

    private fun forEachPlayer(action: (Player) -> Unit) {
        game.players.forEach { mjp ->
            val p = Bukkit.getPlayer(UUID.fromString(mjp.uuid))
            if (p != null) action(p)
        }
    }

    fun cleanup() {
        stopHudUpdates()
        turnTimerBar.cleanup()
    }

    fun hideBarForPlayer(playerUUID: String) {
        turnTimerBar.hideForPlayer(playerUUID)
    }

    override fun onPendingActionStart(player: MahjongPlayer, behaviors: List<MahjongGameBehavior>, timeoutSeconds: Int) {
        ScheduleUtil.region(renderer.tableCenter, Runnable {
            turnTimerBar.startAction(player, behaviors, timeoutSeconds)
            renderer.spawnActionOptions(player.uuid, player.actionOptions)
            Bukkit.getPlayer(UUID.fromString(player.uuid))?.let {
                it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f)
            }
        })
    }

    override fun onPendingActionEnd(player: MahjongPlayer) {
        ScheduleUtil.region(renderer.tableCenter, Runnable {
            turnTimerBar.endAction()
            if (player.riichiSelectionMode) {
                player.riichiSelectionMode = false
                renderer.exitRiichiMode(player.uuid)
            }
            renderer.clearActionOptions(player.uuid)
        })
    }
}
