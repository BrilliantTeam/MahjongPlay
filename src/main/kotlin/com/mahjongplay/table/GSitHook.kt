package com.mahjongplay.table

import dev.geco.gsit.api.event.PreEntitySitEvent
import org.bukkit.Tag
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

internal class GSitHook(private val tableManager: MahjongTableManager) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onPreSit(event: PreEntitySitEvent) {
        val block = event.block
        if (Tag.WOOL_CARPETS.isTagged(block.type) && tableManager.isProtectedBlock(block.location)) {
            event.isCancelled = true
        }
    }
}
