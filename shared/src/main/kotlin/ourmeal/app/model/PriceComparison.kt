package ourmeal.app.model

import com.google.gson.annotations.SerializedName

/** A price older than this is shown with its date and only wins when nothing newer exists. */
const val PRICE_FRESH_MS = 7L * 24 * 60 * 60 * 1000

/** What a pack is measured in. Prices are only ever compared within one kind. */
enum class UnitKind(val wire: String) {
  @SerializedName("mass") MASS("mass"),
  @SerializedName("volume") VOLUME("volume"),
  @SerializedName("count") COUNT("count");

  companion object {
    fun fromWire(value: String?): UnitKind? = entries.firstOrNull { it.wire == value?.trim()?.lowercase() }
  }
}

/** Where a price comes from; every price carries one. */
enum class PriceSource(val wire: String) {
  @SerializedName("online") ONLINE("online"),
  @SerializedName("open_prices") OPEN_PRICES("open_prices"),
  @SerializedName("user") USER("user");

  companion object {
    fun fromWire(value: String?): PriceSource? = entries.firstOrNull { it.wire == value?.trim()?.lowercase() }
  }
}

/** Any brand of the product, or the concrete product the user chose. */
enum class ItemMode { GENERIC, BRAND }

/** What a list product was resolved to on the price service: a generic group or a concrete product. */
data class PriceTarget(
  @SerializedName("group_id") val groupId: Int?,
  @SerializedName("canonical_id") val canonicalId: Int?,
  @SerializedName("name") val name: String
) {
  val mode: ItemMode get() = if (canonicalId != null) ItemMode.BRAND else ItemMode.GENERIC

  /** One id per group or product, shared by requests and the cache. */
  val key: String get() = canonicalId?.let { "c:$it" } ?: "g:$groupId"

  companion object {
    /** Null unless at least one id is valid and the name is usable. */
    fun of(groupId: Int?, canonicalId: Int?, name: String?): PriceTarget? {
      val group = groupId?.takeIf { it > 0 }
      val canonical = canonicalId?.takeIf { it > 0 }
      val label = name?.let(::cleanServiceText).orEmpty()
      if ((group == null && canonical == null) || label.isEmpty()) return null
      return PriceTarget(group, canonical, label)
    }
  }
}

/** One chain's price for a target, with the pack price and the comparable price per kilo, litre or unit. */
data class PriceOffer(
  @SerializedName("chain_id") val chainId: Int,
  @SerializedName("name") val productName: String,
  @SerializedName("price") val price: Double,
  @SerializedName("unit_price") val unitPrice: Double,
  @SerializedName("unit_kind") val unitKind: UnitKind,
  @SerializedName("pack_total") val packTotal: Double?,
  @SerializedName("on_offer") val onOffer: Boolean,
  @SerializedName("observed_at") val observedAt: Long,
  @SerializedName("source") val source: PriceSource
) {
  fun isFresh(now: Long): Boolean = now >= observedAt && now - observedAt <= PRICE_FRESH_MS
}

/** Offers of the chosen supermarkets, cheapest per unit first, and the one to recommend. */
data class PriceComparison(val offers: List<PriceOffer>, val best: PriceOffer?) {
  /** How much cheaper per unit the best offer is than the next chain, from 0 to 1. Null without a rival. */
  val savingOverNext: Double?
    get() {
      val winner = best ?: return null
      val next = offers.firstOrNull { it.chainId != winner.chainId && it.unitPrice > winner.unitPrice }
        ?: return null
      return 1.0 - winner.unitPrice / next.unitPrice
    }

  /**
   * A chain whose pack costs less than the recommended one but is dearer per unit: the
   * cheaper-looking option the user should not fall for. Null when the cheapest pack also wins.
   */
  val cheaperPackElsewhere: PriceOffer?
    get() {
      val winner = best ?: return null
      val cheapestPack = offers.minWithOrNull(compareBy<PriceOffer>({ it.price }, { it.chainId })) ?: return null
      return cheapestPack.takeIf {
        it.chainId != winner.chainId && it.price < winner.price && it.unitPrice > winner.unitPrice
      }
    }

  /** The cheapest price per unit when it was not recommended because it is too old to trust. */
  val cheaperButOld: PriceOffer?
    get() {
      val winner = best ?: return null
      return offers.firstOrNull().takeIf { it != null && it != winner && it.unitPrice < winner.unitPrice }
    }

  companion object {
    val EMPTY = PriceComparison(emptyList(), null)
  }
}

/**
 * Price per kilo, litre or unit, which is what makes two pack sizes comparable.
 * [packTotal] is in grams, millilitres or units. Null when the inputs cannot give a real price.
 */
fun unitPriceOf(price: Double, kind: UnitKind, packTotal: Double): Double? {
  if (!price.isFinite() || !packTotal.isFinite() || price <= 0.0 || packTotal <= 0.0) return null
  return when (kind) {
    UnitKind.MASS, UnitKind.VOLUME -> price / (packTotal / 1000.0)
    UnitKind.COUNT -> price / packTotal
  }.takeIf { it.isFinite() && it > 0 }
}

/**
 * Keeps one offer per chosen chain and picks the cheapest per unit, never by pack price.
 *
 * Offers measured differently (kilos against litres) are never compared: the kind most chains
 * agree on wins. Fresh prices are preferred, and an old one is recommended only when no chain
 * has a fresh price.
 */
fun compareOffers(offers: List<PriceOffer>, chainIds: Set<Int>, now: Long): PriceComparison {
  val chosen = offers.filter { it.chainId in chainIds }
  if (chosen.isEmpty()) return PriceComparison.EMPTY
  val kind = chosen.groupBy { it.unitKind }
    .maxWith(compareBy<Map.Entry<UnitKind, List<PriceOffer>>>({ entry -> entry.value.distinctBy { it.chainId }.size })
      .thenByDescending { it.key.ordinal })
    .key
  val byUnitPrice = compareBy<PriceOffer>({ it.unitPrice }, { it.price }, { it.chainId })
  val perChain = chosen.filter { it.unitKind == kind }
    .groupBy { it.chainId }
    .values
    .map { chainOffers -> chainOffers.filter { it.isFresh(now) }.ifEmpty { chainOffers }.minWith(byUnitPrice) }
    .sortedWith(byUnitPrice)
  val best = perChain.firstOrNull { it.isFresh(now) } ?: perChain.firstOrNull()
  return PriceComparison(perChain, best)
}

private val controlCharacters = Regex("\\p{Cntrl}")

/** Names from the service are shown as plain text: no control characters and a sane length. */
fun cleanServiceText(raw: String, maxLength: Int = 120): String =
  normalizeName(raw.replace(controlCharacters, " ")).take(maxLength).trim()
