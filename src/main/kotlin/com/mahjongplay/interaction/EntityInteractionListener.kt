package com.mahjongplay.interaction

import com.mahjongplay.game.GameStatus
import com.mahjongplay.game.MahjongGame
import com.mahjongplay.model.MahjongGameBehavior
import com.mahjongplay.game.MahjongPlayer
import com.mahjongplay.table.MahjongPanel
import com.mahjongplay.table.MahjongTableManager
import com.mahjongplay.util.msg
import com.mahjongplay.util.MJColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Interaction
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class EntityInteractionListener(
    private val gameManager: MahjongTableManager
) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onWandClick(event: PlayerInteractEvent) {
        if (event.action != Action.LEFT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return
        val item = event.item ?: return
        if (item.type != Material.PAPER) return
        val meta = item.itemMeta ?: return
        if (!meta.hasCustomModelData() || meta.customModelData != MahjongPanel.WAND_MODEL_DATA) return
        val block = event.clickedBlock ?: return

        event.isCancelled = true
        val player = event.player
        if (!player.hasPermission("mahjongplay.command.create")) {
            player.msg("你沒有權限建立牌桌", MJColor.RED)
            return
        }
        val center = Location(block.world, block.x + 0.5, (block.y + 1).toDouble(), block.z + 0.5)
        MahjongPanel.sendModePrompt(gameManager, player, center)
    }

    @EventHandler(ignoreCancelled = true)
    fun onManageClick(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return
        val player = event.player
        if (!player.isSneaking) return
        val item = event.item ?: return
        if (item.type != Material.PAPER) return
        val meta = item.itemMeta ?: return
        if (!meta.hasCustomModelData() || meta.customModelData != MahjongPanel.WAND_MODEL_DATA) return
        val block = event.clickedBlock ?: return
        val session = gameManager.getSessionByBlock(block.location) ?: return
        if (!gameManager.canManage(session, player)) return
        event.isCancelled = true
        MahjongPanel.manage(gameManager, player, session)
    }

    @EventHandler
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        val clickedEntity = event.rightClicked
        if (clickedEntity !is Interaction) return

        val player = event.player
        val playerUUID = player.uniqueId.toString()

        val joinSession = gameManager.getTableByJoinInteraction(clickedEntity.uniqueId)
        if (joinSession != null) {
            event.isCancelled = true
            if (joinSession.game.status != GameStatus.WAITING) {
                player.msg("牌局正在進行中", MJColor.RED)
                return
            }

            val existingSession = gameManager.getSessionForPlayer(playerUUID)
            if (existingSession != null && existingSession.tableId == joinSession.tableId) {
                gameManager.leaveTable(playerUUID)
                player.msg("已退出牌桌", MJColor.YELLOW)
                return
            }

            if (existingSession != null) {
                player.msg("你已經在另一個牌桌中了", MJColor.RED)
                return
            }
            if (gameManager.joinTable(joinSession.tableId, playerUUID, player.name)) {
                player.msg("已加入牌桌！", MJColor.GREEN)
            } else {
                player.msg("無法加入（桌子已滿）", MJColor.RED)
            }
            return
        }

        val readySession = gameManager.getTableByReadyInteraction(clickedEntity.uniqueId)
        if (readySession != null) {
            event.isCancelled = true
            if (readySession.game.status != GameStatus.WAITING) return

            val mjPlayer = readySession.game.players.find { it.uuid == playerUUID }
            if (mjPlayer == null) {
                player.msg("你不在這個牌桌中", MJColor.RED)
                return
            }
            val newReady = !mjPlayer.ready
            readySession.game.readyOrNot(playerUUID, newReady)
            gameManager.updateTableDisplay(readySession)
            gameManager.checkAutoStart(readySession)
            player.msg(if (newReady) "已準備 ✓" else "取消準備 ✗", if (newReady) MJColor.GREEN else MJColor.YELLOW)
            return
        }

        val startSession = gameManager.getTableByStartInteraction(clickedEntity.uniqueId)
        if (startSession != null) {
            event.isCancelled = true
            if (startSession.game.status != GameStatus.WAITING) return

            if (!gameManager.canManage(startSession, player)) {
                player.msg("只有桌主才能開局", MJColor.RED)
                return
            }

            val error = gameManager.startGame(startSession)
            if (error != null) {
                player.msg(error, MJColor.RED)
            }
            return
        }

        val game = gameManager.getGameForPlayer(playerUUID) ?: return
        val mjPlayer = game.realPlayers.find { it.uuid == playerUUID } as? MahjongPlayer ?: return
        val renderer = gameManager.getRenderer(game) ?: return

        val actionDisplay = renderer.getActionByInteraction(clickedEntity.uniqueId)
        if (actionDisplay != null && actionDisplay.ownerUUID == playerUUID) {
            event.isCancelled = true

            if (actionDisplay.behavior == MahjongGameBehavior.RIICHI && actionDisplay.data == "enter_mode") {
                mjPlayer.riichiSelectionMode = true
                renderer.enterRiichiMode(playerUUID, mjPlayer.riichiTilePairs)
                return
            }

            if (actionDisplay.behavior == MahjongGameBehavior.SKIP && actionDisplay.data == "cancel_riichi") {
                mjPlayer.riichiSelectionMode = false
                renderer.exitRiichiMode(playerUUID)
                renderer.spawnActionOptions(playerUUID, mjPlayer.riichiOriginalOptions)
                return
            }

            if (actionDisplay.subOptions != null && actionDisplay.subOptions.isNotEmpty()) {
                renderer.expandSubMenu(playerUUID, actionDisplay.subOptions)
            } else {
                mjPlayer.resolveAction(actionDisplay.behavior, actionDisplay.data)
            }
            return
        }

        val pending = mjPlayer.pendingAction ?: return

        if (mjPlayer.riichiSelectionMode) {
            val ownerDisplays = renderer.handOwnerDisplays[mjPlayer.uuid] ?: return
            val clickedIndex = ownerDisplays.indexOfFirst {
                it.interactionEntity?.uniqueId == clickedEntity.uniqueId
            }
            if (clickedIndex < 0) return

            val tile = mjPlayer.hands.getOrNull(clickedIndex) ?: return
            event.isCancelled = true

            val eligibleTiles = mjPlayer.riichiTilePairs.map { it.first }
            if (tile !in eligibleTiles) return

            val confirmed = renderer.selectTileForRiichi(playerUUID, clickedIndex, tile, mjPlayer.riichiTilePairs)
            if (confirmed) {
                mjPlayer.riichiSelectionMode = false
                renderer.exitRiichiMode(playerUUID)
                mjPlayer.resolveAction(MahjongGameBehavior.RIICHI, "${tile.code}")
            }
            return
        }

        if (MahjongGameBehavior.DISCARD !in pending.behaviors) return

        val ownerDisplays = renderer.handOwnerDisplays[mjPlayer.uuid] ?: return

        val clickedIndex = ownerDisplays.indexOfFirst {
            it.interactionEntity?.uniqueId == clickedEntity.uniqueId
        }
        if (clickedIndex < 0) return

        val tile = mjPlayer.hands.getOrNull(clickedIndex) ?: return
        event.isCancelled = true

        val confirmed = renderer.selectTileForDiscard(mjPlayer.uuid, clickedIndex)
        if (confirmed) {
            mjPlayer.resolveAction(MahjongGameBehavior.DISCARD, "${tile.code}")
        }
    }
}

interface GameRegistry {
    fun getGameForPlayer(uuid: String): MahjongGame?
    fun getRenderer(game: MahjongGame): com.mahjongplay.display.BoardRenderer?
}
