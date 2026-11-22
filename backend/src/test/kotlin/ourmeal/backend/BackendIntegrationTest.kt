package ourmeal.backend

import com.google.gson.JsonParser
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.Assert.*
import ourmeal.app.model.*
import java.time.Instant

class BackendIntegrationTest {
  @Before fun reset() {
    runtime.reset()
    repository.saveCity(Fixtures.city)
  }

  @Test fun `catalogue persists and compares by kilo rather than by pack`() {
    repository.ingest(7, Fixtures.product(1))
    repository.ingest(7, Fixtures.product(2))
    Ingestion(repository, { Fixtures.Source() }, enabled = false).use { ingestion ->
      val engine = PriceEngine(repository, ingestion)
      val resolution = engine.resolve(ResolveRequest("arroz redondo", ItemMode.GENERIC, 7, listOf(1, 2)))
      assertEquals("RESOLVED", resolution.status)
      val group = resolution.candidates!!.single()!!.groupId!!
      val result = engine.compare(CompareRequest(listOf(1, 2), listOf(10, 20), listOf(CompareItemDto("rice", ItemMode.GENERIC, group, null)), 7))
      val prices = result.items!!.single()!!.offers!!.filterNotNull()
      assertEquals(2, prices.first().chainId)
      assertEquals(1.79, prices.first().unitPrice!!, 0.0001)
      assertEquals(1.98, prices.last().unitPrice!!, 0.0001)
      assertEquals(group, CatalogRepository(db).search(7, setOf(1), "arroz redondo", ItemMode.GENERIC).single().candidate.groupId)
    }
  }

  @Test fun `city and selected chains never leak prices from another location`() {
    val other = Fixtures.city.copy(city = Fixtures.city.city.copy(id = 8, relationId = 8008, name = "Otra ciudad"))
    repository.saveCity(other)
    repository.ingest(7, Fixtures.product(1, price = 2.0))
    repository.ingest(8, Fixtures.product(1, price = 0.10))
    val group = repository.search(7, setOf(1), "arroz redondo", ItemMode.GENERIC).single().candidate.groupId
    assertEquals(2.0, repository.offers(7, setOf(1), group, null).single().price!!, 0.0)
    assertTrue(repository.offers(7, setOf(2), group, null).isEmpty())
    Ingestion(repository, { Fixtures.Source() }, false).use { ingestion ->
      val engine = PriceEngine(repository, ingestion)
      assertThrows(IllegalArgumentException::class.java) { engine.scope(7, listOf(99)) }
      assertThrows(IllegalArgumentException::class.java) {
        engine.compare(CompareRequest(listOf(1), listOf(20), listOf(CompareItemDto("x", ItemMode.GENERIC, group, null)), 7))
      }
    }
  }

  @Test fun `abnormal prices are quarantined and repeated observations are not duplicated`() {
    val now = Instant.now()
    val first = Fixtures.product(1, price = 1.0, observedAt = now.minusSeconds(172800))
    repository.ingest(7, first)
    repository.ingest(7, first)
    val group = repository.search(7, setOf(1), "arroz redondo", ItemMode.GENERIC).single().candidate.groupId
    assertEquals(1, db.source.connection.use { it.rows("SELECT count(*) FROM price") { it.getInt(1) }.single() })
    repository.ingest(7, first.copy(price = 100.0, observedAt = now.toString()))
    assertTrue(repository.offers(7, setOf(1), group, null).isEmpty())
    assertEquals(2, db.source.connection.use { it.rows("SELECT count(*) FROM price") { it.getInt(1) }.single() })
  }

  @Test fun `same installation cannot cast several votes for an alias`() {
    repository.ingest(7, Fixtures.product(1))
    val group = repository.search(7, setOf(1), "arroz redondo", ItemMode.GENERIC).single().candidate.groupId
    repeat(3) { repository.alias("arroz para paella", "g:$group", "installation-one") }
    assertNull(repository.confidentAlias("arroz para paella"))
    repository.alias("arroz para paella", "g:$group", "installation-two")
    assertEquals(group, repository.confidentAlias("arroz para paella")!!.groupId)
  }

