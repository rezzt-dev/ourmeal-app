package ourmeal.app.model

import com.google.gson.annotations.SerializedName

const val PRODUCT_ALIASES_VERSION = 1
const val PRODUCT_ALIASES_CAP = 500

/**
 * What the user means by a product name, learned once and reused on this device: the group or
 * product it was resolved to, or that the user preferred not to compare it.
 */
data class ProductAlias(
  @SerializedName("key") val key: String,
  @SerializedName("query") val query: String,
  @SerializedName("target") val target: PriceTarget?,
  @SerializedName("ignored") val ignored: Boolean,
  @SerializedName("updated_at") val updatedAt: Long
)

data class ProductAliases(
  @SerializedName("version") val version: Int = PRODUCT_ALIASES_VERSION,
  @SerializedName("aliases") val aliases: List<ProductAlias> = emptyList()
) {
  fun find(key: String): ProductAlias? = aliases.firstOrNull { it.key == key }

  /** Replaces the aliases with the same key and keeps only the most recent [cap] entries. */
  fun upsert(changes: List<ProductAlias>, cap: Int = PRODUCT_ALIASES_CAP): ProductAliases {
    val changed = changes.associateBy { it.key }
    val kept = aliases.filterNot { it.key in changed }
    return copy(aliases = (changed.values + kept).sortedByDescending { it.updatedAt }.take(cap))
  }

  fun remove(key: String): ProductAliases = copy(aliases = aliases.filterNot { it.key == key })

  /** Gson skips the Kotlin constructor, so a missing or null field arrives as null here. */
  fun sanitized(): ProductAliases {
    val stored: List<ProductAlias?>? = aliases
    return ProductAliases(
      version = if (version > 0) version else PRODUCT_ALIASES_VERSION,
      aliases = stored.orEmpty().mapNotNull { it?.sanitized() }
        .sortedByDescending { it.updatedAt }.distinctBy { it.key }.take(PRODUCT_ALIASES_CAP)
    )
  }

  private fun ProductAlias.sanitized(): ProductAlias? {
    val storedKey: String? = key
    val storedQuery: String? = query
    if (storedKey.isNullOrBlank() || storedQuery.isNullOrBlank()) return null
    val validTarget = target?.let { PriceTarget.of(it.groupId, it.canonicalId, it.name) }
    if (!ignored && validTarget == null) return null
    return ProductAlias(storedKey, normalizeName(storedQuery), if (ignored) null else validTarget, ignored, updatedAt)
  }
}
