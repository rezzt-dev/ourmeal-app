package ourmeal.app.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/*
 * Wire format of the price service (`/v1`). Every response field is nullable because the data
 * comes from the network: it is validated here and turned into the app's own models, dropping
 * whatever is incomplete rather than showing a doubtful price.
 */

private const val MAX_PACK_PRICE = 10_000.0

data class CityDto(val id: Int?, val name: String?, val province: String?)

data class StoreDto(val id: Int?, val chainId: Int?, val chainName: String?, val name: String?)

data class CityStoresResponse(
  val cityId: Int?,
  val city: String?,
  val province: String?,
  val stores: List<StoreDto?>?,
  val attribution: String?
)

data class ResolveRequest(
  val query: String,
  val mode: ItemMode,
  val cityId: Int? = null,
  val chainIds: List<Int> = emptyList()
)

data class CandidateDto(
  val groupId: Int?,
  val canonicalId: Int?,
  val name: String?,
  val brand: String?,
  val score: Double?
)

data class ResolveResponse(val status: String?, val candidates: List<CandidateDto?>?)

data class AliasRequest(val query: String, val groupId: Int?, val canonicalId: Int?)

data class CompareItemDto(
  val id: String,
  val mode: ItemMode,
  val groupId: Int?,
  val canonicalId: Int?,
  val quantity: Int = 1
)

data class CompareRequest(val chainIds: List<Int>, val storeIds: List<Int>, val items: List<CompareItemDto>, val cityId: Int? = null)

data class OfferDto(
  val chainId: Int?,
  val name: String?,
  val price: Double?,
  val unitPrice: Double?,
  val unitKind: String?,
  val packTotal: Double?,
  val onOffer: Boolean?,
  val observedAt: String?,
  val source: String?
)

data class ItemComparisonDto(val itemId: String?, val offers: List<OfferDto?>?)

data class CompareResponse(val items: List<ItemComparisonDto?>?, val pending: Boolean? = false)

/** A possible meaning of what the user wrote, offered when the service is not sure. */
data class PriceCandidate(val target: PriceTarget, val brand: String?)

/** How the service understood a product name. */
sealed interface Resolution {
  /** A bounded background ingestion is still collecting the selected city's prices. */
  data object Pending : Resolution
  data class Resolved(val target: PriceTarget) : Resolution
  /** Probably one of these: the user picks. */
  data class Choose(val options: List<PriceCandidate>) : Resolution
  /** Not understood; the options, when any, are loose suggestions. */
  data class Refine(val options: List<PriceCandidate>) : Resolution
}

fun CityDto.toCity(): PriceCity? {
  val validId = id?.takeIf { it > 0 } ?: return null
  val label = name?.let(::cleanServiceText).orEmpty().ifEmpty { return null }
  return PriceCity(validId, label, province?.let(::cleanServiceText)?.ifEmpty { null })
}

fun List<CityDto?>.toCities(): List<PriceCity> = mapNotNull { it?.toCity() }.distinctBy { it.id }.take(20)

/** Null when the response does not name a city. A city without known chains is still valid. */
fun CityStoresResponse.toCityStores(): CityStores? {
  val city = CityDto(cityId, city, province).toCity() ?: return null
  val chains = stores.orEmpty().filterNotNull()
    .mapNotNull { store ->
      val chainId = store.chainId?.takeIf { it > 0 } ?: return@mapNotNull null
      val storeId = store.id?.takeIf { it > 0 } ?: return@mapNotNull null
      val chainName = store.chainName?.let(::cleanServiceText).orEmpty().ifEmpty { return@mapNotNull null }
      Triple(chainId, chainName, storeId)
    }
    .groupBy { it.first }
    .map { (chainId, stores) -> ChainStores(PriceChain(chainId, stores.first().second), stores.map { it.third }.distinct()) }
    .sortedWith(compareByDescending<ChainStores> { it.storeIds.size }.thenBy { matchKey(it.chain.name) })
  val credit = attribution?.let(::cleanServiceText).orEmpty().ifEmpty { OSM_ATTRIBUTION }
  return CityStores(city, chains, credit)
}

const val OSM_ATTRIBUTION = "© colaboradores de OpenStreetMap"

/** Null for an unknown status: the app then treats the product as not compared. */
fun ResolveResponse.toResolution(): Resolution? {
  val options = candidates.orEmpty().filterNotNull()
    .mapNotNull { candidate ->
      PriceTarget.of(candidate.groupId, candidate.canonicalId, candidate.name)
        ?.let { PriceCandidate(it, candidate.brand?.let(::cleanServiceText)?.ifEmpty { null }) }
    }
    .distinctBy { it.target.key }
  return when (status?.trim()?.uppercase()) {
    "PROCESSING" -> Resolution.Pending
    "RESOLVED" -> options.firstOrNull()?.let { Resolution.Resolved(it.target) } ?: Resolution.Refine(emptyList())
    "CHOOSE" -> if (options.isEmpty()) Resolution.Refine(emptyList()) else Resolution.Choose(options.take(3))
    "REFINE" -> Resolution.Refine(options.take(5))
    else -> null
  }
}

/** Offers per requested item id. Items missing from the response have no price anywhere. */
fun CompareResponse.toOffers(requested: Set<String>): Map<String, List<PriceOffer>> {
  val received = items.orEmpty().filterNotNull()
    .filter { it.itemId in requested }
    .associate { item -> item.itemId!! to item.offers.orEmpty().mapNotNull { it?.toOffer() } }
  return requested.associateWith { received[it].orEmpty() }
}

/**
 * Null unless the offer has a chain, a real pack price, a unit kind, a date and a source.
 * The price per unit is recomputed from the pack when the pack size is known.
 */
fun OfferDto.toOffer(): PriceOffer? {
  val chain = chainId?.takeIf { it > 0 } ?: return null
  val packPrice = price?.takeIf { it.isFinite() && it > 0.0 && it < MAX_PACK_PRICE } ?: return null
  val kind = UnitKind.fromWire(unitKind) ?: return null
  val origin = PriceSource.fromWire(source) ?: return null
  val observed = observedAt?.let(::parseInstant) ?: return null
  val pack = packTotal?.takeIf { it.isFinite() && it > 0.0 }
  val perUnit = pack?.let { unitPriceOf(packPrice, kind, it) }
    ?: unitPrice?.takeIf { it.isFinite() && it > 0.0 }
    ?: return null
  return PriceOffer(
    chainId = chain,
    productName = name?.let(::cleanServiceText).orEmpty(),
    price = packPrice,
    unitPrice = perUnit,
    unitKind = kind,
    packTotal = pack,
    onOffer = onOffer == true,
    observedAt = observed,
    source = origin
  )
}

private fun parseInstant(value: String): Long? = try {
  OffsetDateTime.parse(value.trim()).toInstant().toEpochMilli()
} catch (_: DateTimeParseException) {
  try { Instant.parse(value.trim()).toEpochMilli() } catch (_: DateTimeParseException) { null }
}
