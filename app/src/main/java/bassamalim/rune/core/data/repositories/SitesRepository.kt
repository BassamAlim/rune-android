package bassamalim.rune.core.data.repositories

import androidx.core.util.AtomicFile
import bassamalim.rune.core.di.ApplicationScope
import bassamalim.rune.core.di.IoDispatcher
import bassamalim.rune.core.di.SitesFile
import bassamalim.rune.core.models.MergeResult
import bassamalim.rune.core.models.Site
import bassamalim.rune.core.models.SiteKey
import bassamalim.rune.core.models.SiteList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SitesState {
    data object Loading : SitesState
    data class Loaded(val list: SiteList) : SitesState

    /**
     * The file is there but can't be read. Rune stops here rather than start empty, because the
     * next save would overwrite counters that can't be worked out again.
     */
    data class Unreadable(val reason: String) : SitesState
}

/** The only way into the site list. One JSON file, written atomically, never holding a password. */
@Singleton
class SitesRepository @Inject constructor(
    @SitesFile private val file: File,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    @ApplicationScope scope: CoroutineScope
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val writes = Mutex()

    private val _state = MutableStateFlow<SitesState>(SitesState.Loading)
    val state: StateFlow<SitesState> = _state.asStateFlow()

    init {
        scope.launch { _state.value = withContext(dispatcher) { read() } }
    }

    fun observeSites(): Flow<List<Site>> =
        state.filterIsInstance<SitesState.Loaded>().map { it.list.sites }

    fun observeSite(key: SiteKey): Flow<Site?> =
        observeSites().map { sites -> sites.firstOrNull { it.key == key } }

    /** The list once it has loaded; null when the file is unreadable. */
    suspend fun sites(): List<Site>? =
        when (val settled = state.first { it !is SitesState.Loading }) {
            is SitesState.Loaded -> settled.list.sites
            else -> null
        }

    /** Adds [site], or rewrites the entry at [replacing]. Fails with a sentence for the user. */
    suspend fun save(site: Site, replacing: SiteKey? = null): Result<Unit> =
        update { it.with(site, replacing) }

    suspend fun delete(key: SiteKey): Result<Unit> = update { it.without(key) }

    /** Remembers that [packageName] is the app for this site, so autofill offers it there. */
    suspend fun linkApp(key: SiteKey, packageName: String): Result<Unit> = update { list ->
        list.copy(
            sites = list.sites.map {
                if (it.key == key && packageName !in it.apps) it.copy(apps = it.apps + packageName)
                else it
            }
        )
    }

    fun parse(text: String): Result<SiteList> = runCatching {
        json.decodeFromString<SiteList>(text).also {
            require(it.version == SiteList.VERSION) { "unsupported version ${it.version}" }
        }
    }

    suspend fun merge(other: SiteList): Result<MergeResult> {
        var merged: MergeResult? = null

        return update { list -> list.merge(other).also { merged = it }.list }.map { merged!! }
    }

    suspend fun export(): String? =
        (state.first { it !is SitesState.Loading } as? SitesState.Loaded)
            ?.let { json.encodeToString(SiteList.serializer(), it.list) }

    /**
     * Every change is written before it is shown: if the write fails, the list in memory still
     * matches the file.
     */
    private suspend fun update(change: (SiteList) -> SiteList): Result<Unit> = writes.withLock {
        val current = state.first { it !is SitesState.Loading } as? SitesState.Loaded
            ?: return Result.failure(IllegalStateException("The site list couldn't be read."))

        runCatching {
            val changed = change(current.list)

            withContext(dispatcher) { write(changed) }
            _state.value = SitesState.Loaded(changed)
        }
    }

    private fun read(): SitesState {
        if (!file.exists()) return SitesState.Loaded(SiteList())

        return parse(runCatching { AtomicFile(file).readFully().decodeToString() }.getOrElse {
            return SitesState.Unreadable(it.message ?: "The file couldn't be opened.")
        }).fold(
            onSuccess = { SitesState.Loaded(it) },
            onFailure = { SitesState.Unreadable(it.message ?: "The file isn't a site list.") }
        )
    }

    private fun write(list: SiteList) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()

        try {
            stream.write(json.encodeToString(SiteList.serializer(), list).encodeToByteArray())
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }
}
