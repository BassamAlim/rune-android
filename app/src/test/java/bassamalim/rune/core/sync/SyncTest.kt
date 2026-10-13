package bassamalim.rune.core.sync

import bassamalim.rune.core.crypto.Seal
import bassamalim.rune.core.models.Site
import bassamalim.rune.core.models.SiteList
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class SyncTest {

    private val key = Seal.syncKey(ByteArray(32) { 7 })
    private val loopback = InetAddress.getLoopbackAddress()

    /** The message the desktop's `sync_messages_open_only_with_the_same_master` test seals. */
    @OptIn(ExperimentalStdlibApi::class)
    @Test fun opensTheDesktopsMessage() {
        val message = (
            "09090909090909090909090932c1ac9a31895ff6306e6f7139af6044bbaa5e2cd5af2f8be8722941db67734b" +
                "c975fc8e3619d2fb92cd3ff5a4c822cd5e61945fc01acfc418438359b6d0a1381a3d6f376ef1a852c31477de" +
                "98766e8aade92939144510ff34acca16cb79a7f6c37bff0d819279badb8e64b6f4d221b33f864606b5381b08" +
                "7f1baec866e03cc83fc6d59caa6d89de6db37d88967e9e2fd719d1e02c5ed656cf2247636d4f77c55f738f73"
            ).hexToByteArray()

        val list = Json.decodeFromString<SiteList>(Seal.openMessage(key, message)!!)
        assertEquals(listOf(Site("a", length = 20, modified = 10)), list.sites)

        assertNull(Seal.openMessage(Seal.syncKey(ByteArray(32) { 8 }), message))
        assertEquals("{}", Seal.openMessage(key, Seal.sealMessage(key, "{}")))
    }

    @Test fun findsTheDesktopWithTheSameFingerprint() {
        DatagramSocket(0, loopback).use { desktop ->
            thread {
                // A desktop answers only its own fingerprint, by sending the probe back.
                repeat(2) {
                    val packet = DatagramPacket(ByteArray(64), 64)
                    desktop.receive(packet)
                    if (String(packet.data, 0, packet.length) == "rune-sync/1 ef15") desktop.send(packet)
                }
            }

            assertEquals(loopback, Wire.discover("ef15", setOf(loopback), desktop.localPort))
            assertNull(Wire.discover("0000", setOf(loopback), desktop.localPort))
        }
    }

    @Test fun exchangesOneLengthPrefixedMessageEachWay() {
        ServerSocket(0, 1, loopback).use { desktop ->
            var received = ByteArray(0)
            thread {
                desktop.accept().use { phone ->
                    val input = DataInputStream(phone.getInputStream())
                    received = ByteArray(input.readInt()).also(input::readFully)
                    DataOutputStream(phone.getOutputStream()).run { writeInt(3); write(byteArrayOf(4, 5, 6)) }
                }
            }

            val answer = Wire.exchange(loopback, byteArrayOf(1, 2), desktop.localPort)
            assertArrayEquals(byteArrayOf(4, 5, 6), answer)
            assertArrayEquals(byteArrayOf(1, 2), received)
        }
    }
}
