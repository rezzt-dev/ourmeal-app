package ourmeal.prices

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** The selected administrative boundary, not a radius that includes neighbouring cities. */
data class City(val id: Int, val name: String, val province: String?, val relationId: Long)

data class Store(val id: Int, val chainId: Int, val chainName: String, val name: String)

/** Websites come from OSM tags, not from a fixed list of supermarket brands. */
data class Chain(val id: Int, val name: String, val websites: List<String>, val stores: List<Store>)

data class CityCatalog(val city: City, val chains: List<Chain>) {
  /** Matches the Android client's /v1/stores response, with no coordinates. */
  fun toStoresResponse(): Map<String, Any?> = linkedMapOf(
    "cityId" to city.id,
    "city" to city.name,
    "province" to city.province,
    "stores" to chains.flatMap { it.stores },
    "attribution" to "© colaboradores de OpenStreetMap"
  )
}

data class ProductPrice(
  val externalId: String,
  val chainId: Int,
  val name: String,
  val price: Double,
  val unitKind: String,
  val packTotal: Double,
  val unitPrice: Double,
  val observedAt: String,
  val url: String,
  val source: String = "online",
  val brand: String? = null,
  val ean: String? = null
)

/** Empty, unsupported and failed are distinct; none is a price of zero. */
enum class ReadStatus { OK, EMPTY, NO_WEBSITE, SEARCH_UNSUPPORTED, INTERACTION_REQUIRED, UNAVAILABLE }

data class ChainRead(
  val chainId: Int,
  val chainName: String,
  val status: ReadStatus,
  val products: List<ProductPrice> = emptyList()
)

data class ReadReport(val city: String, val results: List<ChainRead>, val attribution: String = "© colaboradores de OpenStreetMap")

internal fun normalized(value: String): String = ourmeal.app.model.matchKey(value)

/** Stable across process restarts; OSM object type is part of the identity. */
internal fun stableId(key: String): Int {
  val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(StandardCharsets.UTF_8))
  return (((digest[0].toInt() and 0x7f) shl 24) or
    ((digest[1].toInt() and 0xff) shl 16) or
    ((digest[2].toInt() and 0xff) shl 8) or (digest[3].toInt() and 0xff)).coerceAtLeast(1)
}

internal fun stableKey(key: String): String = MessageDigest.getInstance("SHA-256")
  .digest(key.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
