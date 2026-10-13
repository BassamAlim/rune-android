package bassamalim.rune.core.sync

import bassamalim.rune.core.crypto.Approval
import bassamalim.rune.core.crypto.Seal
import bassamalim.rune.core.data.repositories.SitesRepository
import bassamalim.rune.core.data.repositories.SitesState
import bassamalim.rune.core.di.ApplicationScope
import bassamalim.rune.core.di.IoDispatcher
import bassamalim.rune.core.vault.Vault
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
import java.io.Closeable
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
    private val approvals: Approvals,
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

    suspend fun sync(): SyncStatus {
        var handOver: (() -> Unit)? = null
        val status = running.withLock {
            _status.value = SyncStatus.Syncing
            attempt { handOver = it }.also { _status.value = it }
        }
        // Outside the lock: answering takes as long as the user does, and may sync again.
        handOver?.invoke()
        return status
    }

    private var watching: Job? = null

    /**
     * While the app is in front, asks every [WATCH_MILLIS] whether the desktop is waiting on an
     * approval (SPEC §3.4) and syncs to take it when it is.
     */
    fun watch() {
        watching?.cancel()
        watching = scope.launch {
            while (true) {
                delay(WATCH_MILLIS)
                if (approvals.isBusy || running.isLocked) continue
                val fingerprint = vault.fingerprint() ?: continue
                val found = withContext(dispatcher) { runCatching { Wire.discover(fingerprint) }.getOrNull() }
                if (found?.asking == true) sync()
            }
        }
    }

    fun stopWatching() {
        watching?.cancel()
        watching = null
    }

    private suspend fun attempt(handOver: (() -> Unit) -> Unit): SyncStatus {
        val key = vault.syncKey() ?: return SyncStatus.NotReady
        val fingerprint = vault.fingerprint() ?: return SyncStatus.NotReady
        val mine = sitesRepository.export() ?: return SyncStatus.Failed("The site list can't be read.")

        var connection: Wire.Connection? = null
        try {
            val reply = try {
                withContext(dispatcher) {
                    val desktop = Wire.discover(fingerprint) ?: return@withContext null
                    Wire.connect(desktop.address).also { connection = it }.run {
                        send(Seal.sealMessage(key, mine))
                        receive()
                    }
                }
            } catch (e: IOException) {
                return SyncStatus.Failed("The desktop stopped answering.")
            } ?: return SyncStatus.NoDesktop

            val theirs = Seal.openMessage(key, reply)?.let { sitesRepository.parse(it).getOrNull() }
                ?: return SyncStatus.Failed("The desktop's answer couldn't be read.")

            val status = sitesRepository.merge(theirs).fold(
                onSuccess = { SyncStatus.Synced(System.currentTimeMillis()) },
                onFailure = { SyncStatus.Failed("The site list couldn't be saved.") }
            )

            // §3.4: after the lists, what the desktop is asking for, or `{}`. An older desktop
            // just closes the connection.
            val request = withContext(dispatcher) { runCatching { connection!!.receive() }.getOrNull() }
                ?.let { Approval.openFrame(key, Approval.REQUEST_AAD, it) }
                ?.let { runCatching { frameJson.decodeFromString(Frame.serializer(), it) }.getOrNull() }
            if (request != null && request.id.isNotEmpty()) {
                val taken = connection!!
                connection = null
                handOver { approvals.handle(taken, request, key) }
            }
            return status
        } finally {
            connection?.close()
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 500L
        const val WATCH_MILLIS = 2_000L
    }
}

/** The bytes on the network (SPEC §3.3), apart from [Sync] so a test can play the desktop. */
internal object Wire {
    /** UDP for finding the desktop, TCP for the exchange. The desktop listens on both. */
    const val PORT = 47825
    private const val PROBE = "rune-sync/1"
    private val ASKING = " ask".toByteArray(Charsets.UTF_8)
    private const val MAX_MESSAGE_BYTES = 4 * 1024 * 1024
    private const val DISCOVER_MILLIS = 1_000
    private const val EXCHANGE_MILLIS = 5_000

    /** Every network this phone is on. */
    fun broadcastTargets(): Set<InetAddress> = NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.interfaceAddresses.asSequence() }
        .mapNotNull { it.broadcast }
        .toSet() + InetAddress.getByName("255.255.255.255")

    /** A desktop with the same master key; [asking]: it has a request waiting (§3.4). */
    data class Found(val address: InetAddress, val asking: Boolean)

    /** Asks [targets]; the first desktop with the same master key answers. */
    fun discover(
        fingerprint: String,
        targets: Set<InetAddress> = broadcastTargets(),
        port: Int = PORT
    ): Found? = DatagramSocket().use { socket ->
        val probe = "$PROBE $fingerprint".toByteArray(Charsets.UTF_8)
        val asking = probe + ASKING
        socket.broadcast = true
        socket.soTimeout = DISCOVER_MILLIS

        for (target in targets) {
            // One network refusing the broadcast mustn't stop the others being asked.
            runCatching { socket.send(DatagramPacket(probe, probe.size, target, port)) }
        }

        val answer = DatagramPacket(ByteArray(asking.size), asking.size)
        try {
            socket.receive(answer)
            when {
                answer.data.copyOf(answer.length).contentEquals(probe) -> Found(answer.address, asking = false)
                answer.data.copyOf(answer.length).contentEquals(asking) -> Found(answer.address, asking = true)
                else -> null
            }
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    fun connect(desktop: InetAddress, port: Int = PORT): Connection {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(desktop, port), EXCHANGE_MILLIS)
            socket.soTimeout = EXCHANGE_MILLIS
        } catch (e: IOException) {
            socket.close()
            throw e
        }
        return Connection(socket)
    }

    /** Messages each way, each a length and then that many sealed bytes. */
    class Connection(private val socket: Socket) : Closeable {
        private val input = DataInputStream(socket.getInputStream())
        private val output = DataOutputStream(socket.getOutputStream())

        fun send(message: ByteArray) = output.run {
            writeInt(message.size)
            write(message)
            flush()
        }

        fun receive(): ByteArray {
            val size = input.readInt()
            if (size !in 0..MAX_MESSAGE_BYTES) throw IOException("an answer of $size bytes")
            return ByteArray(size).also(input::readFully)
        }

        /** An approval waits on its user, not the network's usual few seconds. */
        fun waitUpTo(millis: Int) {
            socket.soTimeout = millis
        }

        override fun close() = socket.close()
    }
}
