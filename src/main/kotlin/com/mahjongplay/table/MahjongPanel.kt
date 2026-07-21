package com.mahjongplay.table

import com.mahjongplay.game.GameStatus
import com.mahjongplay.model.MahjongRule
import com.mahjongplay.util.MESSAGE_PREFIX
import com.mahjongplay.util.ScheduleUtil
import com.mahjongplay.util.msg
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import com.mahjongplay.util.MJColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Location
import org.bukkit.entity.Player
import java.time.Duration

object MahjongPanel {

    const val WAND_MODEL_DATA = 38

    private val residenceAvailable: Boolean by lazy {
        org.bukkit.Bukkit.getPluginManager().isPluginEnabled("Residence")
    }

    private val REUSABLE: ClickCallback.Options = ClickCallback.Options.builder()
        .uses(ClickCallback.UNLIMITED_USES)
        .lifetime(Duration.ofMinutes(10))
        .build()

    private val ONCE: ClickCallback.Options = ClickCallback.Options.builder()
        .uses(1)
        .lifetime(Duration.ofMinutes(2))
        .build()

    private data class Mode(
        val label: String,
        val length: MahjongRule.GameLength,
        val playerCount: Int,
        val startingPoints: Int,
        val hint: String
    )

    private val MODES = listOf(
        Mode("一局", MahjongRule.GameLength.ONE_GAME, 4, 25000, "只打一局就結算，最適合快速體驗"),
        Mode("東風", MahjongRule.GameLength.EAST, 4, 25000, "打完東一到東四即結束"),
        Mode("半莊", MahjongRule.GameLength.TWO_WIND, 4, 25000, "東風加南風，共八局"),
        Mode("三麻", MahjongRule.GameLength.TWO_WIND, 3, 35000, "三人半莊，移除二萬至八萬，起始 35000 點")
    )

    fun sendModePrompt(manager: MahjongTableManager, player: Player, center: Location) {
        var msg = MESSAGE_PREFIX.append(Component.text("選擇牌桌模式：", MJColor.GOLD))
        MODES.forEach { mode ->
            msg = msg.append(Component.space())
                .append(button("[${mode.label}]", MJColor.AQUA, ONCE, mode.hint) { create(manager, it, center, mode) })
        }
        player.sendMessage(msg)
    }

    fun open(manager: MahjongTableManager, player: Player, showAll: Boolean = false) {
        val uuid = player.uniqueId.toString()
        player.sendMessage(MESSAGE_PREFIX.append(Component.text("麻將面板", MJColor.GOLD).decorate(TextDecoration.BOLD)))

        val current = manager.getSessionForPlayer(uuid)
        val manageable =
            (if (showAll) manager.getManageableSessions(player) else manager.getOwnedSessions(player)).take(8)
        manageable.forEach { session ->
            player.sendMessage(MESSAGE_PREFIX.append(statusLine(session)))
            player.sendMessage(
                MESSAGE_PREFIX.append(
                    manageButtons(manager, session, seated = current != null, own = session.ownerUUID == uuid)
                )
            )
        }

        if (current != null) {
            player.sendMessage(MESSAGE_PREFIX.append(memberButtons(manager, current, uuid)))
        } else if (manageable.isEmpty()) {
            player.msg("要加入別人的牌桌請到牌桌前右鍵加入區", MJColor.DARK_GRAY)
            if (player.hasPermission("mahjongplay.command.create")) {
                player.msg("手持麻將牌左鍵方塊即可建立自己的牌桌", MJColor.DARK_GRAY)
            }
        }
        if (!showAll && player.hasPermission(MahjongTableManager.ADMIN_PERMISSION)) {
            player.msg("輸入 /mahjong all 可列出並管理全服牌桌", MJColor.DARK_GRAY)
        }
    }

    private fun statusLine(session: MahjongTableSession): Component {
        val game = session.game
        val status = if (game.status == GameStatus.WAITING) "等待中" else "進行中"
        val names = game.players.joinToString("、") { "${it.displayName}${if (it.ready) "✓" else "✗"}" }
        val owner = if (session.ownerName.isEmpty()) "" else "（${session.ownerName}）"
        val line = Component.text(
            "${session.humanId}$owner ${game.players.size}/${game.rule.playerCount} $status",
            MJColor.AQUA
        )
        return if (names.isEmpty()) line else line.append(Component.text(" 〔$names〕", MJColor.GRAY))
    }

