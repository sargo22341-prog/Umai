package org.opensources.umai.provider

/** The recipe providers the app knows; see [RecipeProvider]. */
class ProviderRegistry(val providers: List<RecipeProvider>) {

    fun byId(id: String): RecipeProvider? = providers.firstOrNull { it.id == id }

    /** The provider of the page at [url], `null` when no provider handles it. */
    fun forUrl(url: String?): RecipeProvider? =
        url?.takeIf { it.isNotBlank() }?.let { address -> providers.firstOrNull { it.handles(address) } }
}
