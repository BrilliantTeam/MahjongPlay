package com.mahjongplay.table

import com.mahjongplay.display.BoardRenderer
import com.mahjongplay.game.GameStatus
import com.mahjongplay.game.MahjongBot
import com.mahjongplay.game.MahjongGame
import com.mahjongplay.interaction.GameRegistry
import com.mahjongplay.interaction.PaperGameBridge
import com.mahjongplay.model.MahjongRule
import com.mahjongplay.util.CancelTask
import com.mahjongplay.util.ScheduleUtil
import com.mahjongplay.util.actionBarMsg
import com.mahjongplay.util.msg
import net.kyori.adventure.text.Component
import com.mahjongplay.util.MJColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MahjongTableManager : GameRegistry {

    companion object {
        const val ADMIN_PERMISSION = "mahjongplay.admin"
        private const val MAX_TABLE_DISTANCE = 20.0
    }

    private val tables = ConcurrentHashMap<UUID, MahjongTableSession>()
    private val playerToTable = ConcurrentHashMap<String, UUID>()
    private val joinInteractionToTable = ConcurrentHashMap<UUID, UUID>()
    private val startInteractionToTable = ConcurrentHashMap<UUID, UUID>()
    private val readyInteractionToTable = ConcurrentHashMap<UUID, UUID>()
    private val countdownTasks = ConcurrentHashMap<UUID, CancelTask>()
    private val countdownRemaining = ConcurrentHashMap<UUID, Int>()
    private val tableCounter = AtomicInteger(0)
    private val interruptedPlayers = ConcurrentHashMap.newKeySet<String>()
    private var dataFolder: File? = null
    private var loading = false

    fun createTable(center: Location, creatorUUID: String, creatorName: String, gameLength: MahjongRule.GameLength = MahjongRule.GameLength.TWO_WIND, playerCount: Int = 4, startingPoints: Int = 25000, removedSeats: Set<Int> = emptySet(), quarter: Int = 0): MahjongTableSession {
        val game = MahjongGame(rule = MahjongRule(length = gameLength, playerCount = playerCount, startingPoints = startingPoints))
        val renderer = BoardRenderer(game, center, quarter)
        val bridge = PaperGameBridge(game, renderer, this)
        game.listener = bridge

        val modeText = if (gameLength == MahjongRule.GameLength.TWO_WIND && playerCount == 3) "三麻" else gameLength.displayText
        val existingNums = tables.values.map { it.humanId.substringAfterLast("]").removeSuffix("號桌").toIntOrNull() ?: 0 }.toSet()
        var tableNum = 1
        while (tableNum in existingNums) tableNum++
        val humanId = "[$modeText]${tableNum}號桌"

        val session = MahjongTableSession(
            tableId = game.tableId,
            game = game,
            renderer = renderer,
            bridge = bridge,
            center = center,
            table = MahjongTable(center, modeText, playerCount, removedSeats, quarter),
            humanId = humanId,
            ownerUUID = creatorUUID,
            ownerName = creatorName,
            quarter = quarter
        )

        tables[session.tableId] = session
        autoSave()

        return session
    }

    fun changeSettings(session: MahjongTableSession, gameLength: MahjongRule.GameLength, playerCount: Int, startingPoints: Int): String? {
        if (session.game.status != GameStatus.WAITING) return "牌局進行中無法變更設定"
        if (session.game.players.isNotEmpty()) return "請先清空座位再變更設定"

        session.bridge.cleanup()
        session.table.joinInteraction?.uniqueId?.let { joinInteractionToTable.remove(it) }
        session.table.startInteraction?.uniqueId?.let { startInteractionToTable.remove(it) }
        session.table.readyInteraction?.uniqueId?.let { readyInteractionToTable.remove(it) }

        val modeText = if (gameLength == MahjongRule.GameLength.TWO_WIND && playerCount == 3) "三麻" else gameLength.displayText
        val tableNum = session.humanId.substringAfterLast("]").removeSuffix("號桌")
        val game = MahjongGame(tableId = session.tableId, rule = MahjongRule(length = gameLength, playerCount = playerCount, startingPoints = startingPoints))
        val renderer = BoardRenderer(game, session.center, session.quarter)
        val bridge = PaperGameBridge(game, renderer, this)
        game.listener = bridge
        val table = MahjongTable(session.center, modeText, playerCount, emptySet(), session.quarter)

        val newSession = session.copy(game = game, renderer = renderer, bridge = bridge, table = table, humanId = "[$modeText]${tableNum}號桌")
        tables[session.tableId] = newSession

        ScheduleUtil.region(session.center) {
            session.table.removeEntities()
            session.table.clearSeats()
            table.spawn()
            registerJoinInteraction(newSession)
        }
        autoSave()
        return null
    }

    fun registerJoinInteraction(session: MahjongTableSession) {
        val interactionUUID = session.table.joinInteraction?.uniqueId ?: return
        joinInteractionToTable[interactionUUID] = session.tableId
        session.table.startInteraction?.uniqueId?.let { startInteractionToTable[it] = session.tableId }
        session.table.readyInteraction?.uniqueId?.let { readyInteractionToTable[it] = session.tableId }
        updateTableDisplay(session)
    }

    fun joinTable(tableId: UUID, playerUUID: String, playerName: String): Boolean {
        if (playerToTable.containsKey(playerUUID)) return false
        val session = tables[tableId] ?: return false
        if (!session.game.join(playerUUID, playerName)) return false
        playerToTable[playerUUID] = tableId
        updateTableDisplay(session)
        return true
    }

    fun getTableByJoinInteraction(interactionUUID: UUID): MahjongTableSession? {
        val tableId = joinInteractionToTable[interactionUUID] ?: return null
        return tables[tableId]
    }

    fun getTableByStartInteraction(interactionUUID: UUID): MahjongTableSession? {
        val tableId = startInteractionToTable[interactionUUID] ?: return null
        return tables[tableId]
    }

    fun getTableByReadyInteraction(interactionUUID: UUID): MahjongTableSession? {
        val tableId = readyInteractionToTable[interactionUUID] ?: return null
        return tables[tableId]
    }

    fun startGame(session: MahjongTableSession): String? {
        if (session.game.status != GameStatus.WAITING) return "牌局進行中"
        if (session.game.players.isEmpty()) return "沒有玩家"

        val unready = session.game.players.filter { it.isRealPlayer && !it.ready }
        if (unready.isNotEmpty()) return "還有玩家未準備：${unready.joinToString { it.displayName }}"

        val pc = session.game.rule.playerCount
        while (session.game.players.size < pc) {
            val botNum = session.game.players.count { !it.isRealPlayer } + 1
            if (!session.game.addBot("電腦$botNum")) break
        }

        session.table.hideActionButtons()
        startInteractionToTable.values.removeAll { it == session.tableId }
        readyInteractionToTable.values.removeAll { it == session.tableId }

        session.game.start()
        updateTableDisplay(session)
        return null
    }

    fun abortGame(session: MahjongTableSession): String? {
        if (session.game.status != GameStatus.PLAYING) return "牌局目前沒有進行中"
        session.game.end()
        return null
    }

    fun leaveTable(playerUUID: String): Boolean {
        val tableId = playerToTable[playerUUID] ?: return false
        val session = tables[tableId] ?: return false

        session.bridge.hideBarForPlayer(playerUUID)
        session.game.leave(playerUUID)
        playerToTable.remove(playerUUID)

        if (session.game.realPlayers.isEmpty()) {
            session.game.players.removeAll { it is MahjongBot }
        }

        cancelCountdown(session.tableId)
        updateTableDisplay(session)
        return true
    }

    fun destroyTable(tableId: UUID) {
        val session = tables.remove(tableId) ?: return
        cancelCountdown(tableId)
        session.game.end()
        session.bridge.cleanup()
        session.table.joinInteraction?.uniqueId?.let { joinInteractionToTable.remove(it) }
        session.table.startInteraction?.uniqueId?.let { startInteractionToTable.remove(it) }
        session.table.readyInteraction?.uniqueId?.let { readyInteractionToTable.remove(it) }
        ScheduleUtil.region(session.center) {
            session.renderer.clearAllDisplays()
            session.table.destroy()
        }
        session.game.players.forEach { playerToTable.remove(it.uuid) }
        autoSave()
    }

    fun updateTableDisplay(session: MahjongTableSession) {
        ScheduleUtil.region(session.center) {
            val isWaiting = session.game.status == GameStatus.WAITING
            val playerInfo = session.game.players.map { it.displayName to it.ready }
            session.table.updateJoinDisplay(
                playerCount = session.game.players.size,
                maxPlayers = session.game.rule.playerCount,
                waiting = isWaiting,
                playerInfo = playerInfo
            )
            startInteractionToTable.values.removeAll { it == session.tableId }
            readyInteractionToTable.values.removeAll { it == session.tableId }
            session.table.startInteraction?.uniqueId?.let { startInteractionToTable[it] = session.tableId }
            session.table.readyInteraction?.uniqueId?.let { readyInteractionToTable[it] = session.tableId }
        }
    }

    fun checkAutoStart(session: MahjongTableSession) {
        cancelCountdown(session.tableId)

        val pc = session.game.rule.playerCount
        if (session.game.status != GameStatus.WAITING) return
        if (session.game.players.size != pc) return
        if (!session.game.players.all { it.ready }) return

        countdownRemaining[session.tableId] = 3
        val cancel = ScheduleUtil.regionTimer(session.center, 0L, 20L) {
            val remaining = countdownRemaining[session.tableId] ?: return@regionTimer
            if (remaining > 0) {
                session.table.showCountdown(remaining)
                session.game.players.forEach { mjp ->
                    Bukkit.getPlayer(UUID.fromString(mjp.uuid))?.actionBarMsg(
                        Component.text("牌局將在 ${remaining} 秒後開始…", MJColor.GOLD)
                    )
                }
                countdownRemaining[session.tableId] = remaining - 1
            } else {
                cancelCountdown(session.tableId)
                if (session.game.status == GameStatus.WAITING
                    && session.game.players.size == pc
                    && session.game.players.all { it.ready }
                ) {
                    startGame(session)
                }
            }
        }
        countdownTasks[session.tableId] = cancel
    }

    fun cancelCountdown(tableId: UUID) {
        countdownTasks.remove(tableId)?.invoke()
        countdownRemaining.remove(tableId)
    }

    fun getSessionForPlayer(playerUUID: String): MahjongTableSession? {
        val tableId = playerToTable[playerUUID] ?: return null
        return tables[tableId]
    }

    fun getSession(tableId: UUID): MahjongTableSession? = tables[tableId]

    fun getAllSessions(): Collection<MahjongTableSession> = tables.values

    fun canManage(session: MahjongTableSession, player: org.bukkit.entity.Player): Boolean =
        player.hasPermission(ADMIN_PERMISSION) ||
            (session.ownerUUID.isNotEmpty() && session.ownerUUID == player.uniqueId.toString())

    fun getManageableSessions(player: org.bukkit.entity.Player): List<MahjongTableSession> =
        tables.values.filter { canManage(it, player) }

    fun getOwnedSessions(player: org.bukkit.entity.Player): List<MahjongTableSession> =
        tables.values.filter { it.ownerUUID.isNotEmpty() && it.ownerUUID == player.uniqueId.toString() }

    fun kickSeat(session: MahjongTableSession, index: Int): String? {
        if (session.game.status != GameStatus.WAITING) return "牌局進行中無法踢人"
        val target = session.game.players.getOrNull(index) ?: return "座位 $index 沒有玩家"
        if (target.isRealPlayer) {
            leaveTable(target.uuid)
            Bukkit.getPlayer(UUID.fromString(target.uuid))?.msg("你被踢出了牌桌", MJColor.RED)
        } else {
            session.game.kick(index)
            cancelCountdown(session.tableId)
            updateTableDisplay(session)
        }
        return null
    }

    fun shutdown() {
        tables.values.forEach { session ->
            if (session.game.status == GameStatus.PLAYING) {
                session.game.end()
            }
            session.bridge.cleanup()
            ScheduleUtil.region(session.center) {
                session.renderer.clearAllDisplays()
                session.table.removeEntities()
            }
            session.game.players.forEach { playerToTable.remove(it.uuid) }
        }
        tables.clear()
        joinInteractionToTable.clear()
        startInteractionToTable.clear()
        readyInteractionToTable.clear()
    }

    fun saveTables(dataFolder: File) {
        this.dataFolder = dataFolder
        val file = File(dataFolder, "tables.yml")
        val config = YamlConfiguration()
        val tableList = tables.values.map { session ->
            val map = mutableMapOf<String, Any>(
                "world" to session.center.world.name,
                "x" to session.center.blockX,
                "y" to session.center.blockY,
                "z" to session.center.blockZ,
                "gameLength" to session.game.rule.length.name,
                "playerCount" to session.game.rule.playerCount,
                "startingPoints" to session.game.rule.startingPoints,
                "owner" to session.ownerUUID,
                "ownerName" to session.ownerName,
                "quarter" to session.quarter
            )
            if (session.game.status == GameStatus.PLAYING) {
                map["playingPlayers"] = session.game.realPlayers.map { it.uuid }
            }
            if (session.table.removedSeats.isNotEmpty()) {
                map["removedSeats"] = session.table.removedSeats.toList()
            }
            map
        }
        config.set("tables", tableList)
        config.save(file)
    }

    private fun autoSave() {
        if (loading) return
        val folder = dataFolder ?: return
        saveTables(folder)
    }

    fun loadTables(dataFolder: File) {
        this.dataFolder = dataFolder
        val file = File(dataFolder, "tables.yml")
        if (!file.exists()) return
        loading = true
        val config = YamlConfiguration.loadConfiguration(file)
        val tableList = config.getMapList("tables")
        tableList.forEach { map ->
            val worldName = map["world"] as? String ?: return@forEach
            val world = Bukkit.getWorld(worldName) ?: return@forEach
            val x = (map["x"] as? Number)?.toInt() ?: return@forEach
            val y = (map["y"] as? Number)?.toInt() ?: return@forEach
            val z = (map["z"] as? Number)?.toInt() ?: return@forEach
            val gameLengthName = map["gameLength"] as? String ?: "TWO_WIND"
            val playerCount = (map["playerCount"] as? Number)?.toInt() ?: 4
            val startingPoints = (map["startingPoints"] as? Number)?.toInt() ?: 25000
            val gameLength = try { MahjongRule.GameLength.valueOf(gameLengthName) } catch (_: Exception) { MahjongRule.GameLength.TWO_WIND }

            @Suppress("UNCHECKED_CAST")
            val playingPlayers = map["playingPlayers"] as? List<String> ?: emptyList()
            playingPlayers.forEach { interruptedPlayers.add(it) }

            val owner = map["owner"] as? String ?: ""
            val ownerName = map["ownerName"] as? String ?: ""

            val removedSeats = (map["removedSeats"] as? List<*>)
                ?.mapNotNull { (it as? Number)?.toInt() }?.toSet() ?: emptySet()
            val quarter = (map["quarter"] as? Number)?.toInt() ?: 0

            val center = Location(world, x + 0.5, y.toDouble(), z + 0.5)
            val session = createTable(center, owner, ownerName, gameLength, playerCount, startingPoints, removedSeats, quarter)
            ScheduleUtil.region(center) {
                if (session.table.isIntact()) {
                    session.table.spawn()
                    registerJoinInteraction(session)
                } else {
                    session.table.clearFootprint()
                    destroyTable(session.tableId)
                }
            }
        }
        loading = false
    }

    fun getSessionByBlock(loc: org.bukkit.Location): MahjongTableSession? =
        tables.values.firstOrNull { it.table.isProtectedBlock(loc) }

    fun isProtectedBlock(loc: org.bukkit.Location): Boolean = getSessionByBlock(loc) != null

    fun respawnTablesIn(chunk: org.bukkit.Chunk) {
        tables.values.forEach { session ->
            if (session.center.world == chunk.world &&
                (session.center.blockX shr 4) == chunk.x &&
                (session.center.blockZ shr 4) == chunk.z
            ) respawnSession(session)
        }
    }

    private fun respawnSession(session: MahjongTableSession) {
        ScheduleUtil.region(session.center) {
            if (!tables.containsKey(session.tableId)) return@region
            if (!session.table.isIntact()) {
                session.table.clearFootprint()
                destroyTable(session.tableId)
                return@region
            }
            val join = session.table.joinInteraction
            if (join == null || !join.isValid) {
                session.renderer.clearAllDisplays()
                session.table.respawnDisplays()
                registerJoinInteraction(session)
            }
        }
    }

    fun breakSeat(loc: Location, player: org.bukkit.entity.Player): Boolean {
        val session = tables.values.firstOrNull { it.table.seatIndexAt(loc) >= 0 } ?: return false
        if (!canManage(session, player)) return false
        session.table.removeSeat(session.table.seatIndexAt(loc))
        autoSave()
        return true
    }

    fun notifyIfInterrupted(playerUUID: String, playerBukkit: org.bukkit.entity.Player) {
        if (interruptedPlayers.remove(playerUUID)) {
            ScheduleUtil.globalLater(40L) {
                playerBukkit.msg("你之前的牌局因伺服器重啟而中斷，非常抱歉！", MJColor.YELLOW)
            }
        }
    }

    fun refreshVisibility() {
        tables.values.forEach { session ->
            if (session.game.status != GameStatus.PLAYING) return@forEach
            ScheduleUtil.region(session.center) { session.renderer.refreshVisibility() }
        }
    }

    fun checkPlayerDistances() {
        tables.values.forEach { session ->
            session.game.realPlayers.forEach { seat ->
                val player = Bukkit.getPlayer(UUID.fromString(seat.uuid))
                if (player != null && (player.world != session.center.world || player.location.distanceSquared(session.center) > MAX_TABLE_DISTANCE * MAX_TABLE_DISTANCE)) {
                    leaveTable(seat.uuid)
                    player.msg("你距離牌桌太遠，已自動離開", MJColor.RED)
                }
            }
        }
    }

    override fun getGameForPlayer(uuid: String): MahjongGame? =
        getSessionForPlayer(uuid)?.game

    override fun getRenderer(game: MahjongGame): BoardRenderer? =
        tables[game.tableId]?.renderer
}

data class MahjongTableSession(
    val tableId: UUID,
    val game: MahjongGame,
    val renderer: BoardRenderer,
    val bridge: PaperGameBridge,
    val center: Location,
    val table: MahjongTable,
    val humanId: String,
    val ownerUUID: String,
    val ownerName: String,
    val quarter: Int = 0
)
