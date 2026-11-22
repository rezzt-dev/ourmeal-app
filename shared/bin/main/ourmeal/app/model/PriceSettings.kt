package ourmeal.app.model

import com.google.gson.annotations.SerializedName

const val PRICE_SETTINGS_VERSION = 1

data class PriceCity(
  @SerializedName("id") val id: Int,
  @SerializedName("name") val name: String,
  @SerializedName("province") val province: String?
)

data class PriceChain(
  @SerializedName("id") val id: Int,
  @SerializedName("name") val name: String
)

/** A chain present in the city, with the stores it has there. */
data class ChainStores(val chain: PriceChain, val storeIds: List<Int>)

/** Supermarkets of one city, grouped by chain. Stores of unknown chains are left out. */
data class CityStores(val city: PriceCity, val chains: List<ChainStores>, val attribution: String)

/**
 * The price comparison settings, off by default. Only the city name and the product names ever
 * reach the price service; the installation key is random and identifies no one.
 */
data class PriceSettings(
  @SerializedName("version") val version: Int = PRICE_SETTINGS_VERSION,
  @SerializedName("enabled") val enabled: Boolean = false,
  @SerializedName("installation_key") val installationKey: String = "",
  @SerializedName("city") val city: PriceCity? = null,
  @SerializedName("chains") val chains: List<PriceChain> = emptyList(),
  @SerializedName("store_ids") val storeIds: List<Int> = emptyList()
) {
  /** On, with a city and at least one supermarket to compare. */
  val isReady: Boolean get() = enabled && city != null && chains.isNotEmpty()

  val chainIds: Set<Int> get() = chains.mapTo(mutableSetOf()) { it.id }

  fun chainName(id: Int): String? = chains.firstOrNull { it.id == id }?.name

  /** Cached comparisons are only valid for the same city and supermarkets. */
  val signature: String get() = "${city?.id ?: 0}|${chainIds.sorted().joinToString(",")}|${storeIds.sorted().joinToString(",")}"

  /** Chooses [stores] as the city and keeps the chains already chosen there, or all of them. */
  fun withCity(stores: CityStores): PriceSettings {
    val sameCity = city?.id == stores.city.id
    val kept = if (sameCity) stores.chains.filter { it.chain.id in chainIds } else emptyList()
    return withChains(stores, (if (sameCity) kept else stores.chains).map { it.chain.id }.toSet())
      .copy(city = stores.city)
  }

  /** Chooses the [selected] chains among the city's [stores], with their store ids. */
  fun withChains(stores: CityStores, selected: Set<Int>): PriceSettings {
    val chosen = stores.chains.filter { it.chain.id in selected }
    return copy(
      chains = chosen.map { it.chain },
      storeIds = chosen.flatMap { it.storeIds }.distinct().sorted()
    )
  }

  /** Gson skips the Kotlin constructor, so a missing or null field arrives as null here. */
  fun sanitized(): PriceSettings {
    val storedKey: String? = installationKey
    val storedChains: List<PriceChain?>? = chains
    val storedStores: List<Int?>? = storeIds
    val validCity = city?.sanitized()
    return PriceSettings(
      version = if (version > 0) version else PRICE_SETTINGS_VERSION,
      enabled = enabled,
      installationKey = storedKey?.trim().orEmpty(),
      city = validCity,
      chains = if (validCity == null) emptyList() else
        storedChains.orEmpty().mapNotNull { it?.sanitized() }.distinctBy { it.id },
      storeIds = if (validCity == null) emptyList() else
        storedStores.orEmpty().filterNotNull().filter { it > 0 }.distinct()
    )
  }

  private fun PriceCity.sanitized(): PriceCity? {
    val storedName: String? = name
    val label = storedName?.let(::cleanServiceText).orEmpty()
    if (id <= 0 || label.isEmpty()) return null
    return PriceCity(id, label, province?.let(::cleanServiceText)?.ifEmpty { null })
  }

  private fun PriceChain.sanitized(): PriceChain? {
    val storedName: String? = name
    val label = storedName?.let(::cleanServiceText).orEmpty()
    return if (id > 0 && label.isNotEmpty()) PriceChain(id, label) else null
  }
}
