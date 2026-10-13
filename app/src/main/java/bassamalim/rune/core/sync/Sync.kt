package bassamalim.rune.core.sync

import bassamalim.rune.core.crypto.Seal
import bassamalim.rune.core.data.repositories.SitesRepository
import bassamalim.rune.core.data.repositories.SitesState
import bassamalim.rune.core.di.ApplicationScope
import bassamalim.rune.core.di.IoDispatcher
import bassamalim.rune.core.vault.Vault
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val atMillis: Long) : SyncStatus

    /** No desktop with this master password answered on this network. */
    data object NoDesktop : SyncStatus

    /** The sync key isn't on this phone yet: it arrives with the next unlock. */
    data object NotReady : SyncStatus
    data class Failed(val reason: String) : SyncStatus
}

/**
 * Keeps the site list the same as the desktop's over the local network (SPEC §3.3). The phone
 * asks, the desktop answers: a broadcast finds it, then the two exchange lists, sealed with the
 * key both derive from the master password, and each merges the other's in. It runs when the
 * app comes forward and after every change; a desktop that isn't there is simply not found.
 */
@Singleton
class Sync @Inject constructor(
    private val vault: Vault,
    private val sitesRepository: SitesRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val running = Mutex()

    init {
        // At start and after each change. A sync that brings changes in runs once more and
        // finds nothing left to exchange, so this settles.
        scope.launch {
            sitesRepository.state.filterIsInstance<SitesState.Loaded>().collectLatest {
                delay(SETTLE_MILLIS)
                sync()
            }
        }
    }

    /** The app came forward: the desktop may have changed since. */
    fun request() {
        scope.launch { sync() }
    }

    suspend fun sync(): SyncStatus = running.withLock {
        _status.value = SyncStatus.Syncing
        attempt().also { _status.value = it }
    }

    private suspend fun attempt(): SyncStatus {
        val key = vault.syncKey() ?: return SyncStatus.NotReady
        val fingerprint = vault.fingerprint() ?: return SyncStatus.NotReady
        val mine = sitesRepository.export() ?: return SyncStatus.Failed("The site list can't be read.")

        val reply = try {
            withContext(dispatcher) {
                val desktop = Wire.discover(fingerprint) ?: return@withContext null
                Wire.exchange(desktop, Seal.sealMessage(key, mine))
            }
        } catch (e: IOException) {
            return SyncStatus.Failed("The desktop stopped answering.")
        } ?: return SyncStatus.NoDesktop

        val theirs = Seal.openMessage(key, reply)?.let { sitesRepository.parse(it).getOrNull() }
            ?: return SyncStatus.Failed("The desktop's answer couldn't be read.")

        return sitesRepository.merge(theirs).fold(
            onSuccess = { SyncStatus.Synced(System.currentTimeMillis()) },
            onFailure = { SyncStatus.Failed("The site list couldn't be saved.") }
        )
    }

    private companion object {
        const val SETTLE_MILLIS = 500L
    }
}

/** The bytes on the network (SPEC §3.3), apart from [Sync] so a test can play the desktop. */
internal object Wire {
    /** UDP for finding the desktop, TCP for the exchange. The desktop listens on both. */
    const val PORT = 47825
    private const val PROBE = "rune-sync/1"
    private const val MAX_MESSAGE_BYTES = 4 * 1024 * 1024
    private const val DISCOVER_MILLIS = 1_000
    private const val EXCHANGE_MILLIS = 5_000

    /** Every network this phone is on. */
    fun broadcastTargets(): Set<InetAddress> = NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.interfaceAddresses.asSequence() }
        .mapNotNull { it.broadcast }
        .toSet() + InetAddress.getByName("255.255.255.255")

    /** Asks [targets]; the first desktop with the same master key answers. */
    fun discover(
        fingerprint: String,
        targets: Set<InetAddress> = broadcastTargets(),
        port: Int = PORT
    ): InetAddress? = DatagramSocket().use { socket ->
        val probe = "$PROBE $fingerprint".toByteArray(Charsets.UTF_8)
        socket.broadcast = true
        socket.soTimeout = DISCOVER_MILLIS

        for (target in targets) {
            // One network refusing the broadcast mustn't stop the others being asked.
            runCatching { socket.send(DatagramPacket(probe, probe.size, target, port)) }
        }

        val answer = DatagramPacket(ByteArray(probe.size), probe.size)
        try {
            socket.receive(answer)
            answer.address.takeIf { answer.data.copyOf(answer.length).contentEquals(probe) }
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    /** One message each way, each a length and then that many sealed bytes. */
    fun exchange(desktop: InetAddress, message: ByteArray, port: Int = PORT): ByteArray =
        Socket().use { socket ->
            socket.connect(InetSocketAddress(desktop, port), EXCHANGE_MILLIS)
            socket.soTimeout = EXCHANGE_MILLIS

            DataOutputStream(socket.getOutputStream()).run {
                writeInt(message.size)
                write(message)
                flush()
            }
            DataInputStream(socket.getInputStream()).run {
                val size = readInt()
                if (size !in 0..MAX_MESSAGE_BYTES) throw IOException("an answer of $size bytes")
                ByteArray(size).also(::readFully)
            }
        }
}
