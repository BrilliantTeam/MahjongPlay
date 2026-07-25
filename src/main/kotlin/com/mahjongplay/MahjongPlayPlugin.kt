package com.mahjongplay

import com.mahjongplay.interaction.EntityInteractionListener
import com.mahjongplay.table.GSitHook
import com.mahjongplay.table.MahjongCommand
import com.mahjongplay.table.MahjongTableManager
import com.mahjongplay.util.ScheduleUtil
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.plugin.java.JavaPlugin
import fr.skytasul.glowingentities.GlowingEntities

class MahjongPlayPlugin : JavaPlugin(), Listener {

    companion object {
        lateinit var instance: MahjongPlayPlugin
            private set
    }

    lateinit var tableManager: MahjongTableManager
        private set

    lateinit var glowingEntities: GlowingEntities
        private set

    override fun onEnable() {
        instance = this
        glowingEntities = GlowingEntities(this)
        tableManager = MahjongTableManager()

        getCommand("mahjong")?.let {
            val cmd = MahjongCommand(tableManager)
            it.setExecutor(cmd)
            it.tabCompleter = cmd
        }

        server.pluginManager.registerEvents(EntityInteractionListener(tableManager), this)
        server.pluginManager.registerEvents(this, this)
        if (server.pluginManager.isPluginEnabled("GSit")) {
            server.pluginManager.registerEvents(GSitHook(tableManager), this)
        }

        ScheduleUtil.globalLater(20L) {
            tableManager.loadTables(dataFolder)
        }

        logger.info("MahjongCraft v${pluginMeta.version} enabled!")
    }

    override fun onDisable() {
        if (::glowingEntities.isInitialized) {
            glowingEntities.disable()
        }
        if (::tableManager.isInitialized) {
            tableManager.saveTables(dataFolder)
            tableManager.shutdown()
        }
        logger.info("MahjongCraft disabled.")
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        tableManager.notifyIfInterrupted(event.player.uniqueId.toString(), event.player)
        tableManager.refreshVisibility()
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId.toString()
        tableManager.leaveTable(uuid)
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        tableManager.respawnTablesIn(event.chunk)
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!tableManager.isProtectedBlock(event.block.location)) return
        if (tableManager.breakSeat(event.block.location, event.player)) {
            event.isDropItems = false
        } else {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (event.blocks.any { tableManager.isProtectedBlock(it.location) || tableManager.isProtectedBlock(it.getRelative(event.direction).location) }) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (event.blocks.any { tableManager.isProtectedBlock(it.location) || tableManager.isProtectedBlock(it.getRelative(event.direction).location) }) {
            event.isCancelled = true
        }
    }
}