    private fun manageButtons(
        manager: MahjongTableManager,
        session: MahjongTableSession,
        seated: Boolean,
        own: Boolean
    ): Component {
        var line = Component.text("  ", MJColor.GRAY)
        if (session.game.status == GameStatus.WAITING) {
            if (own && !seated && session.game.players.size < session.game.rule.playerCount) {
                line = line.append(button("[加入]", MJColor.GREEN, REUSABLE, "入座自己的牌桌") { join(manager, it, session) })
                    .append(Component.space())
            }
            line = line.append(button("[開始]", MJColor.GOLD, REUSABLE, "立刻開局，空位會自動補上機器人") { start(manager, it, session) })
                .append(Component.space())
                .append(button("[機器人]", MJColor.GREEN, REUSABLE, "在空位加入一名機器人") { addBot(manager, it, session) })
                .append(Component.space())
                .append(button("[踢人]", MJColor.YELLOW, REUSABLE, "展開座位清單，選擇要踢出的對象") { sendKickPrompt(manager, it, session) })
                .append(Component.space())
        }
        return line.append(button("[規則]", MJColor.AQUA, REUSABLE, "查看這張牌桌的完整規則") { sendRules(it, session) })
            .append(Component.space())
            .append(button("[銷毀]", MJColor.RED, REUSABLE, "拆掉 ${session.humanId}，方塊與牌面都會清除") { destroy(manager, it, session) })
    }

    private fun memberButtons(manager: MahjongTableManager, session: MahjongTableSession, uuid: String): Component {
        var line = Component.text("你在 ${session.humanId}：", MJColor.YELLOW)
        if (session.game.status == GameStatus.WAITING) {
            val ready = session.game.players.find { it.uuid == uuid }?.ready == true
            val label = if (ready) "[取消準備]" else "[準備]"
            val color = if (ready) MJColor.GRAY else MJColor.GREEN
            val hover = if (ready) "取消準備，開局倒數會中止" else "標記為準備完成，全員準備後倒數 3 秒開局"
            line = line.append(button(label, color, REUSABLE, hover) { toggleReady(manager, it) }).append(Component.space())
        }
        return line.append(button("[規則]", MJColor.AQUA, REUSABLE, "查看這張牌桌的完整規則") { sendRules(it, session) })
            .append(Component.space())
            .append(button("[離開]", MJColor.RED, REUSABLE, "離開 ${session.humanId}") { leave(manager, it) })
    }

    private fun sendRules(player: Player, session: MahjongTableSession) {
        player.msg("${session.humanId} 的規則", MJColor.GOLD)
        session.game.rule.toComponents().forEach { player.sendMessage(it) }
    }

    private fun create(manager: MahjongTableManager, player: Player, center: Location, mode: Mode) {
        if (!player.hasPermission("mahjongplay.command.create")) {
            player.msg("你沒有權限建立麻將桌", MJColor.RED)
            return
        }
        val tooClose = manager.getAllSessions().any {
            it.center.world == center.world && it.center.distanceSquared(center) < 36.0
        }
        if (tooClose) {
            player.msg("這裡太靠近其他麻將桌了", MJColor.RED)
            return
        }
        val blocked = placementError(player, center, mode.playerCount)
        if (blocked != null) {
            player.msg(blocked, MJColor.RED)
            return
        }

        val session = manager.createTable(
            center, player.uniqueId.toString(), player.name,
            mode.length, mode.playerCount, mode.startingPoints
        )
        ScheduleUtil.region(center) {
            session.table.spawn()
            manager.registerJoinInteraction(session)
        }
        player.msg("已建立 ${session.humanId}，只有你能管理這張牌桌", MJColor.GREEN)
        open(manager, player)
    }

    private fun placementError(player: Player, center: Location, playerCount: Int): String? {
        val blocks = MahjongTable.footprint(center, playerCount)
        if (residenceAvailable && blocks.any { !ResidenceHook.canBuild(player, it) }) {
            return "你在這塊領地沒有建築權限"
        }
        if (blocks.any { !it.block.isReplaceable }) {
            return "空間不足，牌桌需要 5x5 範圍與兩格高的淨空"
        }
        return null
    }

    private fun start(manager: MahjongTableManager, player: Player, session: MahjongTableSession) {
        if (!guard(manager, session, player, "mahjongplay.command.start")) return
        val error = manager.startGame(session)
        if (error != null) player.msg(error, MJColor.RED)
    }

    private fun addBot(manager: MahjongTableManager, player: Player, session: MahjongTableSession) {
        if (!guard(manager, session, player, "mahjongplay.command.bot")) return
        val game = session.game
        if (game.status != GameStatus.WAITING) {
            player.msg("遊戲已經開始", MJColor.RED)
            return
        }
        if (game.players.size >= game.rule.playerCount) {
            player.msg("牌桌已滿", MJColor.RED)
            return
        }
        val botNum = game.players.count { !it.isRealPlayer } + 1
        game.addBot("Bot$botNum")
        manager.updateTableDisplay(session)
        manager.checkAutoStart(session)
        open(manager, player)
    }

