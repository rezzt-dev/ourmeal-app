package ourmeal.app.controller

import android.content.Context
import ourmeal.app.model.ProductPrices

/** `product_prices.json`: the last price written for each product. */
class ProductPriceManager(context: Context) {
  private val document = JsonDocument(
    context, "product_prices.json", ProductPrices::class.java, ::ProductPrices, ProductPrices::sanitized
  )

  /** No prices when the file does not exist; IOException when it cannot be read. */
  fun load(): ProductPrices = document.load()

  /** Read-modify-write under the file lock. Null when reading or writing fails. */
  fun update(change: (ProductPrices) -> ProductPrices): ProductPrices? = document.update(change)
}
