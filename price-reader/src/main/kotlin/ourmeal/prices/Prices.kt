package ourmeal.prices

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import ourmeal.prices.shared.ProductText

internal data class Pack(val kind: String, val total: Double) {
  fun unitPrice(price: Double): Double = price / if (kind == "count") total else total / 1000.0
}

/** No default of one unit: missing quantities are not comparable prices. */
internal fun parsePack(raw: String): Pack? = ProductText.quantity(raw)?.let { Pack(it.kind, it.total) }

/** Reads Product/Offer JSON-LD (including @graph and ItemList); never guesses from page prose. */
internal fun pricesFromJson(
  root: JsonElement,
  chainId: Int,
  pageUrl: String,
  now: Instant = Instant.now()
): List<ProductPrice> {
  val products = mutableListOf<ProductPrice>()
  fun visit(value: JsonElement, depth: Int) {
    if (depth > 24) return
    if (value.isJsonArray) value.asJsonArray.forEach { visit(it, depth + 1) }
    if (!value.isJsonObject) return
    val item = value.asJsonObject
    val types = item.get("@type")
    val isProduct = if (types?.isJsonArray == true) types.asJsonArray.any { it.isJsonPrimitive && it.asString == "Product" }
      else item.text("@type") == "Product"
    if (isProduct) products += productOffers(item, chainId, pageUrl, now)
    item.entrySet().forEach { (_, child) -> if (child.isJsonArray || child.isJsonObject) visit(child, depth + 1) }
  }
  visit(root, 0)
  return products.distinctBy { listOf(it.externalId, it.price, it.packTotal) }
}

private fun productOffers(product: JsonObject, chainId: Int, pageUrl: String, now: Instant): List<ProductPrice> {
  val name = product.text("name")?.takeIf { it.length in 2..240 } ?: return emptyList()
  val size = product.text("size") ?: product.get("weight")?.takeIf { it.isJsonObject }?.asJsonObject?.let { weight ->
    val unit = when (weight.text("unitCode")) { "GRM" -> "g"; "KGM" -> "kg"; "MLT" -> "ml"; "LTR" -> "l"; else -> weight.text("unitText") }
    weight.text("value")?.let { "$it ${unit.orEmpty()}" }
  }
  val pack = size?.let(::parsePack) ?: parsePack(name) ?: return emptyList()
  val rawOffers = product.get("offers") ?: return emptyList()
  val offers = if (rawOffers.isJsonArray) rawOffers.asJsonArray.toList() else listOf(rawOffers)
  return offers.mapNotNull { element ->
    val offer = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
    // AggregateOffer.lowPrice is not a verified, purchasable offer.
    if (offer.text("@type") == "AggregateOffer") return@mapNotNull null
    val specification = offer.get("priceSpecification")?.takeIf { it.isJsonObject }?.asJsonObject
    val currency = offer.text("priceCurrency") ?: specification?.text("priceCurrency")
    if (currency != "EUR") return@mapNotNull null
    val price = (offer.text("price") ?: specification?.text("price"))?.replace(',', '.')?.toDoubleOrNull()
      ?.takeIf { it.isFinite() && it > 0 && it < 10_000 } ?: return@mapNotNull null
    val availability = offer.text("availability")?.substringAfterLast('/')
    if (availability != null && availability !in setOf("InStock", "LimitedAvailability", "OnlineOnly")) return@mapNotNull null
    offer.text("priceValidUntil")?.let { expiry ->
      val date = runCatching { LocalDate.parse(expiry.take(10)) }.getOrNull() ?: return@mapNotNull null
      if (date.isBefore(now.atOffset(ZoneOffset.UTC).toLocalDate())) return@mapNotNull null
    }
    val url = offer.text("url") ?: product.text("url") ?: pageUrl
    val resolved = pageUrl.toHttpUrlOrNull()?.resolve(url)?.toString()
      ?.let(::publicWebsite) ?: return@mapNotNull null
    val id = product.text("sku") ?: product.text("gtin13") ?: product.text("gtin") ?: resolved
    val unitPrice = pack.unitPrice(price).takeIf { it.isFinite() && it > 0 } ?: return@mapNotNull null
    val brand = product.text("brand") ?: product.get("brand")?.takeIf { it.isJsonObject }?.asJsonObject?.text("name")
    val ean = (product.text("gtin13") ?: product.text("gtin8") ?: product.text("gtin"))?.takeIf { Regex("\\d{8}|\\d{13}").matches(it) }
    ProductPrice(id, chainId, name, price, pack.kind, pack.total, unitPrice, now.toString(), resolved, brand = brand, ean = ean)
  }
}
