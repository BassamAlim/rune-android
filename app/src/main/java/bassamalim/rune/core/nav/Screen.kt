package bassamalim.rune.core.nav

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes (Navigation Compose 2.8+): destinations are serializable objects,
 * so arguments are real types rather than strings parsed out of a URL.
 */
sealed interface Screen {

    /** First run, and where a reset lands: master password in, keys out. */
    @Serializable data object Setup : Screen

    @Serializable data object Sites : Screen

    /** One site's password, behind the fingerprint. A site is named by its site and login. */
    @Serializable data class Site(val site: String, val login: String) : Screen

    /** Editing the entry named by [site] and [login], or adding one when [site] is null. */
    @Serializable data class Editor(val site: String? = null, val login: String? = null) : Screen

    @Serializable data object Settings : Screen
}
