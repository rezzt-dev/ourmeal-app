package ourmeal.app

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ourmeal.app.model.ListItem
import ourmeal.app.model.PRICE_MAX_CENTS
import ourmeal.app.model.PRODUCT_PRICE_CAP
import ourmeal.app.model.PriceInput
import ourmeal.app.model.ProductPrices
import ourmeal.app.model.ShownPrice
import ourmeal.app.model.UserList
import ourmeal.app.model.estimate
import ourmeal.app.model.formatPrice
import ourmeal.app.model.parsePrice
import ourmeal.app.model.priceFieldText
import ourmeal.app.model.shownPrice
import ourmeal.app.model.withItemPrice

class ItemPriceTest {
  private fun item(id: Long, name: String, cents: Long? = null) =
    ListItem(id = id, name = name, createdAt = 0L, updatedAt = 0L, priceCents = cents)

  @Test
  fun pricesAreReadWithCommaOrDotAndOptionalSymbol() {
    assertEquals(PriceInput.Valid(250), parsePrice("2,5"))
    assertEquals(PriceInput.Valid(250), parsePrice("2.50"))
    assertEquals(PriceInput.Valid(300), parsePrice("3"))
    assertEquals(PriceInput.Valid(300), parsePrice("3,"))
    assertEquals(PriceInput.Valid(50), parsePrice(",5"))
    assertEquals(PriceInput.Valid(199), parsePrice(" 1,99 € "))
    assertEquals(PriceInput.Valid(PRICE_MAX_CENTS), parsePrice("9999,99"))
  }

  @Test
  fun emptyZeroInvalidAndLargeAmountsAreToldApart() {
    assertEquals(PriceInput.Empty, parsePrice("   "))
    assertEquals(PriceInput.Zero, parsePrice("0,00"))
    assertEquals(PriceInput.Invalid, parsePrice("1,234"))
    assertEquals(PriceInput.Invalid, parsePrice("1.234,56"))
    assertEquals(PriceInput.Invalid, parsePrice("-2"))
    assertEquals(PriceInput.Invalid, parsePrice("dos"))
    assertEquals(PriceInput.Invalid, parsePrice(","))
    assertEquals(PriceInput.TooLarge, parsePrice("10000"))
    assertEquals(PriceInput.TooLarge, parsePrice("99999999999999999999"))
  }

  @Test
  fun pricesAreWrittenTheSpanishWay() {
    assertEquals("1,20 €", formatPrice(120))
    assertEquals("0,05 €", formatPrice(5))
    assertEquals("9999,99 €", formatPrice(PRICE_MAX_CENTS))
    assertEquals("2,50", priceFieldText(250))
  }

  @Test
  fun theListPriceWinsOverTheRememberedOne() {
    val remembered = mapOf("leche" to 110L, "pan" to 90L)

    assertEquals(ShownPrice(125, remembered = false), item(1, "Leche", 125).shownPrice(remembered))
    assertEquals(ShownPrice(90, remembered = true), item(2, "PAN", null).shownPrice(remembered))
    assertNull(item(3, "Huevos").shownPrice(remembered))
    // A damaged stored price is not shown; the remembered one takes its place.
    assertEquals(ShownPrice(110, remembered = true), item(4, "Leche", -3).shownPrice(remembered))
  }

  @Test
  fun theEstimateAddsKnownPricesAndCountsTheRest() {
    val items = listOf(item(1, "Leche", 125), item(2, "Pan"), item(3, "Huevos"))

    val estimate = items.estimate(mapOf("pan" to 90L))

    assertEquals(215L, estimate.totalCents)
    assertEquals(2, estimate.priced)
    assertEquals(1, estimate.unpriced)
    assertTrue(estimate.hasPrices)
    assertFalse(listOf(item(1, "Sal")).estimate(emptyMap()).hasPrices)
  }

  @Test
  fun aPriceIsWrittenOnOneProductOnly() {
    val list = UserList(1L, "Compra", mutableListOf(item(1, "Leche"), item(2, "Pan")), createdAt = 0L)

    val priced = list.withItemPrice(itemId = 2, cents = 95, now = 7L)!!
    val cleared = priced.withItemPrice(itemId = 2, cents = null, now = 8L)!!

    assertEquals(listOf(null, 95L), priced.items.map { it.priceCents })
    assertEquals(7L, priced.items[1].updatedAt)
    assertNull(cleared.items[1].priceCents)
    assertNull(list.items[1].priceCents)
    assertNull(list.withItemPrice(itemId = 99, cents = 10, now = 9L))
  }

  @Test
  fun rememberedPricesKeepTheNewestPerProductWithinTheCap() {
    var prices = ProductPrices()
    repeat(PRODUCT_PRICE_CAP + 5) { index -> prices = prices.remember("Producto $index", 100L + index, now = index.toLong()) }
    prices = prices.remember("producto 4", 999, now = 10_000)

    assertEquals(PRODUCT_PRICE_CAP, prices.prices.size)
    assertEquals(999L, prices.byKey()["producto 4"])
    assertNull(prices.byKey()["producto 0"])
    assertNull(prices.forget("PRODUCTO 4").byKey()["producto 4"])
    // Invalid amounts are never remembered.
    assertEquals(prices, prices.remember("Sal", 0, now = 1))
  }

  @Test
  fun oldListsWithoutPricesAndDamagedMemoriesAreRead() {
    val gson = Gson()
    val old = gson.fromJson("""{"id":1,"name":"Pan","is_completed":false,"created_at":0}""", ListItem::class.java)
    assertNull(old.priceCents)

    val stored = gson.fromJson(
      """{"prices":[null,{"key":"pan","name":" Pan ","cents":95,"updated_at":2},
        {"key":"","name":"x","cents":10},{"key":"sal","name":"Sal","cents":0},
        {"key":"pan","name":"Pan viejo","cents":80,"updated_at":1}]}""",
      ProductPrices::class.java
    ).sanitized()

    assertEquals(mapOf("pan" to 95L), stored.byKey())
    assertEquals("Pan", stored.prices.single().name)
    assertEquals(1, stored.version)
  }
}
