package bassamalim.rune.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SealTest {

    private val key = Seal.key(ByteArray(32) { 7 })

    /** The same vector the desktop's `seal_vector` test asserts: each app opens the other's. */
    @Test fun matchesTheDesktop() {
        val sealed = Seal.seal(key, "Hunter2 – ünïcode", ByteArray(12) { (it + 1).toByte() })
        assertEquals(
            "0102030405060708090a0b0c21bcf4402847d8fc368013cfceee4958760fa13ce5252a5544c5d679c31f12209598356c53",
            sealed
        )
        assertEquals("Hunter2 – ünïcode", Seal.open(key, sealed))
    }

    @Test fun refusesWrongKeysAndEditedFields() {
        val sealed = Seal.seal(key, "Hunter2!")
        assertThrows(IllegalArgumentException::class.java) { Seal.open(Seal.key(ByteArray(32) { 8 }), sealed) }
        val edited = sealed.replaceRange(30, 32, if (sealed.substring(30, 32) == "00") "01" else "00")
        assertThrows(IllegalArgumentException::class.java) { Seal.open(key, edited) }
        assertThrows(IllegalArgumentException::class.java) { Seal.open(key, "zz") }
    }

    @Test fun everySealIsFresh() = assert(Seal.seal(key, "same") != Seal.seal(key, "same"))
}
