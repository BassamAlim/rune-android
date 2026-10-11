package bassamalim.rune.core.di

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "vault")

/**
 * Everything Rune keeps lives on the device: the wrapped keys in a DataStore and the site list
 * in one JSON file. No passwords are in either.
 *
 * Repositories aren't listed here: they're `@Singleton class ... @Inject constructor`, so Hilt
 * builds them without a module.
 */
@Module @InstallIn(SingletonComponent::class)
object DataSourceModule {

    @Provides @Singleton
    fun provideVaultDataStore(application: Application): DataStore<Preferences> =
        application.vaultDataStore

    /** The same file format the desktop app reads and writes (SPEC.md §3). */
    @Provides @Singleton @SitesFile
    fun provideSitesFile(application: Application): File = File(application.filesDir, "sites.json")

}

@Qualifier @Retention(AnnotationRetention.BINARY)
annotation class SitesFile
