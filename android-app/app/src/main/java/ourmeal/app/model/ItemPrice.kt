package ourmeal.app.model

import com.google.gson.annotations.SerializedName
import java.util.Locale

/** Highest price a product can take, 9999,99 €, so totals never overflow or read as typos. */
const val PRICE_MAX_CENTS = 999_999L
const val PRODUCT_PRICES_VERSION = 1
const val PRODUCT_PRICE_CAP = 500

/** Longest run of euro digits worth parsing; anything longer is over the limit anyway. */
private const val PRICE_MAX_EURO_DIGITS = 7
private val priceNoise = Regex("[\\s\u00A0€]")
private val pricePattern = Regex("^(\\d*)(?:[.,](\\d*))?$")

/** What the user typed in a price field. */
sealed interface PriceInput {
  /** Nothing typed: the price is removed. */
  data object Empty : PriceInput
  data class Valid(val cents: Long) : PriceInput
  /** Not an amount: letters, a sign, two separators or more than two decimals. */
  data object Invalid : PriceInput
  data object Zero : PriceInput
  data object TooLarge : PriceInput
}

/**
 * Reads an amount in euros as written in Spain or elsewhere: `2,5`, `2.50`, `3` or `1,99 €`.
 * Either a comma or a dot separates the cents; thousands separators are not accepted, since
 * `1.234` is ambiguous and every valid price has at most four euro digits.
 */
fun parsePrice(raw: String): PriceInput {
  val text = raw.replace(priceNoise, "")
  if (text.isEmpty()) return PriceInput.Empty
  val match = pricePattern.matchEntire(text) ?: return PriceInput.Invalid
  val euros = match.groupValues[1]
  val decimals = match.groupValues[2]
  if (euros.isEmpty() && decimals.isEmpty()) return PriceInput.Invalid
  if (decimals.length > 2) return PriceInput.Invalid
  if (euros.trimStart('0').length > PRICE_MAX_EURO_DIGITS) return PriceInput.TooLarge
  val cents = (euros.ifEmpty { "0" }.toLong() * 100) + decimals.padEnd(2, '0').toLong()
  return when {
    cents == 0L -> PriceInput.Zero
    cents > PRICE_MAX_CENTS -> PriceInput.TooLarge
    else -> PriceInput.Valid(cents)
  }
}

/**
 * Spanish notation with a non-breaking space before the symbol: `1,20 €`. No thousands
 * separator: the highest price has four euro digits, which Spanish writes ungrouped.
 */
fun formatPrice(cents: Long): String = "%d,%02d\u00A0€".format(Locale.ROOT, cents / 100, cents % 100)

/** The text that fills a price field: `1,2` for 1,20 € would read as a typo, so cents stay. */
fun priceFieldText(cents: Long): String = "%d,%02d".format(Locale.ROOT, cents / 100, cents % 100)

/** A stored price is only trusted inside the limits a user could have typed. */
fun validPrice(cents: Long?): Long? = cents?.takeIf { it in 1..PRICE_MAX_CENTS }

/** The price a product shows: the one written in its list or, failing that, the remembered one. */
data class ShownPrice(val cents: Long, val remembered: Boolean)

fun ListItem.shownPrice(remembered: Map<String, Long>): ShownPrice? {
  validPrice(priceCents)?.let { return ShownPrice(it, remembered = false) }
  return remembered[matchKey(name)]?.let { ShownPrice(it, remembered = true) }
}

/** Sum of the known prices, and how many products have none and are left out of it. */
data class PriceEstimate(val totalCents: Long, val priced: Int, val unpriced: Int) {
  val hasPrices: Boolean get() = priced > 0
}

fun estimateOf(prices: List<Long?>): PriceEstimate {
  val known = prices.mapNotNull(::validPrice)
  return PriceEstimate(totalCents = known.sum(), priced = known.size, unpriced = prices.size - known.size)
}

fun List<ListItem>.estimate(remembered: Map<String, Long>): PriceEstimate =
  estimateOf(map { it.shownPrice(remembered)?.cents })

/** Writes [cents] on the product [itemId], or removes its price when null; null when it no longer exists. */
fun UserList.withItemPrice(itemId: Long, cents: Long?, now: Long): UserList? {
  if (items.none { it.id == itemId }) return null
  return copy(items = items.map { item ->
    if (item.id == itemId) item.copy(priceCents = validPrice(cents), updatedAt = now) else item.copy()
  }.toMutableList())
}

data class RememberedPrice(
  @SerializedName("key") val key: String,
  @SerializedName("name") val name: String,
  @SerializedName("cents") val cents: Long,
  @SerializedName("updated_at") val updatedAt: Long
)

/** The last price written for each product, to offer it the next time the product is listed. */
data class ProductPrices(
  @SerializedName("version") val version: Int = PRODUCT_PRICES_VERSION,
  @SerializedName("prices") val prices: List<RememberedPrice> = emptyList()
) {
  /** Price by [matchKey] of the product name. */
  fun byKey(): Map<String, Long> = prices.associate { it.key to it.cents }

  /** The newest price of a product replaces the older one; the oldest drop past [PRODUCT_PRICE_CAP]. */
  fun remember(name: String, cents: Long, now: Long): ProductPrices {
    val key = matchKey(name)
    val price = validPrice(cents)
    if (key.isEmpty() || price == null) return this
    val entry = RememberedPrice(key, normalizeName(name), price, now)
    return copy(prices = (listOf(entry) + prices.filterNot { it.key == key }).take(PRODUCT_PRICE_CAP))
  }

  /** A price removed by the user is not offered again. */
  fun forget(name: String): ProductPrices {
    val key = matchKey(name)
    return if (prices.none { it.key == key }) this else copy(prices = prices.filterNot { it.key == key })
  }

  /** Gson skips the Kotlin constructor, so a missing or null field arrives as null here. */
  fun sanitized(): ProductPrices {
    val stored: List<RememberedPrice?>? = prices
    val valid = stored.orEmpty().mapNotNull { entry ->
      val key: String? = entry?.key
      val name: String? = entry?.name
      val cents = validPrice(entry?.cents)
      if (entry == null || key.isNullOrBlank() || name.isNullOrBlank() || cents == null) null
      else entry.copy(name = normalizeName(name), cents = cents)
    }
    return ProductPrices(
      version = if (version > 0) version else PRODUCT_PRICES_VERSION,
      prices = valid.sortedByDescending { it.updatedAt }.distinctBy { it.key }.take(PRODUCT_PRICE_CAP)
    )
  }
}
