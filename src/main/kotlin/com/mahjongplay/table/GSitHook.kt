package com.mahjongplay.table

import dev.geco.gsit.api.event.PreEntitySitEvent
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

internal class GSitHook(private val tableManager: MahjongTableManager) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onPreSit(event: PreEntitySitEvent) {
        val block = event.block
        if (block.type == Material.BARRIER && tableManager.isProtectedBlock(block.location)) {
            event.isCancelled = true
        }
    }
}
