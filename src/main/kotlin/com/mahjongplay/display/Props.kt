package com.mahjongplay.display

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import com.mahjongplay.MahjongPlayPlugin
import com.google.gson.JsonParser
import net.kyori.adventure.text.format.ShadowColor
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.joml.Matrix4f
import java.awt.image.BufferedImage
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

object Props {

    private const val ITEM_DISPLAY_FLIP = 180.0

    private const val GAME = "mahjong"

    fun item(name: String): ItemStack = ItemStack(Material.PAPER).apply {
        editMeta { it.setItemModel(NamespacedKey("brilliant", "tabletop/" + if ('/' in name) name else "$GAME/$name")) }
    }

    fun spawn(
        loc: Location,
        name: String,
        scale: Double,
        yawDeg: Double = 0.0,
        billboard: Display.Billboard = Display.Billboard.FIXED,
        lit: Boolean = false,
    ): ItemDisplay {
        val display = loc.world.spawnEntity(loc, EntityType.ITEM_DISPLAY) as ItemDisplay
        display.isPersistent = false
        display.setItemStack(item(name))
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE)
        display.billboard = billboard
        display.setViewRange(1.0f)
        if (lit) display.brightness = Display.Brightness(15, 15)
        display.setTransformationMatrix(Matrix4f().rotateY(Math.toRadians(yawDeg + ITEM_DISPLAY_FLIP).toFloat()).scale(scale.toFloat()))
        return display
    }

    fun placeInStrip(display: ItemDisplay, x: Double, y: Double, scale: Double) {
        display.setTransformationMatrix(
            Matrix4f().translate(x.toFloat(), y.toFloat(), 0f)
                .rotateY(Math.toRadians(ITEM_DISPLAY_FLIP).toFloat()).scale(scale.toFloat())
        )
    }

    fun textWidth(text: String): Double = text.sumOf { if (it.code > 255) 0.225 else 0.15 }

    fun table(world: World, x: Double, floorY: Int, z: Double, name: String, size: Double, yawDeg: Double): ItemDisplay =
        spawn(Location(world, x, floorY + size / 2, z), name, size, yawDeg)

    const val CLOCK = 0xE940
    const val TURN = 0xE941
    const val MOUSE = 0xE970
    const val SLOT_EMPTY = 0xE971
    const val SLOT_SEATED = 0xE972
    const val READY_MARK = 0xEB0B
    const val SLOT_GAP = 0xEB0C
    const val DIGIT = 0xE980

    fun clock(remaining: Long, limit: Int): Component = glyph(
        when {
            limit <= 0 || remaining > limit / 2 -> CLOCK
            remaining > limit / 4 -> 0xE948
            else -> 0xE949
        }
    )

    fun number(value: Int): Component {
        var out: Component = Component.empty()
        value.toString().forEach { out = out.append(glyph(DIGIT + (it - '0'))) }
        return out
    }

    fun glyph(code: Int, color: TextColor = NamedTextColor.WHITE): Component =
        Component.empty().append(Component.text(String(Character.toChars(code))).font(Key.key("brilliant", "tabletop")).color(color))

    fun label(loc: Location, text: Component, scale: Float = 0.45f): TextDisplay {
        val display = loc.world.spawnEntity(loc, EntityType.TEXT_DISPLAY) as TextDisplay
        display.isPersistent = false
        display.billboard = Display.Billboard.CENTER
        display.backgroundColor = Color.fromARGB(110, 0, 0, 0)
        display.isShadowed = true
        display.setViewRange(0.5f)
        display.alignment = TextDisplay.TextAlignment.CENTER
        display.setTransformationMatrix(Matrix4f().scale(scale))
        display.text(text)
        return display
    }
}

class ReadyToggle(loc: Location) {
    private val ready = button(loc, "common/ready_stand")
    private val cancel = button(loc, "common/cancel_stand")
    private var viewers = emptySet<java.util.UUID>()

    private fun button(loc: Location, name: String): ItemDisplay =
        Props.spawn(loc, name, 0.45, billboard = Display.Billboard.VERTICAL).also { it.setVisibleByDefault(false) }

    fun update(seated: Map<java.util.UUID, Boolean>) {
        val plugin = MahjongPlayPlugin.instance
        (viewers - seated.keys).forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let { it.hideEntity(plugin, ready); it.hideEntity(plugin, cancel) }
        }
        seated.forEach { (uuid, isReady) ->
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            player.showEntity(plugin, if (isReady) cancel else ready)
            player.hideEntity(plugin, if (isReady) ready else cancel)
        }
        viewers = seated.keys
    }

    fun remove() {
        ready.remove()
        cancel.remove()
    }
}

