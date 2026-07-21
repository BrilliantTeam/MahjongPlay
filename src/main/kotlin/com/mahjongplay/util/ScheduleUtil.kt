package com.mahjongplay.util

import com.mahjongplay.MahjongPlayPlugin
import org.bukkit.Bukkit
import org.bukkit.Location

/** 取消排程用的 handle。兩個平台取消方式不同, 統一包成 lambda。 */
typealias CancelTask = () -> Unit

/**
 * Folia 相容的排程封裝。
 *
 * Folia 把世界切成多個 region、各自跑一條執行緒, 所以動到實體或方塊的工作
 * 必須排進「該座標所屬 region」的排程器 ([region] 系列);
 * 只送訊息 / BossBar 這種不碰世界狀態的工作放 [global] 即可。
 * 一般 Paper 沒有 region 概念, 兩者都退回主執行緒排程。
 */
object ScheduleUtil {

    val isFolia: Boolean = runCatching {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
    }.isSuccess

    private val plugin get() = MahjongPlayPlugin.instance

    // Folia 的 runAtFixedRate/runDelayed 不接受 <= 0 的 tick, Paper 則允許 0。統一夾到最小 1。
    private fun Long.atLeastOneTick() = coerceAtLeast(1L)

    fun global(task: Runnable) {
        if (isFolia) Bukkit.getGlobalRegionScheduler().run(plugin) { task.run() }
        else Bukkit.getScheduler().runTask(plugin, task)
    }

    fun globalLater(delayTicks: Long, task: Runnable) {
        if (isFolia) Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { task.run() }, delayTicks.atLeastOneTick())
        else Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)
    }

    fun globalTimer(delayTicks: Long, periodTicks: Long, task: Runnable): CancelTask {
        if (isFolia) {
            val handle = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, { task.run() }, delayTicks.atLeastOneTick(), periodTicks.atLeastOneTick())
            return { handle.cancel() }
        }
        val id = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks).taskId
        return { Bukkit.getScheduler().cancelTask(id) }
    }

    fun region(loc: Location, task: Runnable) {
        if (isFolia) Bukkit.getRegionScheduler().run(plugin, loc) { task.run() }
        else Bukkit.getScheduler().runTask(plugin, task)
    }

    fun regionLater(loc: Location, delayTicks: Long, task: Runnable) {
        if (isFolia) Bukkit.getRegionScheduler().runDelayed(plugin, loc, { task.run() }, delayTicks.atLeastOneTick())
        else Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)
    }

    fun regionTimer(loc: Location, delayTicks: Long, periodTicks: Long, task: Runnable): CancelTask {
        if (isFolia) {
            val handle = Bukkit.getRegionScheduler()
                .runAtFixedRate(plugin, loc, { task.run() }, delayTicks.atLeastOneTick(), periodTicks.atLeastOneTick())
            return { handle.cancel() }
        }
        val id = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks).taskId
        return { Bukkit.getScheduler().cancelTask(id) }
    }
}
