package ourmeal.prices

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class PricesTest {
  @Test fun `compares mass volume and count without guessing missing quantities`() {
    assertEquals(Pack("mass", 500.0), parsePack("Yogur griego 4x125 g"))
    assertEquals(Pack("mass", 1500.0), parsePack("Yogur 2 x 6 x 125 g"))
    assertEquals(Pack("volume", 1500.0), parsePack("Aceite 1,5 litros"))
    assertEquals(Pack("volume", 1980.0), parsePack("Refresco 6 x 33 cl"))
    assertEquals(Pack("count", 12.0), parsePack("Papel higiénico 12 rollos"))
    assertNull(parsePack("Huevos L"))
    assertNull(parsePack("Manzanas"))
    assertNull(parsePack("Yogur 125 g o 500 g"))
    assertEquals(4.0, parsePack("Yogur 4x125g")!!.unitPrice(2.0), 0.00001)
  }

  @Test fun `reads nested JSON LD with observation time and source URL`() {
    val json = """{"@graph":[{"@type":"ItemList","itemListElement":[{"item":{
      "@type":["Product"],"name":"Yogur 4x125 g","sku":"yogur-1",
      "offers":{"@type":"Offer","price":"2.00","priceCurrency":"EUR","availability":"https://schema.org/InStock","url":"/yogur"}
    }}]}]}"""
    val offer = prices(json).single()
    assertEquals(7, offer.chainId)
    assertEquals("mass", offer.unitKind)
    assertEquals(500.0, offer.packTotal, 0.0)
    assertEquals(4.0, offer.unitPrice, 0.0)
    assertEquals("https://shop.example/yogur", offer.url)
    assertEquals("2026-11-22T10:00:00Z", offer.observedAt)
    assertEquals("online", offer.source)
  }

  @Test fun `does not turn price ranges stale offers out of stock or unknown currencies into prices`() {
    val valid = """{"@type":"Product","name":"Leche 1 L","offers":{"@type":"Offer","price":1.1,"priceCurrency":"EUR","availability":"https://schema.org/InStock"}}"""
    assertEquals(1, prices(valid).size)
    listOf(
      valid.replace("EUR", "USD"),
      valid.replace("InStock", "OutOfStock"),
      valid.replace("1.1", "-1.0"),
      valid.replace("1.1", "\"NaN\""),
      valid.replace("Leche 1 L", "Leche"),
      valid.replace("\"@type\":\"Offer\"", "\"@type\":\"AggregateOffer\""),
      valid.replace("\"price\":1.1", "\"priceValidUntil\":\"2026-01-01\",\"price\":1.1")
    ).forEach { assertTrue(it, prices(it).isEmpty()) }
  }

  @Test fun `schema quantitative weight and relative URLs are supported`() {
    val offer = prices("""{"@type":"Product","name":"Arroz","weight":{"value":1,"unitCode":"KGM"},"url":"/arroz","offers":{"price":2,"priceCurrency":"EUR"}}""").single()
    assertEquals(1000.0, offer.packTotal, 0.0)
    assertEquals(2.0, offer.unitPrice, 0.0)
  }

  private fun prices(json: String) = pricesFromJson(JsonParser.parseString(json), 7, "https://shop.example/search", Instant.parse("2026-11-22T10:00:00Z"))
}