class SeatPlates(
    private val world: World,
    private val centerX: Double,
    private val centerZ: Double,
    private val half: Double,
    private val surfaceY: Double,
    private val seats: List<Pair<Double, Double>>,
) {
    private val plates = arrayOfNulls<ItemDisplay>(seats.size)
    private val names = arrayOfNulls<TextDisplay>(seats.size)
    private val shown = arrayOfNulls<Triple<String, Component?, java.util.UUID?>>(seats.size)

    private fun spot(index: Int): Pair<Location, Double> {
        val (sx, sz) = seats[index]
        val reach = half + 0.12
        val x = sx.coerceIn(centerX - reach, centerX + reach)
        val z = sz.coerceIn(centerZ - reach, centerZ + reach)
        val yaw = Math.toDegrees(Math.atan2(sx - x, sz - z))
        return Location(world, x, surfaceY + 0.125, z) to yaw
    }

    fun hasSlots(): Boolean = shown.any { it != null }

    fun slotsWidth(): Double = shown.filterNotNull().sumOf { if (it.third != null && it.first == "ready") 0.475 else 0.3 }

    fun slots(): Component {
        var out: Component = Component.empty()
        shown.forEach { entry ->
            entry ?: return@forEach
            val owner = entry.third
            val slot = when {
                owner != null -> PlayerFaces.of(owner.toString())
                entry.first == "empty" -> Props.glyph(Props.SLOT_EMPTY)
                else -> Props.glyph(Props.SLOT_SEATED)
            }
            out = out.append(slot)
            if (owner != null && entry.first == "ready") out = out.append(Props.glyph(Props.READY_MARK))
            out = out.append(Props.glyph(Props.SLOT_GAP))
        }
        return out
    }

    fun show(index: Int, state: String?, label: Component?, owner: java.util.UUID? = null) {
        if (index !in seats.indices) return
        if (state == null) {
            plates[index]?.remove(); plates[index] = null
            names[index]?.remove(); names[index] = null
            shown[index] = null
            return
        }
        val previous = shown[index]?.third
        if (shown[index] == Triple(state, label, owner) && plates[index]?.isValid == true) {
            hideFromOwner(index, owner)
            return
        }
        shown[index] = Triple(state, label, owner)
        val (loc, yaw) = spot(index)
        val plate = plates[index]?.takeIf { it.isValid } ?: Props.spawn(loc, "common/plate_${state}_stand", 0.5, yaw).also { plates[index] = it }
        plate.setItemStack(Props.item("common/plate_${state}_stand"))
        plate.brightness = if (state == "turn") Display.Brightness(15, 15) else null
        names[index]?.remove(); names[index] = null
        if (label != null) names[index] = Props.label(loc.clone().add(0.0, 0.3, 0.0), label, 0.4f)
        if (previous != null && previous != owner) Bukkit.getPlayer(previous)?.showEntity(MahjongPlayPlugin.instance, plate)
        hideFromOwner(index, owner)
    }

    private fun hideFromOwner(index: Int, owner: java.util.UUID?) {
        val viewer = owner?.let { Bukkit.getPlayer(it) } ?: return
        plates[index]?.let { viewer.hideEntity(MahjongPlayPlugin.instance, it) }
        names[index]?.let { viewer.hideEntity(MahjongPlayPlugin.instance, it) }
    }

    fun clear() = seats.indices.forEach { show(it, null, null) }
}

object PlayerFaces {

    private class Entry(val key: String, val face: Component?, val at: Long)

    private val cache = ConcurrentHashMap<UUID, Entry>()
    private val loading = ConcurrentHashMap.newKeySet<UUID>()
    private val SKIN_URL = Regex("^https?://textures\\.minecraft\\.net/texture/[0-9a-f]+$")
    private val PIXEL_FONT = Key.key("brilliant", "tabletop")
    private const val RETRY_MS = 300_000L
    private const val TIMEOUT_MS = 5_000
    private const val BOT_HEAD = 0xEB0A

    fun of(uuid: String, onLoaded: (() -> Unit)? = null): Component {
        val id = runCatching { UUID.fromString(uuid) }.getOrNull() ?: return Props.glyph(BOT_HEAD)
        val player = Bukkit.getPlayer(id) ?: return cache[id]?.face ?: Props.glyph(BOT_HEAD)
        val key = player.playerProfile.properties.firstOrNull { it.name == "textures" }?.value
            ?: return Props.glyph(Props.SLOT_SEATED)
        val entry = cache[id]?.takeIf { it.key == key }
        val settled = entry != null && (entry.face != null || System.currentTimeMillis() - entry.at < RETRY_MS)
        if (!settled && loading.add(id)) {
            CompletableFuture.runAsync {
                try {
                    val face = runCatching { render(capture(download(key))) }.getOrNull()
                    cache[id] = Entry(key, face, System.currentTimeMillis())
                    if (face != null) onLoaded?.invoke()
                } finally {
                    loading.remove(id)
                }
            }
        }
        return entry?.face ?: Props.glyph(Props.SLOT_SEATED)
    }

    fun skinUrl(textures: String): String? {
        val json = JsonParser.parseString(String(Base64.getDecoder().decode(textures), StandardCharsets.UTF_8)).asJsonObject
        val url = json.getAsJsonObject("textures")?.getAsJsonObject("SKIN")?.get("url")?.asString ?: return null
        return url.takeIf { SKIN_URL.matches(it) }
    }

    private fun download(textures: String): BufferedImage {
        val url = requireNotNull(skinUrl(textures)).replaceFirst("http://", "https://")
        val connection = URI(url).toURL().openConnection()
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        return connection.getInputStream().use { requireNotNull(ImageIO.read(it)) }
    }

    fun capture(skin: BufferedImage): IntArray {
        require(skin.width == 64 && skin.height >= 32)
        return IntArray(64) { index ->
            val x = index % 8
            val y = index / 8
            val hat = skin.getRGB(40 + x, 8 + y)
            (if (hat ushr 24 != 0) hat else skin.getRGB(8 + x, 8 + y)) and 0xFFFFFF
        }
    }

    private fun render(pixels: IntArray): Component {
        var out: Component = Component.empty()
        for (row in 0 until 8) {
            val glyph = (0xEB00 + row).toChar()
            var column = 0
            while (column < 8) {
                val rgb = pixels[row * 8 + column]
                val run = StringBuilder()
                while (column < 8 && pixels[row * 8 + column] == rgb) {
                    run.append(glyph).append('\uEB08')
                    column++
                }
                out = out.append(Component.text(run.toString()).font(PIXEL_FONT).color(TextColor.color(rgb)).shadowColor(ShadowColor.none()))
            }
            if (row < 7) out = out.append(Component.text("\uEB09").font(PIXEL_FONT))
        }
        return out
    }
}
