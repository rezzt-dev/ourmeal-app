package ourmeal.app.model

import com.google.gson.annotations.SerializedName

const val PRICE_CACHE_VERSION = 1
const val PRICE_CACHE_CAP = 400

/** Prices are asked again after this time; until then, and offline, the saved ones are shown. */
const val PRICE_CACHE_TTL_MS = 6L * 60 * 60 * 1000

/** The offers last received for one target in one city and set of supermarkets. */
data class CachedComparison(
  @SerializedName("target_key") val targetKey: String,
  @SerializedName("signature") val signature: String,
  @SerializedName("offers") val offers: List<PriceOffer>,
  @SerializedName("fetched_at") val fetchedAt: Long
) {
  fun isExpired(now: Long): Boolean = now - fetchedAt > PRICE_CACHE_TTL_MS || fetchedAt > now
}

/** Disposable copy of the last prices received: it holds no user data. */
data class PriceCache(
  @SerializedName("version") val version: Int = PRICE_CACHE_VERSION,
  @SerializedName("entries") val entries: List<CachedComparison> = emptyList()
) {
  fun find(targetKey: String, signature: String): CachedComparison? =
    entries.firstOrNull { it.targetKey == targetKey && it.signature == signature }

  fun upsert(changes: List<CachedComparison>, cap: Int = PRICE_CACHE_CAP): PriceCache {
    val changed = changes.associateBy { it.targetKey to it.signature }
    val kept = entries.filterNot { (it.targetKey to it.signature) in changed }
    return copy(entries = (changed.values + kept).sortedByDescending { it.fetchedAt }.take(cap))
  }

  /** Gson skips the Kotlin constructor, so a missing or null field arrives as null here. */
  fun sanitized(): PriceCache {
    val stored: List<CachedComparison?>? = entries
    return PriceCache(
      version = if (version > 0) version else PRICE_CACHE_VERSION,
      entries = stored.orEmpty().mapNotNull { it?.sanitized() }
        .sortedByDescending { it.fetchedAt }.distinctBy { it.targetKey to it.signature }.take(PRICE_CACHE_CAP)
    )
  }

  private fun CachedComparison.sanitized(): CachedComparison? {
    val storedKey: String? = targetKey
    val storedSignature: String? = signature
    val storedOffers: List<PriceOffer?>? = offers
    if (storedKey.isNullOrBlank() || storedSignature.isNullOrBlank() || storedOffers == null) return null
    return copy(offers = storedOffers.mapNotNull { it?.sanitized() })
  }

  private fun PriceOffer.sanitized(): PriceOffer? {
    val storedName: String? = productName
    val storedKind: UnitKind? = unitKind
    val storedSource: PriceSource? = source
    if (storedName == null || storedKind == null || storedSource == null) return null
    if (chainId <= 0 || !price.isFinite() || price <= 0.0 || !unitPrice.isFinite() || unitPrice <= 0.0) return null
    return copy(productName = cleanServiceText(storedName), packTotal = packTotal?.takeIf { it.isFinite() && it > 0.0 })
  }
}
