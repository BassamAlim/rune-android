package bassamalim.rune.core.crypto

import bassamalim.rune.core.sync.Frame
import bassamalim.rune.core.sync.Revealed
import bassamalim.rune.core.sync.frameJson
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** SPEC §3.4: the values the desktop's `approval_vector` asserts. */
@OptIn(ExperimentalStdlibApi::class)
class ApprovalTest {

    private val desktop = ByteArray(32) { 1 }
    private val phone = ByteArray(32) { 2 }
    private val desktopPub = Approval.publicKey(desktop)
    private val phonePub = Approval.publicKey(phone)

    @Test fun matchesTheDesktop() {
        val key = Approval.answerKey(desktop, phonePub, desktopPub, phonePub)!!
        assertArrayEquals("both sides agree", key, Approval.answerKey(phone, desktopPub, desktopPub, phonePub))

        assertEquals("a4e09292b651c278b9772c569f5fa9bb13d906b46ab68c9df9dc2b4409f8a209", desktopPub.toHexString())
        assertEquals("ce8d3ad1ccb633ec7b70c17814a5c76ecd029685050d344745ba05870e587d59", phonePub.toHexString())
        assertEquals("96fd0426e04d80775bd47312d4508dda42e8d2f44d38b55e2f27c08f41016cd2", Approval.commit(desktopPub).toHexString())
        assertEquals("437923", Approval.code(desktopPub, phonePub))
        assertEquals("b0b00aa39da3bb7e44274a34529acae4ad457effe3404d6030e4d5e10fa8f7fa", key.toHexString())

        assertNull("a low-order key is refused", Approval.answerKey(desktop, ByteArray(32), desktopPub, phonePub))
    }

    /** A request the desktop sealed, and the answer box it expects back, byte for byte. */
    @Test fun opensTheDesktopsRequestAndSealsTheSameBox() {
        val syncKey = Seal.syncKey(ByteArray(32) { 7 })
        val sealed = ("040404040404040404040404d589631975cf50fdef031efa339185e19bb9dcb8089757d17e0e4a3b5c8df9a6" +
            "2fa4ec1ca0f46299cf1397ed9d5300ce29b621cd794f9e3783461e81594fda122756b51a21b71f5846820ff7c64f1c9a4c5" +
            "e65db49ec72c4837899b922817c93592f153390220f0b7c773ec7a4d1f0d5829d24386d5e62b49d25469dd9867f0bf7c049" +
            "1813789c336e1cc694cc62b5b17463fd49").hexToByteArray()

        val request = frameJson.decodeFromString(
            Frame.serializer(),
            Approval.openFrame(syncKey, Approval.REQUEST_AAD, sealed)!!
        )
        assertEquals(
            Frame(id = "ab", kind = "show", site = "github.com", login = "", commit = Approval.commit(desktopPub).toHexString()),
            request
        )
        assertNull("a request can't pass as an answer", Approval.openFrame(syncKey, Approval.ANSWER_AAD, sealed))

        val key = Approval.answerKey(phone, desktopPub, desktopPub, phonePub)!!
        val revealed = frameJson.encodeToString(Revealed.serializer(), Revealed("pw", kept = "old"))
        val box = Approval.seal(key, revealed, ByteArray(12) { 3 })
        assertEquals(
            "030303030303030303030303260ef21ae3530030473a4b88650ce2721eb11da272f4fcdede823fc98cdf2dfb41c1ef0a9cd497a7f24af9e892d3",
            box
        )
        assertEquals(revealed, Approval.open(key, box))
        assertNull(Approval.open(ByteArray(32), box))
    }
}
