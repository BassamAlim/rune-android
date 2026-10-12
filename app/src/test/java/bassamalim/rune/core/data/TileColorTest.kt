package bassamalim.rune.core.data

import bassamalim.rune.core.data.repositories.SiteIcons.Companion.tileColor
import org.junit.Assert.assertEquals
import org.junit.Test

class TileColorTest {

    private val clear = 0x00000000
    private val light = 0xFFF6F1E9.toInt()

    /** A 4×4 logo: [edge] all round, [middle] in the 2×2 centre. */
    private fun logo(edge: Int, middle: Int) = IntArray(16) { i ->
        val x = i % 4
        val y = i / 4
        if (x in 1..2 && y in 1..2) middle else edge
    }

    @Test fun solidBackgroundIsKept() =
        assertEquals(0xFFFFFFFF.toInt(), tileColor(logo(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 4, 4))

    @Test fun darkTransparentLogoGetsLightTile() =
        assertEquals(light, tileColor(logo(clear, 0xFF181717.toInt()), 4, 4))

    @Test fun brightTransparentLogoGetsWashOfItsColour() =
        assertEquals(0x2933CC66, tileColor(logo(clear, 0xFF33CC66.toInt()), 4, 4))

    @Test fun emptyLogoGetsLightTile() = assertEquals(light, tileColor(IntArray(16), 4, 4))
}