  @Test fun `an explicit brand and pack cannot resolve to another brand or size`() {
    repository.ingest(7, Fixtures.product(1))
    repository.ingest(7, Fixtures.product(2))
    Ingestion(repository, { Fixtures.Source() }, false).use { ingestion ->
      val engine = PriceEngine(repository, ingestion)
      val result = engine.resolve(ResolveRequest("Arroz redondo Marca 1 500g", ItemMode.GENERIC, 7, listOf(1, 2)))
      assertEquals("RESOLVED", result.status)
      val candidate = result.candidates!!.single()!!
      assertNotNull(candidate.canonicalId)
      assertEquals("Marca 1", candidate.brand)
      assertEquals("REFINE", engine.resolve(ResolveRequest("Arroz redondo Marca 1 1kg", ItemMode.BRAND, 7, listOf(1, 2))).status)
    }
  }

  @Test fun `dietary restrictions never silently resolve to another variant`() {
    repository.ingest(7, Fixtures.product(1, name = "Leche entera", kind = "volume"))
    Ingestion(repository, { Fixtures.Source() }, false).use { ingestion ->
      val engine = PriceEngine(repository, ingestion)
      listOf("leche desnatada", "leche sin lactosa").forEach { query ->
        assertEquals("REFINE", engine.resolve(ResolveRequest(query, ItemMode.GENERIC, 7, listOf(1))).status)
      }
    }
  }

  @Test fun `HTTP API uses real persistence and background ingestion with the Android wire format`() {
    Ingestion(repository, { Fixtures.Source() }, enabled = true).use { ingestion ->
      val engine = PriceEngine(repository, ingestion)
      val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) { priceApi(engine, Fixtures.LocationsFixture(), ingestion, db) }.start()
      try {
        val port = runBlocking { server.resolvedConnectors().single().port }
        val client = OkHttpClient()
        fun request(path: String, body: String? = null, key: String? = "test-installation") = client.newCall(Request.Builder()
          .url("http://127.0.0.1:$port$path").apply {
            if (key != null) header("X-Api-Key", key)
            if (body != null) post(body.toRequestBody("application/json".toMediaType()))
          }.build()).execute()
        request("/v1/cities?query=Sevilla", key = null).use { assertEquals(401, it.code) }
        request("/v1/cities?query=Sevilla").use { assertEquals(200, it.code); assertTrue(it.body!!.string().contains("Sevilla")) }
        request("/v1/stores?city=Sevilla").use { assertEquals(200, it.code); assertTrue(it.body!!.string().contains("Cadena B")) }
        val body = """{"query":"arroz redondo","mode":"GENERIC","cityId":7,"chainIds":[1,2]}"""
        var group: Int? = null
        repeat(30) {
          if (group != null) return@repeat
          request("/v1/resolve", body).use { response ->
            assertEquals(200, response.code)
            val json = JsonParser.parseString(response.body!!.string()).asJsonObject
            if (json["status"].asString == "RESOLVED") group = json["candidates"].asJsonArray[0].asJsonObject["groupId"].asInt
          }
          if (group == null) Thread.sleep(100)
        }
        assertNotNull(group)
        request("/v1/compare", """{"cityId":7,"chainIds":[1,2],"storeIds":[10,20],"items":[{"id":"rice","mode":"GENERIC","groupId":$group}]}""").use {
          assertEquals(200, it.code)
          val offers = JsonParser.parseString(it.body!!.string()).asJsonObject["items"].asJsonArray[0].asJsonObject["offers"].asJsonArray
          assertEquals(2, offers[0].asJsonObject["chainId"].asInt)
        }
        request("/v1/resolve", body.replace("[1,2]", "[99]")).use { assertEquals(400, it.code) }
        request("/v1/resolve", "{not json}").use { assertEquals(400, it.code) }
        request("/v1/resolve", "[".repeat(1000) + "0" + "]".repeat(1000)).use { assertEquals(400, it.code) }
        request("/health").use { assertEquals(200, it.code) }
      } finally { server.stop(100, 3000) }
    }
  }

  companion object {
    private lateinit var runtime: TestDatabase
    private lateinit var db: Database
    private lateinit var repository: CatalogRepository
    @JvmStatic @BeforeClass fun start() {
      runtime = TestDatabase.start()
      db = runtime.database
      repository = CatalogRepository(db)
    }
    @JvmStatic @AfterClass fun stop() { if (::runtime.isInitialized) runtime.close() }
  }
}
