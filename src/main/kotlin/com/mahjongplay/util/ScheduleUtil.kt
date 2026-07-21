package com.mahjongplay.util

import com.mahjongplay.MahjongPlayPlugin
import org.bukkit.Bukkit
import org.bukkit.Location

typealias CancelTask = () -> Unit

object ScheduleUtil {

    val isFolia: Boolean = runCatching {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
    }.isSuccess

    private val plugin get() = MahjongPlayPlugin.instance

    private fun Long.atLeastOneTick() = coerceAtLeast(1L)

    private fun serverStopping() = Bukkit.isStopping()

    private fun cannotSchedule() = !plugin.isEnabled

    fun global(task: Runnable) {
        if (serverStopping()) return
        if (cannotSchedule()) { task.run(); return }
        if (isFolia) Bukkit.getGlobalRegionScheduler().run(plugin) { task.run() }
        else Bukkit.getScheduler().runTask(plugin, task)
    }

    fun globalLater(delayTicks: Long, task: Runnable) {
        if (serverStopping() || cannotSchedule()) return
        if (isFolia) Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { task.run() }, delayTicks.atLeastOneTick())
        else Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)
    }

    fun globalTimer(delayTicks: Long, periodTicks: Long, task: Runnable): CancelTask {
        if (serverStopping() || cannotSchedule()) return {}
        if (isFolia) {
            val handle = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, { task.run() }, delayTicks.atLeastOneTick(), periodTicks.atLeastOneTick())
            return { handle.cancel() }
        }
        val id = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks).taskId
        return { Bukkit.getScheduler().cancelTask(id) }
    }

    fun region(loc: Location, task: Runnable) {
        if (serverStopping()) return
        if (cannotSchedule()) { task.run(); return }
        if (isFolia) Bukkit.getRegionScheduler().run(plugin, loc) { task.run() }
        else Bukkit.getScheduler().runTask(plugin, task)
    }

    fun regionLater(loc: Location, delayTicks: Long, task: Runnable) {
        if (serverStopping() || cannotSchedule()) return
        if (isFolia) Bukkit.getRegionScheduler().runDelayed(plugin, loc, { task.run() }, delayTicks.atLeastOneTick())
        else Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)
    }

    fun regionTimer(loc: Location, delayTicks: Long, periodTicks: Long, task: Runnable): CancelTask {
        if (serverStopping() || cannotSchedule()) return {}
        if (isFolia) {
            val handle = Bukkit.getRegionScheduler()
                .runAtFixedRate(plugin, loc, { task.run() }, delayTicks.atLeastOneTick(), periodTicks.atLeastOneTick())
            return { handle.cancel() }
        }
        val id = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks).taskId
        return { Bukkit.getScheduler().cancelTask(id) }
    }
}
