package com.mahjongplay.display

import java.awt.image.BufferedImage
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerFacesTest {

    private fun textures(url: String): String =
        Base64.getEncoder().encodeToString("""{"textures":{"SKIN":{"url":"$url"}}}""".toByteArray())

    @Test
    fun hatLayerCoversFaceOnlyWhereOpaque() {
        val skin = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        skin.setRGB(8, 8, 0xFFAA0000.toInt())
        skin.setRGB(9, 8, 0xFF00AA00.toInt())
        skin.setRGB(40, 8, 0xFF0000AA.toInt())
        val face = PlayerFaces.capture(skin)
        assertEquals(0x0000AA, face[0])
        assertEquals(0x00AA00, face[1])
    }

    @Test
    fun onlyMojangSkinHostIsAccepted() {
        val good = "http://textures.minecraft.net/texture/abc123"
        assertEquals(good, PlayerFaces.skinUrl(textures(good)))
        assertNull(PlayerFaces.skinUrl(textures("http://example.com/texture/abc123")))
        assertNull(PlayerFaces.skinUrl(textures("http://textures.minecraft.net.evil.com/texture/abc123")))
    }
}
