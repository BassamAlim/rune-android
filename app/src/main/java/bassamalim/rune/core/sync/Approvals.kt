package bassamalim.rune.core.sync

import bassamalim.rune.core.crypto.Approval
import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.crypto.Seal
import bassamalim.rune.core.data.repositories.SitesRepository
import bassamalim.rune.core.di.ApplicationScope
import bassamalim.rune.core.di.IoDispatcher
import bassamalim.rune.core.models.Site
import bassamalim.rune.core.models.SiteKey
import bassamalim.rune.core.models.passwords
import bassamalim.rune.core.vault.UnlockStep
import bassamalim.rune.core.vault.Unlocker
import dagger.Lazy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/** SPEC §3.4: every frame of an approval, either way. Fields a frame doesn't use are left out. */
@Serializable
data class Frame(
    val id: String = "",
    /** "show", "keep" or "import". */
    val kind: String = "",
    val site: String? = null,
    val login: String? = null,
    val count: Int? = null,
    val commit: String = "",
    val pub: String = "",
    val box: String = "",
    val declined: Boolean = false,
    val missing: Boolean = false,
    val ready: Boolean = false,
    val added: Int? = null,
    val already: Int? = null
)

/** `show`: what this phone reveals for one entry. */
@Serializable
data class Revealed(val password: String, val previous: String? = null, val kept: String? = null)

/** `keep` / `import`: a site and the password to seal for it. */
@Serializable
data class ToKeep(val site: Site, val password: String)

/** Leaves out what a frame doesn't use, as the desktop does. */
internal val frameJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}

sealed interface ApprovalState {
    data object Idle : ApprovalState

    /** The desktop asks for [what]; [code] is on both screens. */
    data class Asking(val what: String, val code: String, val isWorking: Boolean = false) : ApprovalState
}

/**
 * Answers what the desktop asks for (SPEC §3.4): its site card's password, or passwords to seal.
 * The desktop never holds the master key, so it asks here, and this phone shows what is asked
 * and the code, then unlocks with its own [Unlocker]. One request at a time; the connection is
 * the one the sync just used.
 */
