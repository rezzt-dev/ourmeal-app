package ourmeal.app.controller

import android.content.Context
import ourmeal.app.model.PriceCache
import ourmeal.app.model.PriceSettings
import ourmeal.app.model.ProductAliases
import java.util.UUID

/** `price_settings.json`: whether prices are compared, the city and the chosen supermarkets. */
class PriceSettingsManager(context: Context) {
  private val store = JsonFileStore(
    context, "price_settings.json", PriceSettings::class.java, ::PriceSettings, PriceSettings::sanitized
  )

  /** Settings with an installation key; IOException when the file cannot be read. */
  fun load(): PriceSettings {
    val settings = store.read()
    if (settings.installationKey.isNotEmpty()) return settings
    // The key is created once and kept; if it cannot be saved yet, this session uses a fresh one.
    return update { it } ?: settings.copy(installationKey = newKey())
  }

  /** Null when the file cannot be read or written. */
  fun update(change: (PriceSettings) -> PriceSettings): PriceSettings? = store.update { current ->
    val next = change(current)
    if (next.installationKey.isEmpty()) next.copy(installationKey = current.installationKey.ifEmpty { newKey() }) else next
  }

  private fun newKey(): String = UUID.randomUUID().toString()
}

/** `product_aliases.json`: what each product name was resolved to, so it is asked only once. */
class ProductAliasManager(context: Context) {
  private val store = JsonFileStore(
    context, "product_aliases.json", ProductAliases::class.java, ::ProductAliases, ProductAliases::sanitized
  )

  fun load(): ProductAliases = store.read()

  fun update(change: (ProductAliases) -> ProductAliases): ProductAliases? = store.update(change)
}

/** `price_cache.json`: the last prices received. Disposable, so a damaged copy is replaced. */
class PriceCacheManager(context: Context) {
  private val store = JsonFileStore(
    context, "price_cache.json", PriceCache::class.java, ::PriceCache, PriceCache::sanitized,
    discardDamaged = true
  )

  fun load(): PriceCache = try { store.read() } catch (_: java.io.IOException) { PriceCache() }

  fun update(change: (PriceCache) -> PriceCache): PriceCache? = store.update(change)
}
