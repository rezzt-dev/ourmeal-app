package ourmeal.app

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ourmeal.app.controller.PriceResult
import ourmeal.app.controller.PriceService
import ourmeal.app.model.PriceTarget
import ourmeal.app.model.Resolution

class PriceServiceTest {
  private lateinit var server: MockWebServer
  private lateinit var service: PriceService

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    service = PriceService.create(server.url("/").toString(), "clave-de-prueba", debug = true, userAgent = "OurMeal-Test")!!
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

  @Test
  fun everyCallCarriesTheInstallationKeyAndNothingPersonal() = runBlocking {
    server.enqueue(json("""[{"id":7,"name":"Sevilla","province":"Sevilla"}]"""))
    val result = service.cities("Sevi")
    assertEquals(PriceResult.Success(listOf(ourmeal.app.model.PriceCity(7, "Sevilla", "Sevilla"))), result)
    val request = server.takeRequest()
    assertEquals("/v1/cities?query=Sevi", request.path)
    assertEquals("clave-de-prueba", request.getHeader("X-Api-Key"))
    assertEquals("OurMeal-Test", request.getHeader("User-Agent"))
  }

  @Test
  fun resolveSendsTheNameAndTheMode() = runBlocking {
    server.enqueue(json("""{"status":"RESOLVED","candidates":[{"groupId":4,"name":"Leche semidesnatada 1 L","score":0.93}]}"""))
    val result = service.resolve("leche semi") as PriceResult.Success
    assertEquals(Resolution.Resolved(PriceTarget(4, null, "Leche semidesnatada 1 L")), result.value)
    val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
    assertEquals("leche semi", body["query"].asString)
    assertEquals("GENERIC", body["mode"].asString)
  }

  @Test
  fun compareSendsChainsStoresAndOneItemPerTarget() = runBlocking {
    server.enqueue(
      json(
        """{"items":[{"itemId":"g:4","offers":[
          {"chainId":1,"name":"Leche A","price":0.89,"unitPrice":0.89,"unitKind":"volume","packTotal":1000,"onOffer":false,"observedAt":"2026-11-20T10:00:00Z","source":"online"},
          {"chainId":2,"name":"Leche B","price":1.50,"unitKind":"volume","packTotal":1500,"observedAt":"2026-11-21T10:00:00Z","source":"user"}
        ]}]}"""
      )
    )
    val target = PriceTarget(4, null, "Leche semidesnatada 1 L")
    val result = service.compare(listOf(target, target), chainIds = listOf(2, 1), storeIds = listOf(30, 10)) as PriceResult.Success
    val offers = result.value.getValue("g:4")
    assertEquals(2, offers.size)
    assertEquals(1.0, offers.first { it.chainId == 2 }.unitPrice, 1e-9)

    val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
    assertEquals("[1,2]", body["chainIds"].toString())
    assertEquals("[10,30]", body["storeIds"].toString())
    assertEquals(1, body["items"].asJsonArray.size())
    val item = body["items"].asJsonArray[0].asJsonObject
    assertEquals("g:4", item["id"].asString)
    assertEquals(4, item["groupId"].asInt)
    assertEquals(1, item["quantity"].asInt)
  }

  @Test
  fun aServerErrorIsUnavailable() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(500))
    assertEquals(PriceResult.Unavailable, service.resolve("pan"))
  }

  @Test
  fun backgroundIngestionIsPendingRatherThanNotFound() = runBlocking {
    server.enqueue(json("""{"status":"PROCESSING","candidates":[]}"""))
    assertEquals(PriceResult.Success(Resolution.Pending), service.resolve("arroz"))
    server.enqueue(json("""{"items":[],"pending":true}"""))
    val target = PriceTarget(1, null, "Arroz")
    val result = service.compare(listOf(target), listOf(1), listOf(10), cityId = 7) as PriceResult.Success
    assertTrue(result.value.pending)
    assertTrue(result.value.getValue(target.key).isEmpty())
    server.takeRequest()
    val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
    assertEquals(7, body["cityId"].asInt)
  }

  @Test
  fun anUnusableBodyIsUnavailableNotOffline() = runBlocking {
    server.enqueue(json("""{"status":"""))
    assertEquals(PriceResult.Unavailable, service.resolve("pan"))
    server.enqueue(json("""[1,2,3]"""))
    assertEquals(PriceResult.Unavailable, service.stores("Sevilla", null))
    server.enqueue(json("""{"status":"QUIZAS"}"""))
    assertEquals(PriceResult.Unavailable, service.resolve("pan"))
  }

  @Test
  fun aDroppedConnectionIsOffline() = runBlocking {
    server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
    assertEquals(PriceResult.Offline, service.cities("Sevilla"))
  }

  @Test
  fun aliasesReportWhetherTheServiceTookThem() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(204))
    assertTrue(service.sendAlias("papel water", PriceTarget(9, null, "Papel higiénico")))
    server.enqueue(MockResponse().setResponseCode(429))
    assertFalse(service.sendAlias("papel water", PriceTarget(9, null, "Papel higiénico")))
  }

  @Test
  fun onlyHttpsIsUsableOutsideDebugBuilds() {
    assertTrue(PriceService.isUsableUrl("https://precios.example/", debug = false))
    assertFalse(PriceService.isUsableUrl("http://10.0.2.2:8080/", debug = false))
    assertTrue(PriceService.isUsableUrl("http://10.0.2.2:8080/", debug = true))
    assertFalse(PriceService.isUsableUrl("", debug = true))
    assertFalse(PriceService.isUsableUrl("ftp://precios.example/", debug = true))
    assertNull(PriceService.create("", "k", debug = true, userAgent = "x"))
  }
}
