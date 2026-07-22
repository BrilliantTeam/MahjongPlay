package com.mahjongplay.display

import com.mahjongplay.MahjongPlayPlugin
import com.mahjongplay.display.TileConstants.DEPTH
import com.mahjongplay.display.TileConstants.HEIGHT
import com.mahjongplay.display.TileConstants.PADDING
import com.mahjongplay.display.TileConstants.WIDTH
import com.mahjongplay.game.*
import com.mahjongplay.model.*
import com.mahjongplay.util.ScheduleUtil
import net.kyori.adventure.text.Component
import com.mahjongplay.util.MJColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ActionDisplay(
    val textDisplay: TextDisplay,
    val interaction: org.bukkit.entity.Interaction,
    val behavior: MahjongGameBehavior,
    val data: String,
    val ownerUUID: String,
    val subOptions: List<ActionDisplayOption>? = null
)

class BoardRenderer(
    val game: MahjongGame,
    val tableCenter: Location,
) : GameEventListener {

    val handDisplays = ConcurrentHashMap<String, MutableList<MahjongTileDisplay>>()
    val handOwnerDisplays = ConcurrentHashMap<String, MutableList<MahjongTileDisplay>>()
    private val revealedDisplays = ConcurrentHashMap<String, MutableList<MahjongTileDisplay>>()
    private val discardDisplays = ConcurrentHashMap<String, MutableList<MahjongTileDisplay>>()
    private val fuuroDisplays = ConcurrentHashMap<String, MutableList<MahjongTileDisplay>>()
    private val nukiDoraDisplays = ConcurrentHashMap<String, MutableList<MahjongTileDisplay>>()
    private val botNameDisplays = mutableListOf<TextDisplay>()
    private val riichiIndicatorDisplays = ConcurrentHashMap<String, TextDisplay>()
    private var floatingCenterDisplay: MahjongTileDisplay? = null
    private val selectedTileIndices = ConcurrentHashMap<String, Int>()
    private val actionDisplays = ConcurrentHashMap<String, MutableList<ActionDisplay>>()
    private val highlightedDiscards = ConcurrentHashMap<String, MutableList<MahjongTileDisplay>>()

    companion object {
        private const val RAISE_OFFSET = 0.05
        private const val ACTION_BUTTON_SPACING = 0.55
    }

    private val world: World get() = tableCenter.world

    private val surfaceY: Double get() = tableCenter.blockY + 1.0 + 1.0 / 16.0
    private val standingTileY: Double get() = surfaceY + HEIGHT / 2.0
    private val flatTileY: Double get() = surfaceY + DEPTH / 2.0

    private fun physicalSeatIndex(seatIndex: Int): Int {
        if (game.rule.playerCount == 3) {
            return when (seatIndex) {
                0 -> 0
                1 -> 2
                2 -> 3
                else -> seatIndex
            }
        }
        return seatIndex
    }

    private fun seatYaw(seatIndex: Int): Float = when (physicalSeatIndex(seatIndex)) {
        0 -> 90f
        3 -> 0f
        2 -> -90f
        else -> 180f
    }

    private fun seatDirection(seatIndex: Int): DoubleArray = when (physicalSeatIndex(seatIndex)) {
        0 -> doubleArrayOf(1.0, 0.0)
        3 -> doubleArrayOf(0.0, 1.0)
        2 -> doubleArrayOf(-1.0, 0.0)
        else -> doubleArrayOf(0.0, -1.0)
    }

    private fun seatPerpendicular(seatIndex: Int): DoubleArray = when (physicalSeatIndex(seatIndex)) {
        0 -> doubleArrayOf(0.0, 1.0)
        3 -> doubleArrayOf(-1.0, 0.0)
        2 -> doubleArrayOf(0.0, -1.0)
        else -> doubleArrayOf(1.0, 0.0)
    }

    override fun onRoundStart(game: MahjongGame, round: MahjongRound) {
        ScheduleUtil.region(tableCenter, Runnable {
            clearAllDisplays()
            spawnBotNameTags()
        })
    }

    override fun onRiichi(player: MahjongPlayerBase, tile: MahjongTile) {
        ScheduleUtil.region(tableCenter, Runnable {
            renderRiichiIndicator(player)
        })
    }

    override fun onHandsUpdated(player: MahjongPlayerBase) {
        ScheduleUtil.region(tableCenter, Runnable {
            renderHands(player)
            if (player.nukiDoraTiles.isNotEmpty()) renderNukiDora(player)
        })
    }

    override fun onTileDiscarded(player: MahjongPlayerBase, tile: MahjongTile) {
        ScheduleUtil.region(tableCenter, Runnable {
            renderDiscards(player)
            spawnFloatingCenterTile(tile)
        })
    }

    override fun onPon(player: MahjongPlayerBase, claimedTile: MahjongTile, from: MahjongPlayerBase) {
        ScheduleUtil.region(tableCenter, Runnable {
            renderFuuro(player)
            renderHands(player)
            renderDiscards(from)
        })
    }

    override fun onChii(player: MahjongPlayerBase, claimedTile: MahjongTile, from: MahjongPlayerBase) {
        ScheduleUtil.region(tableCenter, Runnable {
            renderFuuro(player)
            renderHands(player)
            renderDiscards(from)
        })
    }

    override fun onKan(player: MahjongPlayerBase, tile: MahjongTile, kanType: String, from: MahjongPlayerBase?) {
        ScheduleUtil.region(tableCenter, Runnable {
            renderFuuro(player)
            renderHands(player)
            if (from != null) renderDiscards(from)
        })
    }

    override fun onGameEnd(game: MahjongGame, scoreList: List<ScoreItem>) {
        ScheduleUtil.region(tableCenter, Runnable {
            clearAllDisplays()
        })
    }

    fun renderHands(player: MahjongPlayerBase) {
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        selectedTileIndices.remove(player.uuid)
        unhighlightDiscards(player.uuid)

        val backList = handDisplays.getOrPut(player.uuid) { mutableListOf() }
        val ownerList = handOwnerDisplays.getOrPut(player.uuid) { mutableListOf() }

        val tileCount = player.hands.size
        val dir = seatDirection(seatIndex)
        val perp = seatPerpendicular(seatIndex)
        val dirOffset = 0.85 + DEPTH + HEIGHT
        val totalWidth = tileCount * WIDTH + (tileCount - 1) * PADDING
        val startOffset = totalWidth / 2.0
        val yaw = seatYaw(seatIndex)
        val isRealPlayer = player.isRealPlayer

        val waitingHandSize = 13 - player.fuuroList.size * 3
        val drawnHandSize = waitingHandSize + 1
        val showGap = player.justDrewTile && tileCount == drawnHandSize

        player.hands.forEachIndexed { index, tile ->
            val isLast = index == tileCount - 1 && showGap
            val tileOffset = index * (WIDTH + PADDING) + if (isLast) PADDING * 15.0 else 0.0

            val x = tableCenter.x + dir[0] * dirOffset + perp[0] * (startOffset - tileOffset)
            val z = tableCenter.z + dir[1] * dirOffset + perp[1] * (startOffset - tileOffset)
            val loc = Location(world, x, standingTileY, z)

            if (index < backList.size) {
                backList[index].updatePosition(loc, yaw, TileFace.STANDING)
                ownerList[index].updateTile(tile)
                ownerList[index].updatePosition(loc.clone(), yaw, TileFace.STANDING)
            } else {
                val backDisplay = MahjongTileDisplay(loc, MahjongTile.UNKNOWN, TileFace.STANDING, yaw, hiddenByDefault = true)
                backDisplay.spawn()
                backList += backDisplay

                val ownerDisplay = MahjongTileDisplay(
                    loc.clone(), tile, TileFace.STANDING, yaw,
                    interactive = isRealPlayer, hiddenByDefault = true
                )
                ownerDisplay.spawn()
                ownerList += ownerDisplay
            }
        }

        while (backList.size > tileCount) {
            backList.removeLast().remove()
            ownerList.removeLast().remove()
        }

        ScheduleUtil.regionLater(tableCenter, 1L, Runnable {
            updateVisibility(player)
        })
    }

    fun selectTileForDiscard(playerUUID: String, clickedIndex: Int): Boolean {
        val currentSelected = selectedTileIndices[playerUUID]
        clearTileSelection(playerUUID)

        if (currentSelected == clickedIndex) {
            unhighlightDiscards(playerUUID)
            clearTenpaiPreview(playerUUID)
            return true
        }

        raiseTileAt(playerUUID, clickedIndex)
        selectedTileIndices[playerUUID] = clickedIndex

        val player = game.seat.find { it.uuid == playerUUID }
        val tile = player?.hands?.getOrNull(clickedIndex)
        if (tile != null) {
            highlightMatchingDiscards(playerUUID, tile)
            showTenpaiPreview(playerUUID, player, tile)
        }

        return false
    }

    private fun showTenpaiPreview(playerUUID: String, player: MahjongPlayerBase, discard: MahjongTile) {
        val machi = game.machiWinnable(player, discard)

        if (machi.isNotEmpty()) {
            val mjPlayer = player as? com.mahjongplay.game.MahjongPlayer
            mjPlayer?.riichiActionBarOverride = Component.text("出牌：${discard.displayName}", MJColor.AQUA)
                .append(Component.text(" ｜ 聽：", MJColor.YELLOW))
                .append(com.mahjongplay.util.machiText(machi))
        } else {
            clearTenpaiPreview(playerUUID)
        }
    }

    private fun clearTenpaiPreview(playerUUID: String) {
        val mjPlayer = game.seat.find { it.uuid == playerUUID } as? com.mahjongplay.game.MahjongPlayer
        if (mjPlayer?.riichiSelectionMode != true) {
            mjPlayer?.riichiActionBarOverride = null
        }
    }

    @Suppress("DEPRECATION")
    private fun glow(entity: org.bukkit.entity.Entity?, viewer: Player?, riichi: Boolean = false) {
        if (entity == null || viewer == null) return
        val color = if (riichi) org.bukkit.ChatColor.RED else org.bukkit.ChatColor.YELLOW
        try {
            MahjongPlayPlugin.instance.glowingEntities.setGlowing(entity, viewer, color)
        } catch (_: ReflectiveOperationException) {}
    }

    private fun unglow(entity: org.bukkit.entity.Entity?, viewer: Player?) {
        if (entity == null || viewer == null) return
        try {
            MahjongPlayPlugin.instance.glowingEntities.unsetGlowing(entity, viewer)
        } catch (_: ReflectiveOperationException) {}
    }

    private fun highlightMatchingDiscards(playerUUID: String, tile: MahjongTile) {
        unhighlightDiscards(playerUUID)
        val bukkitPlayer = Bukkit.getPlayer(UUID.fromString(playerUUID)) ?: return
        val highlighted = highlightedDiscards.getOrPut(playerUUID) { mutableListOf() }
        discardDisplays.values.flatten().forEach { display ->
            if (display.tile == tile) {
                glow(display.entity, bukkitPlayer)
                highlighted += display
            }
        }
    }

    private fun unhighlightDiscards(playerUUID: String) {
        val bukkitPlayer = Bukkit.getPlayer(UUID.fromString(playerUUID))
        highlightedDiscards[playerUUID]?.forEach { display -> unglow(display.entity, bukkitPlayer) }
        highlightedDiscards.remove(playerUUID)
    }

    fun enterRiichiMode(playerUUID: String, tilePairs: List<Pair<MahjongTile, List<MahjongTile>>>) {
        val player = game.seat.find { it.uuid == playerUUID } ?: return
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        clearTileSelection(playerUUID)
        unhighlightDiscards(playerUUID)

        val cancelOption = ActionDisplayOption(MahjongGameBehavior.SKIP, "取消", "cancel_riichi", MJColor.RED)
        spawnActionButtons(playerUUID, seatIndex, listOf(cancelOption))

        val eligibleTiles = tilePairs.map { it.first }
        val ownerDisplays = handOwnerDisplays[playerUUID] ?: return
        val hands = player.hands
        val bukkitPlayer = Bukkit.getPlayer(UUID.fromString(playerUUID))

        ownerDisplays.forEachIndexed { index, display ->
            val tile = hands.getOrNull(index) ?: return@forEachIndexed
            if (tile in eligibleTiles) {
                glow(display.entity, bukkitPlayer, riichi = true)
            }
        }
    }

    fun exitRiichiMode(playerUUID: String) {
        clearTileSelection(playerUUID)
        unhighlightDiscards(playerUUID)

        val ownerDisplays = handOwnerDisplays[playerUUID] ?: return
        val bukkitPlayer = Bukkit.getPlayer(UUID.fromString(playerUUID))
        ownerDisplays.forEach { display -> unglow(display.entity, bukkitPlayer) }

        val mjPlayer = game.seat.find { it.uuid == playerUUID } as? com.mahjongplay.game.MahjongPlayer
        mjPlayer?.riichiActionBarOverride = null
    }

    fun selectTileForRiichi(playerUUID: String, clickedIndex: Int, tile: MahjongTile, tilePairs: List<Pair<MahjongTile, List<MahjongTile>>>): Boolean {
        val currentSelected = selectedTileIndices[playerUUID]
        clearTileSelection(playerUUID)

        if (currentSelected == clickedIndex) {
            unhighlightDiscards(playerUUID)
            return true
        }

        raiseTileAt(playerUUID, clickedIndex)
        selectedTileIndices[playerUUID] = clickedIndex

        val machi = tilePairs.find { it.first == tile }?.second ?: emptyList()
        unhighlightDiscards(playerUUID)
        val bukkitPlayer = Bukkit.getPlayer(UUID.fromString(playerUUID))
        val highlighted = highlightedDiscards.getOrPut(playerUUID) { mutableListOf() }
        discardDisplays.values.flatten().forEach { display ->
            if (display.tile.mahjong4jTile in machi.map { it.mahjong4jTile }) {
                glow(display.entity, bukkitPlayer)
                highlighted += display
            }
        }

        val mjPlayer = game.seat.find { it.uuid == playerUUID } as? com.mahjongplay.game.MahjongPlayer
        if (mjPlayer != null) {
            val machiStr = machi.joinToString(",") { it.displayName }
            mjPlayer.riichiActionBarOverride = Component.text("立直出牌：${tile.displayName}", MJColor.LIGHT_PURPLE)
                .append(Component.text(" ｜ 聽：$machiStr", MJColor.YELLOW))
        }

        return false
    }

    private fun clearTileSelection(playerUUID: String) {
        selectedTileIndices.remove(playerUUID)?.let { lowerTileAt(playerUUID, it) }
    }

    private fun raiseTileAt(playerUUID: String, index: Int) {
        teleportTileY(handOwnerDisplays[playerUUID]?.getOrNull(index), RAISE_OFFSET)
    }

    private fun lowerTileAt(playerUUID: String, index: Int) {
        teleportTileY(handOwnerDisplays[playerUUID]?.getOrNull(index), -RAISE_OFFSET)
    }

    private fun teleportTileY(display: MahjongTileDisplay?, deltaY: Double) {
        display ?: return
        display.entity?.let { e ->
            val loc = e.location.clone()
            loc.y += deltaY
            e.teleportAsync(loc)
        }
        display.interactionEntity?.let { e ->
            val loc = e.location.clone()
            loc.y += deltaY
            e.teleportAsync(loc)
        }
    }

    fun spawnActionOptions(playerUUID: String, options: List<ActionDisplayOption>) {
        clearActionOptions(playerUUID)
        if (options.isEmpty()) return

        val player = game.seat.find { it.uuid == playerUUID } ?: return
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        spawnActionButtons(playerUUID, seatIndex, options)
    }

    private fun spawnActionButtons(playerUUID: String, seatIndex: Int, options: List<ActionDisplayOption>) {
        clearActionOptions(playerUUID)

        val dir = seatDirection(seatIndex)
        val perp = seatPerpendicular(seatIndex)
        val dirOffset = 0.85 + DEPTH + HEIGHT + 0.1
        val actionY = surfaceY + HEIGHT + 0.65

        val totalWidth = options.size * ACTION_BUTTON_SPACING
        val startOffset = (totalWidth - ACTION_BUTTON_SPACING) / 2.0

        val displays = mutableListOf<ActionDisplay>()
        val ownerBukkit = Bukkit.getPlayer(UUID.fromString(playerUUID))

        options.forEachIndexed { index, option ->
            val offset = index * ACTION_BUTTON_SPACING
            val x = tableCenter.x + dir[0] * dirOffset + perp[0] * (startOffset - offset)
            val z = tableCenter.z + dir[1] * dirOffset + perp[1] * (startOffset - offset)
            val loc = Location(world, x, actionY, z)

            val textDisplay = world.spawnEntity(loc, EntityType.TEXT_DISPLAY) as TextDisplay
            textDisplay.isPersistent = false
            textDisplay.billboard = Display.Billboard.CENTER
            textDisplay.backgroundColor = Color.fromARGB(60, 0, 0, 0)
            textDisplay.brightness = Display.Brightness(15, 15)
            textDisplay.text(
                Component.text(option.label, option.color).decorate(TextDecoration.BOLD)
            )
            textDisplay.alignment = TextDisplay.TextAlignment.CENTER
            textDisplay.setViewRange(0.3f)
            textDisplay.setVisibleByDefault(false)

            val scaleMatrix = org.joml.Matrix4f().scale(0.6f)
            textDisplay.setTransformationMatrix(scaleMatrix)

            ownerBukkit?.showEntity(MahjongPlayPlugin.instance, textDisplay)

            val interLoc = Location(world, x, actionY - 0.1, z)
            val interaction = world.spawnEntity(interLoc, EntityType.INTERACTION) as org.bukkit.entity.Interaction
            interaction.isPersistent = false
            interaction.interactionWidth = 0.4f
            interaction.interactionHeight = 0.4f
            interaction.isResponsive = false

            displays += ActionDisplay(textDisplay, interaction, option.behavior, option.data, playerUUID, option.subOptions)
        }

        actionDisplays[playerUUID] = displays
    }

    fun expandSubMenu(playerUUID: String, subOptions: List<ActionDisplayOption>) {
        val player = game.seat.find { it.uuid == playerUUID } ?: return
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        val withSkip = subOptions + ActionDisplayOption(MahjongGameBehavior.SKIP, "跳過", "", MJColor.GRAY)
        spawnActionButtons(playerUUID, seatIndex, withSkip)
    }

    fun clearActionOptions(playerUUID: String) {
        actionDisplays.remove(playerUUID)?.forEach { ad ->
            ad.textDisplay.remove()
            ad.interaction.remove()
        }
    }

    fun getActionByInteraction(interactionUUID: UUID): ActionDisplay? {
        return actionDisplays.values.flatten().find { it.interaction.uniqueId == interactionUUID }
    }

    fun renderDiscards(player: MahjongPlayerBase) {
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        val existing = discardDisplays.getOrPut(player.uuid) { mutableListOf() }
        val tiles = player.discardedTilesForDisplay
        val riichiTile = player.riichiSengenTile

        val canAppend = tiles.size >= existing.size
                && riichiTile == null
                && existing.indices.all { existing[it].tile == tiles[it] }

        if (canAppend && existing.size == tiles.size) return

        val dir = seatDirection(seatIndex)
        val perp = seatPerpendicular(seatIndex)
        val halfSixTiles = WIDTH * 6 / 2.0
        val paddingFromCenter = halfSixTiles + HEIGHT / 2.0 + HEIGHT / 4.0
        val basicOffset = halfSixTiles - WIDTH / 2.0
        val yaw = seatYaw(seatIndex)
        val startX = tableCenter.x + dir[0] * paddingFromCenter + perp[0] * basicOffset
        val startZ = tableCenter.z + dir[1] * paddingFromCenter + perp[1] * basicOffset

        if (canAppend) {
            val startCol = existing.size % 6
            var leftEdge = startCol * (WIDTH + PADDING).toDouble()
            for (index in existing.size until tiles.size) {
                val tile = tiles[index]
                val row = index / 6
                val col = index % 6
                if (col == 0) leftEdge = 0.0

                val centerPerp = leftEdge + WIDTH / 2.0 - WIDTH / 2.0
                val rowOffset = row * (HEIGHT + PADDING)
                val x = startX - perp[0] * centerPerp + dir[0] * rowOffset
                val z = startZ - perp[1] * centerPerp + dir[1] * rowOffset
                val loc = Location(world, x, flatTileY, z)
                leftEdge += WIDTH + PADDING

                val display = MahjongTileDisplay(loc, tile, TileFace.FACE_UP, yaw)
                display.spawn()
                existing += display
            }
        } else {
            val oldDisplays = existing.toList()
            existing.clear()

            var leftEdge = 0.0
            tiles.forEachIndexed { index, tile ->
                val row = index / 6
                val col = index % 6
                val isRiichi = tile == riichiTile
                if (col == 0) leftEdge = 0.0

                val tileWidth = if (isRiichi) HEIGHT else WIDTH
                val centerPerp = leftEdge + tileWidth / 2.0 - WIDTH / 2.0
                val rowOffset = row * (HEIGHT + PADDING)
                val x = startX - perp[0] * centerPerp + dir[0] * rowOffset
                val z = startZ - perp[1] * centerPerp + dir[1] * rowOffset
                val loc = Location(world, x, flatTileY, z)
                leftEdge += tileWidth + PADDING

                val tileYaw = if (isRiichi) yaw - 90f else yaw
                val display = MahjongTileDisplay(loc, tile, TileFace.FACE_UP, tileYaw)
                display.spawn()
                existing += display
            }

            oldDisplays.forEach { it.remove() }
        }
    }

    fun renderFuuro(player: MahjongPlayerBase) {
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        val existing = fuuroDisplays.getOrPut(player.uuid) { mutableListOf() }
        val oldDisplays = existing.toList()
        existing.clear()

        val dir = seatDirection(seatIndex)
        val perp = seatPerpendicular(seatIndex)
        val halfTable = 1.5
        val yaw = seatYaw(seatIndex)
        val tileGap = 0.0

        val fuuroDirOffset = 0.85 + DEPTH + HEIGHT + HEIGHT + DEPTH * 2
        var curX = tableCenter.x + dir[0] * fuuroDirOffset - perp[0] * halfTable
        var curZ = tableCenter.z + dir[1] * fuuroDirOffset - perp[1] * halfTable

        var tileCount = 0
        var lastWasClaimTile = false

        player.fuuroList.forEach { fuuro ->
            val isAnkan = fuuro.mentsu is org.mahjong4j.hands.Kantsu && !fuuro.mentsu.isOpen
            val isKakan = fuuro.mentsu is com.mahjongplay.model.Kakantsu

            if (isAnkan) {
                val tiles = fuuro.tiles
                tiles.forEachIndexed { index, tile ->
                    val stepSize = if (tileCount == 0) WIDTH / 2.0 + tileGap / 2.0
                        else if (lastWasClaimTile) (HEIGHT + WIDTH) / 2.0 + tileGap
                        else WIDTH.toDouble() + tileGap

                    curX += perp[0] * stepSize
                    curZ += perp[1] * stepSize

                    val loc = Location(world, curX, flatTileY, curZ)
                    val face = if (index == 1 || index == 2) TileFace.FACE_UP else TileFace.FACE_DOWN
                    val display = MahjongTileDisplay(loc, tile, face, yaw)
                    display.spawn()
                    existing += display

                    lastWasClaimTile = false
                    tileCount++
                }
            } else {
                val sortedTiles = if (isKakan) fuuro.tiles.toMutableList()
                    else fuuro.tiles.sortedByDescending { it.sortOrder }.toMutableList()

                val kakanTile = if (isKakan) sortedTiles.removeLast() else null

                sortedTiles.remove(fuuro.claimTile)
                val claimIndex = when (fuuro.claimTarget) {
                    ClaimTarget.RIGHT -> { sortedTiles.add(0, fuuro.claimTile); 0 }
                    ClaimTarget.ACROSS -> { sortedTiles.add(1, fuuro.claimTile); 1 }
                    ClaimTarget.LEFT -> { sortedTiles.add(fuuro.claimTile); sortedTiles.size - 1 }
                    else -> -1
                }

                var claimTileX = 0.0
                var claimTileZ = 0.0

                sortedTiles.forEachIndexed { idx, tile ->
                    val isClaimTile = idx == claimIndex

                    val stepSize = when {
                        tileCount == 0 -> {
                            if (isClaimTile) HEIGHT / 2.0 + tileGap / 2.0
                            else WIDTH / 2.0 + tileGap / 2.0
                        }
                        isClaimTile || lastWasClaimTile -> (HEIGHT + WIDTH) / 2.0 + tileGap
                        else -> WIDTH.toDouble() + tileGap
                    }

                    curX += perp[0] * stepSize
                    curZ += perp[1] * stepSize

                    val halfGap = (HEIGHT - WIDTH) / 2.0
                    val posX = if (isClaimTile) curX + dir[0] * halfGap else curX
                    val posZ = if (isClaimTile) curZ + dir[1] * halfGap else curZ

                    val tileYaw = if (isClaimTile) yaw + 90f else yaw

                    val loc = Location(world, posX, flatTileY, posZ)
                    val display = MahjongTileDisplay(loc, tile, TileFace.FACE_UP, tileYaw)
                    display.spawn()
                    existing += display

                    if (isClaimTile) {
                        claimTileX = posX
                        claimTileZ = posZ
                    }
                    lastWasClaimTile = isClaimTile
                    tileCount++
                }

                kakanTile?.let { tile ->
                    val kakanX = claimTileX - dir[0] * (WIDTH.toDouble() + tileGap)
                    val kakanZ = claimTileZ - dir[1] * (WIDTH.toDouble() + tileGap)
                    val loc = Location(world, kakanX, flatTileY, kakanZ)
                    val display = MahjongTileDisplay(loc, tile, TileFace.FACE_UP, yaw + 90f)
                    display.spawn()
                    existing += display
                }
            }

            curX += perp[0] * PADDING
            curZ += perp[1] * PADDING
        }

        oldDisplays.forEach { it.remove() }
    }

    fun renderNukiDora(player: MahjongPlayerBase) {
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        val existing = nukiDoraDisplays.getOrPut(player.uuid) { mutableListOf() }

        if (player.nukiDoraTiles.isEmpty()) {
            existing.forEach { it.remove() }
            existing.clear()
            return
        }

        if (existing.size >= player.nukiDoraTiles.size) return

        val dir = seatDirection(seatIndex)
        val perp = seatPerpendicular(seatIndex)
        val halfTable = 1.5
        val yaw = seatYaw(seatIndex)
        val handDirOffset = 0.85 + DEPTH + HEIGHT

        for (index in existing.size until player.nukiDoraTiles.size) {
            val tile = player.nukiDoraTiles[index]
            val tileOffset = index * (WIDTH + PADDING)
            val perpPos = halfTable - WIDTH / 2.0 - tileOffset
            val x = tableCenter.x + dir[0] * handDirOffset - perp[0] * perpPos
            val z = tableCenter.z + dir[1] * handDirOffset - perp[1] * perpPos
            val loc = Location(world, x, flatTileY, z)

            val display = MahjongTileDisplay(loc, tile, TileFace.FACE_UP, yaw)
            display.spawn()
            existing += display
        }
    }

    fun renderRiichiIndicator(player: MahjongPlayerBase) {
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        riichiIndicatorDisplays.remove(player.uuid)?.remove()

        if (!player.riichi && !player.doubleRiichi) return

        val dir = seatDirection(seatIndex)
        val yaw = seatYaw(seatIndex)
        val halfSixTiles = WIDTH * 6 / 2.0
        val indicatorOffset = halfSixTiles * 0.6
        val x = tableCenter.x + dir[0] * indicatorOffset
        val z = tableCenter.z + dir[1] * indicatorOffset
        val loc = Location(world, x, surfaceY + 0.02, z)
        loc.yaw = yaw

        val textDisplay = world.spawnEntity(loc, EntityType.TEXT_DISPLAY) as TextDisplay
        textDisplay.isPersistent = false
        val label = if (player.doubleRiichi) "W立直" else "立直"
        textDisplay.text(
            Component.text(label, MJColor.LIGHT_PURPLE, TextDecoration.BOLD)
        )
        textDisplay.billboard = Display.Billboard.FIXED
        textDisplay.backgroundColor = Color.fromARGB(0, 0, 0, 0)
        textDisplay.brightness = Display.Brightness(15, 15)
        textDisplay.isSeeThrough = false
        textDisplay.setViewRange(0.5f)
        textDisplay.alignment = TextDisplay.TextAlignment.CENTER

        val transform = org.joml.Matrix4f()
        transform.rotateX(Math.toRadians(-90.0).toFloat())
        transform.scale(0.35f)
        textDisplay.setTransformationMatrix(transform)

        riichiIndicatorDisplays[player.uuid] = textDisplay
    }

    private fun clearRiichiIndicators() {
        riichiIndicatorDisplays.values.forEach { it.remove() }
        riichiIndicatorDisplays.clear()
    }

    private fun spawnBotNameTags() {
        clearBotNameTags()
        game.seat.forEachIndexed { seatIndex, player ->
            if (player !is MahjongBot) return@forEachIndexed

            val dir = seatDirection(seatIndex)
            val nameTagDistance = 3.2
            val x = tableCenter.x + dir[0] * nameTagDistance
            val z = tableCenter.z + dir[1] * nameTagDistance
            val loc = Location(world, x, surfaceY + 0.5, z)

            val textDisplay = world.spawnEntity(loc, EntityType.TEXT_DISPLAY) as TextDisplay
            textDisplay.isPersistent = false
            textDisplay.text(
                Component.text("\uD83E\uDD16 ", MJColor.GRAY)
                    .append(Component.text(player.displayName, MJColor.GOLD))
            )
            textDisplay.billboard = Display.Billboard.CENTER
            textDisplay.backgroundColor = Color.fromARGB(160, 0, 0, 0)
            textDisplay.brightness = Display.Brightness(15, 15)
            textDisplay.isSeeThrough = false
            textDisplay.setViewRange(0.8f)

            botNameDisplays += textDisplay
        }
    }

    private fun clearBotNameTags() {
        botNameDisplays.forEach { it.remove() }
        botNameDisplays.clear()
    }

    fun refreshVisibility() {
        game.players.forEach { updateVisibility(it) }
        actionDisplays.forEach { (uuid, displays) ->
            val owner = Bukkit.getPlayer(UUID.fromString(uuid)) ?: return@forEach
            displays.forEach { owner.showEntity(MahjongPlayPlugin.instance, it.textDisplay) }
        }
    }

    private fun updateVisibility(player: MahjongPlayerBase) {
        val backDisplays = handDisplays[player.uuid] ?: return
        val ownerOnlyDisplays = handOwnerDisplays[player.uuid] ?: return
        val ownerBukkit = Bukkit.getPlayer(UUID.fromString(player.uuid))

        val otherBukkitPlayers = game.players
            .filter { it.uuid != player.uuid }
            .mapNotNull { Bukkit.getPlayer(UUID.fromString(it.uuid)) }

        val spectators = if (game.rule.spectate) {
            Bukkit.getOnlinePlayers().filter { op -> game.players.none { it.uuid == op.uniqueId.toString() } }
        } else emptyList()

        backDisplays.forEach { display ->
            if (ownerBukkit != null) display.hideTo(ownerBukkit)
            otherBukkitPlayers.forEach { display.showTo(it) }
            spectators.forEach { display.hideTo(it) }
        }

        ownerOnlyDisplays.forEach { display ->
            if (ownerBukkit != null) display.showTo(ownerBukkit)
            otherBukkitPlayers.forEach { display.hideTo(it) }
            spectators.forEach { display.showTo(it) }
        }
    }

    fun revealHands(player: MahjongPlayerBase) {
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        clearTileSelection(player.uuid)
        handDisplays.remove(player.uuid)?.forEach { it.remove() }
        handOwnerDisplays.remove(player.uuid)?.forEach { it.remove() }
        revealedDisplays.remove(player.uuid)?.forEach { it.remove() }

        val dir = seatDirection(seatIndex)
        val perp = seatPerpendicular(seatIndex)
        val dirOffset = 0.85 + DEPTH + HEIGHT
        val yaw = seatYaw(seatIndex)
        val tileCount = player.hands.size
        val totalWidth = tileCount * WIDTH + (tileCount - 1) * PADDING
        val startOffset = totalWidth / 2.0

        val revealed = mutableListOf<MahjongTileDisplay>()

        player.hands.forEachIndexed { index, tile ->
            val tileOffset = index * (WIDTH + PADDING)
            val x = tableCenter.x + dir[0] * dirOffset + perp[0] * (startOffset - tileOffset)
            val z = tableCenter.z + dir[1] * dirOffset + perp[1] * (startOffset - tileOffset)
            val loc = Location(world, x, flatTileY, z)

            val display = MahjongTileDisplay(loc, tile, TileFace.FACE_UP, yaw)
            display.spawn()
            revealed += display
        }

        revealedDisplays[player.uuid] = revealed
    }

    private fun spawnFloatingCenterTile(tile: MahjongTile) {
        clearFloatingCenterTile()

        val floatY = surfaceY + 0.6
        val loc = Location(world, tableCenter.x, floatY, tableCenter.z)
        val display = MahjongTileDisplay(loc, tile, TileFace.STANDING, 0f)
        display.spawn()
        display.entity?.let { entity ->
            val matrix = org.joml.Matrix4f()
            matrix.scale(0.3f)
            entity.setTransformationMatrix(matrix)
            entity.billboard = Display.Billboard.CENTER
        }
        floatingCenterDisplay = display
    }

    private fun clearFloatingCenterTile() {
        floatingCenterDisplay?.remove()
        floatingCenterDisplay = null
    }

    fun clearAllDisplays() {
        highlightedDiscards.keys.toList().forEach { unhighlightDiscards(it) }
        handDisplays.values.flatten().forEach { it.remove() }
        handDisplays.clear()
        handOwnerDisplays.values.flatten().forEach { it.remove() }
        handOwnerDisplays.clear()
        revealedDisplays.values.flatten().forEach { it.remove() }
        revealedDisplays.clear()
        discardDisplays.values.flatten().forEach { it.remove() }
        discardDisplays.clear()
        fuuroDisplays.values.flatten().forEach { it.remove() }
        fuuroDisplays.clear()
        nukiDoraDisplays.values.flatten().forEach { it.remove() }
        nukiDoraDisplays.clear()
        actionDisplays.values.flatten().forEach { ad -> ad.textDisplay.remove(); ad.interaction.remove() }
        actionDisplays.clear()
        clearFloatingCenterTile()
        clearBotNameTags()
        clearRiichiIndicators()
    }
}