@OptIn(ExperimentalStdlibApi::class)
@Singleton
class Approvals @Inject constructor(
    private val sitesRepository: SitesRepository,
    private val unlocker: Unlocker,
    private val sync: Lazy<Sync>,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val _state = MutableStateFlow<ApprovalState>(ApprovalState.Idle)
    val state: StateFlow<ApprovalState> = _state.asStateFlow()
    val unlock: StateFlow<UnlockStep> = unlocker.step

    private val busy = Mutex()
    private var decision: CompletableDeferred<ByteArray?>? = null

    val isBusy: Boolean get() = busy.isLocked

    /** Takes over [connection] from a sync that ended with [request]. */
    internal fun handle(connection: Wire.Connection, request: Frame, syncKey: ByteArray) {
        scope.launch {
            if (!busy.tryLock()) return@launch connection.close()
            try {
                answer(connection, request, syncKey)
            } catch (_: IOException) {
                // The desktop went away or sent something it shouldn't: it shows that it failed.
            } catch (_: IllegalArgumentException) {
            } finally {
                connection.close()
                decision?.cancel()
                decision = null
                unlocker.cancel()
                _state.value = ApprovalState.Idle
                busy.unlock()
            }
        }
    }

    fun onApprove() {
        if (unlocker.step.value == UnlockStep.Idle) scope.launch { unlocker.begin() }
    }

    fun onBiometricSucceeded(cipher: Cipher) = scope.launch { unlocker.onBiometricSucceeded(cipher)?.let(::decide) }

    fun onUsePassword() = unlocker.onUsePassword()

    fun onPassword(password: String) = scope.launch { unlocker.onPassword(password)?.let(::decide) }

    fun onSkipPractice() = scope.launch { unlocker.skipPractice() }

    fun onUnlockCancelled() = unlocker.cancel()

    fun onDecline() = decide(null)

    private fun decide(masterKey: ByteArray?) {
        if (decision?.complete(masterKey) != true) masterKey?.fill(0)
    }

    private suspend fun answer(connection: Wire.Connection, request: Frame, syncKey: ByteArray) {
        val id = request.id
        val what = when (request.kind) {
            "show" -> "The password for " + listOfNotNull(request.site, request.login?.ifEmpty { null }).joinToString(" · ")
            "keep" -> "To keep the old password you typed for a site"
            "import" -> "To keep ${request.count ?: 0} passwords imported from Bitwarden"
            else -> return
        }

        fun send(frame: Frame) =
            connection.send(Approval.sealFrame(syncKey, Approval.ANSWER_AAD, frameJson.encodeToString(Frame.serializer(), frame)))

        fun receive(): Frame {
            val text = Approval.openFrame(syncKey, Approval.REQUEST_AAD, connection.receive())
                ?: throw IOException("not from a desktop with this master password")
            return frameJson.decodeFromString(Frame.serializer(), text).also {
                if (it.id != id) throw IOException("another request")
            }
        }

        val until = System.currentTimeMillis() + Approval.TIMEOUT_MILLIS
        fun left() = (until - System.currentTimeMillis()).coerceAtLeast(1)

        val private = Approval.newPrivateKey()
        var answerKey: ByteArray? = null
        try {
            // Steps 3–4: our key first, then the desktop reveals the one it committed to.
            val phonePub = Approval.publicKey(private)
            val code = withContext(dispatcher) {
                send(Frame(id = id, pub = phonePub.toHexString()))
                val desktopPub = receive().pub.hexToByteArray()
                require(desktopPub.size == 32 && Approval.commit(desktopPub).contentEquals(request.commit.hexToByteArray())) {
                    "the desktop's key isn't the one it committed to"
                }
                answerKey = Approval.answerKey(private, desktopPub, desktopPub, phonePub)
                    ?: throw IOException("the desktop's key is unsafe")
                Approval.code(desktopPub, phonePub)
            }

            // Step 5: the user compares the codes and unlocks, or declines.
            val pending = CompletableDeferred<ByteArray?>()
            decision = pending
            _state.value = ApprovalState.Asking(what, code)
            val masterKey = withTimeoutOrNull(left()) { pending.await() }
            unlocker.cancel()
            _state.value = ApprovalState.Asking(what, code, isWorking = true)

            withContext(dispatcher) {
                connection.waitUpTo(left().toInt())
                if (masterKey == null) return@withContext send(Frame(id = id, declined = true))
                try {
                    if (request.kind == "show") show(request, id, masterKey, answerKey!!, ::send)
                    else keep(request.kind, id, masterKey, answerKey!!, ::send, ::receive)
                } finally {
                    masterKey.fill(0)
                }
            }
        } finally {
            private.fill(0)
            answerKey?.fill(0)
        }
    }

    /** Step 6, `show`: the settings are this phone's own, never the request's. */
    private suspend fun show(request: Frame, id: String, masterKey: ByteArray, answerKey: ByteArray, send: (Frame) -> Unit) {
        val key = SiteKey(Rune.normalize(request.site.orEmpty()), Rune.normalize(request.login.orEmpty()))
        val site = sitesRepository.sites()?.firstOrNull { it.key == key }
            ?: return send(Frame(id = id, missing = true))

        val passwords = site.passwords(masterKey.copyOf()).getOrElse { throw IOException(it.message) }
        val revealed = Revealed(
            password = passwords.rune ?: passwords.current,
            previous = passwords.previous,
            kept = passwords.current.takeIf { passwords.rune != null }
        )
        send(Frame(id = id, box = Approval.seal(answerKey, frameJson.encodeToString(Revealed.serializer(), revealed))))
    }

    /**
     * Step 6, `keep` / `import`: ready, then the desktop sends the passwords once its user has
     * confirmed the code there. `keep` sets an entry's kept password; `import` adds only sites
     * that aren't here yet.
     */
    private suspend fun keep(
        kind: String,
        id: String,
        masterKey: ByteArray,
        answerKey: ByteArray,
        send: (Frame) -> Unit,
        receive: () -> Frame
    ) {
        send(Frame(id = id, ready = true))
        val text = Approval.open(answerKey, receive().box) ?: throw IOException("the passwords don't open")
        val items = frameJson.decodeFromString<List<ToKeep>>(text)

        val sealKey = Seal.key(masterKey)
        var added = 0
        var already = 0
        try {
            for (item in items) {
                val key = SiteKey(Rune.normalize(item.site.site), Rune.normalize(item.site.login))
                val existing = sitesRepository.sites()?.firstOrNull { it.key == key }
                val sealed = Seal.seal(sealKey, item.password)
                val saved = when {
                    existing != null && kind == "keep" -> sitesRepository.save(existing.copy(sealed = sealed), replacing = key)
                    existing != null -> null
                    else -> sitesRepository.save(item.site.copy(sealed = sealed))
                }
                if (saved?.isSuccess == true) added++ else already++
            }
        } finally {
            sealKey.fill(0)
        }
        send(Frame(id = id, added = added, already = already))
        // Brings what was sealed back to the desktop.
        sync.get().request()
    }
}
