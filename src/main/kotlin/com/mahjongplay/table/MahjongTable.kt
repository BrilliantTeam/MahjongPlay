package com.mahjongplay.table

import com.mahjongplay.display.Props
import com.mahjongplay.display.ReadyToggle
import com.mahjongplay.display.SeatPlates
import net.kyori.adventure.text.Component
import com.mahjongplay.util.MJColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.entity.Interaction
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay
import java.util.UUID

class MahjongTable(
    val center: Location,
    val gameLengthText: String = "半莊",
    val playerCount: Int = 4,
    initialRemovedSeats: Set<Int> = emptySet(),
    val quarter: Int = 0,
    private val tableModel: String = "table_riichi",
) {

    companion object {
        fun rotOffset(dx: Int, dz: Int, quarter: Int): Pair<Int, Int> = when (quarter and 3) {
            1 -> -dz to dx
            2 -> -dx to -dz
            3 -> dz to -dx
            else -> dx to dz
        }

        fun seatOffsets(playerCount: Int): List<Pair<Int, Int>> =
            if (playerCount == 3) listOf(2 to 0, -2 to 0, 0 to 2)
            else listOf(2 to 0, 0 to -2, -2 to 0, 0 to 2)

        fun footprint(center: Location, playerCount: Int, quarter: Int = 0): List<Location> {
            val world = center.world
            val cx = center.blockX
            val cy = center.blockY
            val cz = center.blockZ
            val blocks = mutableListOf<Location>()
            for (dx in -1..1) {
                for (dz in -1..1) {
                    blocks += Location(world, (cx + dx).toDouble(), cy.toDouble(), (cz + dz).toDouble())
                    blocks += Location(world, (cx + dx).toDouble(), (cy + 1).toDouble(), (cz + dz).toDouble())
                }
            }
            seatOffsets(playerCount).forEach { (dx, dz) ->
                val (rdx, rdz) = rotOffset(dx, dz, quarter)
                blocks += Location(world, (cx + rdx).toDouble(), cy.toDouble(), (cz + rdz).toDouble())
            }
            return blocks
        }
    }

    private val placedBlocks = mutableListOf<Location>()

    val removedSeats = initialRemovedSeats.toMutableSet()

    private val seatOffsets = seatOffsets(playerCount).map { rotOffset(it.first, it.second, quarter) }

    var joinTextDisplay: TextDisplay? = null
        private set
    var joinInteraction: Interaction? = null
        private set
    private var startDisplay: ItemDisplay? = null
    var startInteraction: Interaction? = null
        private set
    private var readyToggle: ReadyToggle? = null
    private var playingDisplay: ItemDisplay? = null
    private var seatedReady: Map<java.util.UUID, Boolean> = emptyMap()
    private var tableDisplay: ItemDisplay? = null
    private var signDisplay: ItemDisplay? = null

    private val plates = SeatPlates(
        center.world, center.x, center.z, 1.5, center.blockY + 1.0 + 3.0 / 16.0,
        seatOffsets.map { (dx, dz) -> center.blockX + dx + 0.5 to center.blockZ + dz + 0.5 }
    )
    var readyInteraction: Interaction? = null
        private set

    fun spawn() {
        val world = center.world
        val cx = center.blockX
        val cy = center.blockY
        val cz = center.blockZ

        for (dx in -1..1) {
            for (dz in -1..1) {
                val fenceLoc = Location(world, (cx + dx).toDouble(), cy.toDouble(), (cz + dz).toDouble())
                fenceLoc.block.type = Material.BARRIER
                placedBlocks += fenceLoc

                val topBlock = Location(world, (cx + dx).toDouble(), (cy + 1).toDouble(), (cz + dz).toDouble()).block
                if (topBlock.type == Material.LIGHT_BLUE_CARPET || topBlock.type == Material.GREEN_CARPET) topBlock.type = Material.AIR
            }
        }

        spawnJoinDisplay()
        spawnSeatSlabs()
    }

    private fun spawnSeatSlabs() {
        seatOffsets.forEachIndexed { index, (dx, dz) ->
            if (index in removedSeats) return@forEachIndexed
            val slabLoc = Location(
                center.world,
                (center.blockX + dx).toDouble(),
                center.blockY.toDouble(),
                (center.blockZ + dz).toDouble()
            )
            if (!slabLoc.block.isReplaceable) return@forEachIndexed
            slabLoc.block.type = Material.OAK_SLAB
            placedBlocks += slabLoc
        }
    }

    fun seatIndexAt(loc: Location): Int {
        if (loc.world != center.world || loc.blockY != center.blockY) return -1
        return seatOffsets.indexOfFirst { (dx, dz) ->
            center.blockX + dx == loc.blockX && center.blockZ + dz == loc.blockZ
        }
    }

    fun removeSeat(index: Int) {
        removedSeats += index
        val (dx, dz) = seatOffsets[index]
        placedBlocks.removeAll {
            it.blockX == center.blockX + dx && it.blockY == center.blockY && it.blockZ == center.blockZ + dz
        }
    }

    private fun spawnJoinDisplay() {
        val world = center.world
        tableDisplay = Props.table(world, center.x, center.blockY, center.z, tableModel, 3.5, -(quarter and 3) * 90.0)
        signDisplay = Props.spawn(Location(center.world, center.x, center.blockY + 2.5, center.z), "sign_stand", 0.5, billboard = Display.Billboard.VERTICAL)
        val displayLoc = Location(world, center.x, center.blockY + 2.5, center.z)

        val textDisplay = world.spawnEntity(displayLoc, EntityType.TEXT_DISPLAY) as TextDisplay
        textDisplay.isPersistent = false
        textDisplay.billboard = Display.Billboard.VERTICAL
        textDisplay.backgroundColor = Color.fromARGB(90, 0, 0, 0)
        textDisplay.brightness = Display.Brightness(15, 15)
        textDisplay.isSeeThrough = false
        textDisplay.setViewRange(0.5f)
        textDisplay.alignment = TextDisplay.TextAlignment.CENTER
        joinTextDisplay = textDisplay
        layoutStrip()

        val interactionLoc = Location(world, center.x, center.blockY + 2.2, center.z)
        val interaction = world.spawnEntity(interactionLoc, EntityType.INTERACTION) as Interaction
        interaction.isPersistent = false
        interaction.interactionWidth = 2.0f
        interaction.interactionHeight = 1.0f
        interaction.isResponsive = true
        joinInteraction = interaction

        updateJoinDisplay(0, playerCount, waiting = true)
    }

    private fun spawnActionButtons() {
        val world = center.world
        val btnY = center.blockY + 1.8

        readyToggle = ReadyToggle(Location(world, center.x - 0.4, btnY, center.z)).also { it.update(seatedReady) }
        val readyInt = world.spawnEntity(Location(world, center.x - 0.4, btnY - 0.22, center.z), EntityType.INTERACTION) as Interaction
        readyInt.isPersistent = false
        readyInt.interactionWidth = 0.45f
        readyInt.interactionHeight = 0.45f
        readyInt.isResponsive = false
        readyInteraction = readyInt

        startDisplay = Props.spawn(Location(world, center.x + 0.4, btnY, center.z), startModel(), 0.45, billboard = Display.Billboard.VERTICAL)
        val startInt = world.spawnEntity(Location(world, center.x + 0.4, btnY - 0.22, center.z), EntityType.INTERACTION) as Interaction
        startInt.isPersistent = false
        startInt.interactionWidth = 0.45f
        startInt.interactionHeight = 0.45f
        startInt.isResponsive = false
        startInteraction = startInt
    }

    fun hideActionButtons() {
        startDisplay?.remove(); startDisplay = null
        startInteraction?.remove(); startInteraction = null
        readyToggle?.remove(); readyToggle = null
        readyInteraction?.remove(); readyInteraction = null
    }

    fun showActionButtons() {
        if (startDisplay == null) spawnActionButtons()
    }

    fun updateJoinDisplay(playerCount: Int, maxPlayers: Int, waiting: Boolean) {
        joinWaiting = waiting
        if (waiting) {
            playingDisplay?.remove()
            playingDisplay = null
        } else if (playingDisplay == null) {
            playingDisplay = Props.spawn(Location(center.world, center.x, center.blockY + 2.5, center.z), "common/playing_stand", 0.38, billboard = Display.Billboard.VERTICAL)
        }
        if (waiting) {
            if (playerCount > 0) showActionButtons() else hideActionButtons()
        } else {
            hideActionButtons()
        }
        renderJoinText()
    }

    private var joinWaiting = true

    private fun startModel(): String =
        if (seatedReady.isNotEmpty() && seatedReady.values.all { it }) "common/start_stand" else "common/start_off_stand"

    private fun refreshActionStates(entries: List<Triple<String, *, java.util.UUID?>?>) {
        seatedReady = entries.filterNotNull().mapNotNull { entry -> entry.third?.let { it to (entry.first == "ready") } }.toMap()
        readyToggle?.update(seatedReady)
        startDisplay?.setItemStack(Props.item(startModel()))
    }

    private fun renderJoinText() {
        val textDisplay = joinTextDisplay ?: return
        val mode = Component.text(gameLengthText, MJColor.AQUA)
        val state = if (joinWaiting) Props.glyph(Props.MOUSE).append(Component.text("  ")) else Component.empty()
        textDisplay.text(if (joinWaiting || plates.hasSlots()) mode.append(Component.newline()).append(state).append(plates.slots()) else mode)
        layoutStrip()
    }

    private fun layoutStrip(counting: Boolean = false) {
        val twoLines = counting || joinWaiting || plates.hasSlots()
        val slots = if (counting) 0.6 else plates.slotsWidth() + if (joinWaiting) 0.45 else 0.0
        val half = maxOf(Props.textWidth(gameLengthText), if (twoLines) slots else 0.0) / 2 + 0.05
        val middle = if (twoLines) 0.25 else 0.125
        signDisplay?.let { Props.placeInStrip(it, -half - 0.28, middle, 0.5) }
        playingDisplay?.let { Props.placeInStrip(it, half + 0.22, middle, 0.38) }
    }

    fun showCountdown(seconds: Int) {
        val textDisplay = joinTextDisplay ?: return
        textDisplay.text(
            Component.text(gameLengthText, MJColor.AQUA)
                .append(Component.newline())
                .append(Props.glyph(Props.CLOCK)).append(Component.text(" ")).append(Props.number(seconds))
        )
        layoutStrip(counting = true)
    }

    fun updatePlates(entries: List<Triple<String, Component?, java.util.UUID?>?>) {
        entries.forEachIndexed { index, entry -> plates.show(index, entry?.first, entry?.second, entry?.third) }
        refreshActionStates(entries)
        renderJoinText()
    }

    fun isProtectedBlock(loc: Location): Boolean {
        return placedBlocks.any { it.blockX == loc.blockX && it.blockY == loc.blockY && it.blockZ == loc.blockZ && it.world == loc.world }
    }

    fun isIntact(): Boolean {
        val world = center.world
        val cx = center.blockX
        val cy = center.blockY
        val cz = center.blockZ
        for (dx in -1..1) {
            for (dz in -1..1) {
                if (Location(world, (cx + dx).toDouble(), cy.toDouble(), (cz + dz).toDouble()).block.type.let { it != Material.BARRIER && it != Material.OAK_FENCE }) return false
            }
        }
        return true
    }

    fun clearFootprint() {
        footprint(center, playerCount, quarter).forEach { it.block.setType(Material.AIR, false) }
    }

    fun clearSeats() {
        val seatCells = seatOffsets.map { (dx, dz) -> (center.blockX + dx) to (center.blockZ + dz) }
        placedBlocks.filter { it.blockY == center.blockY && (it.blockX to it.blockZ) in seatCells }
            .forEach { it.block.setType(Material.AIR, false) }
    }

    fun respawnDisplays() {
        removeEntities()
        spawnJoinDisplay()
    }

    fun removeEntities() {
        joinTextDisplay?.remove(); joinTextDisplay = null
        joinInteraction?.remove(); joinInteraction = null
        tableDisplay?.remove(); tableDisplay = null
        signDisplay?.remove(); signDisplay = null
        playingDisplay?.remove(); playingDisplay = null
        plates.clear()
        hideActionButtons()
    }

    fun destroy() {
        placedBlocks.forEach { it.block.setType(Material.AIR, false) }
        placedBlocks.clear()
        joinTextDisplay?.remove(); joinTextDisplay = null
        joinInteraction?.remove(); joinInteraction = null
        tableDisplay?.remove(); tableDisplay = null
        signDisplay?.remove(); signDisplay = null
        playingDisplay?.remove(); playingDisplay = null
        plates.clear()
        hideActionButtons()
    }
}
