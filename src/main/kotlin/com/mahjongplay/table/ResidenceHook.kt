package com.mahjongplay.table

import com.bekvon.bukkit.residence.Residence
import com.bekvon.bukkit.residence.containers.Flags
import com.bekvon.bukkit.residence.protection.FlagPermissions
import org.bukkit.Location
import org.bukkit.entity.Player

internal object ResidenceHook {

    fun canBuild(player: Player, loc: Location): Boolean =
        permsAt(loc).playerHas(player, Flags.getFlag("build"), true)

    private fun permsAt(loc: Location): FlagPermissions {
        val residence = Residence.getInstance().residenceManager.getByLoc(loc)
        return residence?.permissions ?: Residence.getInstance().worldFlags.getPerms(loc.world.name)
    }
}