    private fun sendKickPrompt(manager: MahjongTableManager, player: Player, session: MahjongTableSession) {
        if (!guard(manager, session, player, "mahjongplay.command.kick")) return
        if (session.game.players.isEmpty()) {
            player.msg("牌桌上沒有玩家", MJColor.YELLOW)
            return
        }
        var msg = MESSAGE_PREFIX.append(Component.text("選擇要踢出的座位：", MJColor.GOLD))
        session.game.players.forEachIndexed { index, seated ->
            val who = if (seated.isRealPlayer) "玩家" else "機器人"
            msg = msg.append(Component.space()).append(
                button("[$index.${seated.displayName}]", MJColor.YELLOW, ONCE, "把${who} ${seated.displayName} 踢出牌桌") {
                    kick(manager, it, session, index)
                }
            )
        }
        player.sendMessage(msg)
    }

    private fun kick(manager: MahjongTableManager, player: Player, session: MahjongTableSession, index: Int) {
        if (!guard(manager, session, player, "mahjongplay.command.kick")) return
        val error = manager.kickSeat(session, index)
        if (error != null) player.msg(error, MJColor.RED) else open(manager, player)
    }

    private fun destroy(manager: MahjongTableManager, player: Player, session: MahjongTableSession) {
        if (!guard(manager, session, player, "mahjongplay.command.destroy")) return
        val name = session.humanId
        manager.destroyTable(session.tableId)
        player.msg("$name 已銷毀", MJColor.YELLOW)
    }

    private fun toggleReady(manager: MahjongTableManager, player: Player) {
        if (!player.hasPermission("mahjongplay.command.ready")) {
            player.msg("你沒有權限準備", MJColor.RED)
            return
        }
        val uuid = player.uniqueId.toString()
        val session = manager.getSessionForPlayer(uuid)
        if (session == null) {
            player.msg("你不在任何牌桌中", MJColor.RED)
            return
        }
        val me = session.game.players.find { it.uuid == uuid } ?: return
        session.game.readyOrNot(uuid, !me.ready)
        manager.updateTableDisplay(session)
        manager.checkAutoStart(session)
        open(manager, player)
    }

    private fun leave(manager: MahjongTableManager, player: Player) {
        if (!player.hasPermission("mahjongplay.command.leave")) {
            player.msg("你沒有權限離開麻將桌", MJColor.RED)
            return
        }
        if (manager.leaveTable(player.uniqueId.toString())) {
            player.msg("已離開牌桌", MJColor.YELLOW)
            open(manager, player)
        } else {
            player.msg("你不在任何牌桌中", MJColor.RED)
        }
    }

    private fun join(manager: MahjongTableManager, player: Player, session: MahjongTableSession) {
        if (!player.hasPermission("mahjongplay.command.join")) {
            player.msg("你沒有權限加入麻將桌", MJColor.RED)
            return
        }
        if (manager.getSession(session.tableId) == null) {
            player.msg("這張牌桌已不存在", MJColor.RED)
            return
        }
        if (manager.joinTable(session.tableId, player.uniqueId.toString(), player.name)) {
            player.msg("已加入 ${session.humanId}", MJColor.GREEN)
            manager.checkAutoStart(session)
            open(manager, player)
        } else {
            player.msg("無法加入（可能已滿或你已在其他牌桌）", MJColor.RED)
        }
    }

    private fun guard(
        manager: MahjongTableManager,
        session: MahjongTableSession,
        player: Player,
        permission: String
    ): Boolean {
        if (manager.getSession(session.tableId) == null) {
            player.msg("這張牌桌已不存在", MJColor.RED)
            return false
        }
        if (!manager.canManage(session, player)) {
            player.msg("這不是你的牌桌", MJColor.RED)
            return false
        }
        if (!player.hasPermission(permission)) {
            player.msg("你沒有權限這麼做", MJColor.RED)
            return false
        }
        return true
    }

    private fun button(
        label: String,
        color: TextColor,
        options: ClickCallback.Options,
        hover: String,
        action: (Player) -> Unit
    ): Component = Component.text(label, color)
        .hoverEvent(HoverEvent.showText(Component.text(hover, MJColor.YELLOW)))
        .clickEvent(
            ClickEvent.callback(ClickCallback<Audience> { audience -> (audience as? Player)?.let(action) }, options)
        )
}
