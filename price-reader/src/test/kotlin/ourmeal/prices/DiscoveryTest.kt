package ourmeal.prices

import com.google.gson.JsonParser
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class DiscoveryTest {
  @get:Rule val temporary = TemporaryFolder()

  @Test fun `location and brand matching preserves ñ while ignoring other accents and spaces`() {
    assertEquals("cañaveral", normalized("  CAÑAVERÁL  "))
    assertNotEquals(normalized("Peña"), normalized("Pena"))
    assertEquals(normalized("San   José"), normalized("san jose"))
  }

  @Test fun `discovers only stores inside selected relation and reuses cache`() {
    MockWebServer().use { server ->
      server.enqueue(MockResponse().setBody(CITIES))
      server.enqueue(MockResponse().setBody(STORES))
      val discovery = Discovery(temporary.root.toPath(), server.url("/search").toString(), server.url("/overpass").toString())
      val city = discovery.cities("Pueblo de prueba").single()
      assertEquals(2468L, city.relationId)
      assertEquals("Provincia de prueba", city.province)
      val catalog = discovery.stores(city)
      assertEquals(2, catalog.chains.size)
      val chain = catalog.chains.single { it.name == "Supermercado nuevo" }
      assertEquals(2, chain.stores.size)
      assertEquals(1, chain.websites.size)
      assertNotEquals(chain.stores[0].id, chain.stores[1].id)
      assertEquals(catalog, discovery.stores(city))
      assertEquals(listOf(city), discovery.cities("Pueblo de prueba"))
      assertEquals(2, server.requestCount)
      val search = server.takeRequest(1, TimeUnit.SECONDS)!!
      assertTrue(search.getHeader("User-Agent")!!.startsWith("Ourmeal-PriceReader"))
      val stores = server.takeRequest(1, TimeUnit.SECONDS)!!
      val body = java.net.URLDecoder.decode(stores.body.readUtf8(), Charsets.UTF_8)
      assertTrue(body.contains("rel(2468);map_to_area->.city"))
      assertFalse(body.contains("around:"))
      assertFalse(body.contains("Pueblo de prueba"))
    }
  }

  @Test fun `a different location has different chains without a fixed registry`() {
    val parsed = parseChains(JsonParser.parseString("""[
      {"type":"node","id":99,"tags":{"shop":"supermarket","name":"Cooperativa local","website":"cooperativa.example"}}
    ]""").asJsonArray)
    assertEquals(listOf("Cooperativa local"), parsed.map { it.name })
    assertEquals(listOf("https://cooperativa.example/"), parsed.single().websites)
    val original = parseChains(JsonParser.parseString(STORES).asJsonObject.getAsJsonArray("elements"))
    assertFalse(original.any { it.id == parsed.single().id })
  }

  @Test fun `partial overpass responses are failures rather than empty cities`() {
    MockWebServer().use { server ->
      server.enqueue(MockResponse().setBody("""{"remark":"runtime error: Query timed out","elements":[]}"""))
      server.enqueue(MockResponse().setBody(STORES))
      val discovery = Discovery(temporary.root.toPath(), overpass = server.url("/").toString())
      assertThrows(java.io.IOException::class.java) { discovery.stores(City(1, "Pueblo", null, 2)) }
      assertEquals(2, discovery.stores(City(1, "Pueblo", null, 2)).chains.size)
      assertEquals(2, server.requestCount)
    }
  }

  @Test fun `ambiguous geocoding returns alternatives instead of picking a different city`() {
    val cities = parseCities(JsonParser.parseString(CITIES.replace("2468", "2469").dropLast(1) + "," + CITIES.drop(1)).asJsonArray)
    assertEquals(2, cities.size)
    assertNotEquals(cities[0].id, cities[1].id)
  }

  companion object {
    private const val CITIES = """[{"osm_type":"relation","osm_id":2468,"addresstype":"town","address":{"town":"Pueblo de prueba","province":"Provincia de prueba"}}]"""
    private const val STORES = """{"elements":[
      {"type":"node","id":1,"tags":{"shop":"supermarket","brand":"Supermercado nuevo","brand:wikidata":"Q123","website":"https://shop.example/"}},
      {"type":"way","id":1,"tags":{"shop":"supermarket","brand":"Supermercado nuevo","website":"https://shop.example/"}},
      {"type":"node","id":3,"tags":{"shop":"supermarket","name":"Tienda independiente"}},
      {"type":"node","id":4,"tags":{"shop":"bakery","name":"Panadería"}}
    ]}"""
  }
}
