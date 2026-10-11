package bassamalim.rune.features.sites

data class SitesUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    /** The rows that match [query], already in display order. */
    val rows: List<SiteRow> = emptyList(),
    /** Nothing saved at all, as opposed to nothing matching the search. */
    val isEmpty: Boolean = false,
    /** Why the site list couldn't be read. When set, nothing else on the screen is offered. */
    val unreadable: String? = null
)

data class SiteRow(val site: String, val login: String)
