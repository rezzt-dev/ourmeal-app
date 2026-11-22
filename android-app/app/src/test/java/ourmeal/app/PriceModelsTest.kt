package ourmeal.app

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ourmeal.app.model.CandidateDto
import ourmeal.app.model.CachedComparison
import ourmeal.app.model.ChainStores
import ourmeal.app.model.CityDto
import ourmeal.app.model.CityStores
import ourmeal.app.model.CityStoresResponse
import ourmeal.app.model.CompareResponse
import ourmeal.app.model.ItemComparisonDto
import ourmeal.app.model.ItemMode
import ourmeal.app.model.OSM_ATTRIBUTION
import ourmeal.app.model.OfferDto
import ourmeal.app.model.PRICE_CACHE_TTL_MS
import ourmeal.app.model.PriceCache
import ourmeal.app.model.PriceChain
import ourmeal.app.model.PriceCity
import ourmeal.app.model.PriceSettings
import ourmeal.app.model.PriceSource
import ourmeal.app.model.PriceTarget
import ourmeal.app.model.ProductAlias
import ourmeal.app.model.ProductAliases
import ourmeal.app.model.Resolution
import ourmeal.app.model.ResolveResponse
import ourmeal.app.model.StoreDto
import ourmeal.app.model.UnitKind
import ourmeal.app.model.formatEuros
import ourmeal.app.model.formatPack
import ourmeal.app.model.formatUnitPrice
import ourmeal.app.model.toCities
import ourmeal.app.model.toCityStores
import ourmeal.app.model.toOffer
import ourmeal.app.model.toOffers
import ourmeal.app.model.toResolution

class PriceModelsTest {
  private val gson = Gson()
  private val nbsp = '\u00A0'
  private val join = "\u2060/\u2060"

  private fun offerDto(
    chainId: Int? = 1,
    price: Double? = 1.15,
    unitPrice: Double? = 2.30,
    unitKind: String? = "mass",
    packTotal: Double? = 500.0,
    observedAt: String? = "2026-11-20T10:00:00Z",
    source: String? = "online"
  ) = OfferDto(chainId, "Arroz redondo 500 g", price, unitPrice, unitKind, packTotal, false, observedAt, source)

  // FORMATO

  @Test
  fun pricesAreWrittenTheSpanishWay() {
    assertEquals("1,15${nbsp}€", formatEuros(1.15))
    // The unit stays on the same line as the price, even with large fonts.
    assertEquals("2,30${nbsp}€${join}kg", formatUnitPrice(2.3, UnitKind.MASS))
    assertEquals("0,98${nbsp}€${join}L", formatUnitPrice(0.98, UnitKind.VOLUME))
    assertEquals("0,25${nbsp}€${join}ud", formatUnitPrice(0.25, UnitKind.COUNT))
  }

  @Test
  fun packSizesUseTheNaturalUnit() {
    assertEquals("500 g", formatPack(500.0, UnitKind.MASS))
    assertEquals("1,5 kg", formatPack(1500.0, UnitKind.MASS))
    assertEquals("330 ml", formatPack(330.0, UnitKind.VOLUME))
    assertEquals("2 L", formatPack(2000.0, UnitKind.VOLUME))
    assertEquals("12 ud", formatPack(12.0, UnitKind.COUNT))
    assertNull(formatPack(0.0, UnitKind.MASS))
  }

  // OFERTAS

  @Test
  fun anOfferRecomputesItsPricePerKiloFromThePack() {
    val offer = offerDto(unitPrice = 99.0).toOffer()!!
    assertEquals(2.30, offer.unitPrice, 1e-9)
    assertEquals(UnitKind.MASS, offer.unitKind)
    assertEquals(PriceSource.ONLINE, offer.source)
    assertEquals(1_795_168_800_000L, offer.observedAt)
  }

  @Test
  fun withoutPackSizeTheServicePricePerUnitIsUsed() {
    assertEquals(2.30, offerDto(packTotal = null).toOffer()!!.unitPrice, 1e-9)
  }

  @Test
  fun offersWithoutDateSourceKindOrRealPriceAreDropped() {
    assertNull(offerDto(observedAt = null).toOffer())
    assertNull(offerDto(observedAt = "ayer").toOffer())
    assertNull(offerDto(source = "scraper").toOffer())
    assertNull(offerDto(unitKind = null).toOffer())
    assertNull(offerDto(price = 0.0).toOffer())
    assertNull(offerDto(price = -1.0).toOffer())
    assertNull(offerDto(price = 50_000.0).toOffer())
    assertNull(offerDto(chainId = null).toOffer())
    assertNull(offerDto(packTotal = null, unitPrice = null).toOffer())
  }

  @Test
  fun datesWithAnOffsetAreAccepted() {
    assertEquals(1_795_168_800_000L, offerDto(observedAt = "2026-11-20T11:00:00+01:00").toOffer()!!.observedAt)
  }

  @Test
  fun itemsMissingFromTheResponseHaveNoOffers() {
    val response = CompareResponse(
      listOf(
        ItemComparisonDto("g:1", listOf(offerDto(), null, offerDto(source = null))),
        ItemComparisonDto("g:99", listOf(offerDto())),
        null
      )
    )
    val offers = response.toOffers(setOf("g:1", "g:2"))
    assertEquals(setOf("g:1", "g:2"), offers.keys)
    assertEquals(1, offers.getValue("g:1").size)
    assertTrue(offers.getValue("g:2").isEmpty())
  }

  // RESOLUCION

  @Test
  fun resolutionFollowsTheServiceStatus() {
    val leche = CandidateDto(groupId = 4, canonicalId = null, name = "Leche semidesnatada 1 L", brand = null, score = 0.9)
    val puleva = CandidateDto(groupId = 4, canonicalId = 8, name = "Leche Puleva 1 L", brand = "Puleva", score = 0.6)
    val resolved = ResolveResponse("RESOLVED", listOf(leche)).toResolution() as Resolution.Resolved
    assertEquals(PriceTarget(4, null, "Leche semidesnatada 1 L"), resolved.target)
    assertEquals(ItemMode.GENERIC, resolved.target.mode)
    assertEquals("g:4", resolved.target.key)

    val choose = ResolveResponse("choose", listOf(leche, puleva, leche)).toResolution() as Resolution.Choose
    assertEquals(listOf("g:4", "c:8"), choose.options.map { it.target.key })
    assertEquals(ItemMode.BRAND, choose.options[1].target.mode)

    assertTrue(ResolveResponse("REFINE", emptyList()).toResolution() is Resolution.Refine)
    assertNull(ResolveResponse("MAYBE", listOf(leche)).toResolution())
  }

  @Test
  fun aResolvedAnswerWithoutUsableCandidateIsNotTrusted() {
    val broken = CandidateDto(groupId = null, canonicalId = null, name = "Leche", brand = null, score = 1.0)
    assertEquals(Resolution.Refine(emptyList()), ResolveResponse("RESOLVED", listOf(broken)).toResolution())
  }

  // CIUDADES Y TIENDAS

  @Test
  fun citiesNeedAnIdAndAName() {
    val cities = listOf(CityDto(1, " Sevilla ", "Sevilla"), CityDto(null, "X", null), CityDto(2, "", null), null).toCities()
    assertEquals(listOf(PriceCity(1, "Sevilla", "Sevilla")), cities)
  }

  @Test
  fun storesAreGroupedByChainAndUnknownChainsLeftOut() {
    val response = CityStoresResponse(
      cityId = 7, city = "Sevilla", province = "Sevilla", attribution = null,
      stores = listOf(
        StoreDto(1, 10, "Cadena B", null),
        StoreDto(2, 20, "Cadena A", null),
        StoreDto(3, 20, "Cadena A", null),
        StoreDto(4, null, null, "Tienda de barrio"),
        StoreDto(null, 10, "Cadena B", null)
      )
    )
    val stores = response.toCityStores()!!
    assertEquals(PriceCity(7, "Sevilla", "Sevilla"), stores.city)
    assertEquals(listOf(20, 10), stores.chains.map { it.chain.id })
    assertEquals(listOf(2, 3), stores.chains.first().storeIds)
    assertEquals(OSM_ATTRIBUTION, stores.attribution)
    assertNull(CityStoresResponse(null, "Sevilla", null, emptyList(), null).toCityStores())
  }

  // AJUSTES

  private val sevilla = CityStores(
    PriceCity(7, "Sevilla", null),
    listOf(ChainStores(PriceChain(20, "Cadena A"), listOf(2, 3)), ChainStores(PriceChain(10, "Cadena B"), listOf(1))),
    OSM_ATTRIBUTION
  )

  @Test
  fun aNewCityChoosesAllItsChains() {
    val settings = PriceSettings(enabled = true).withCity(sevilla)
    assertTrue(settings.isReady)
    assertEquals(setOf(10, 20), settings.chainIds)
    assertEquals(listOf(1, 2, 3), settings.storeIds)
  }

  @Test
  fun refreshingTheSameCityKeepsTheChosenChains() {
    val chosen = PriceSettings(enabled = true).withCity(sevilla).withChains(sevilla, setOf(10))
    assertEquals(setOf(10), chosen.withCity(sevilla).chainIds)
    assertEquals(listOf(1), chosen.withCity(sevilla).storeIds)
  }

  @Test
  fun withoutChainsTheComparisonIsNotReady() {
    val settings = PriceSettings(enabled = true).withCity(sevilla).withChains(sevilla, emptySet())
    assertFalse(settings.isReady)
    assertFalse(PriceSettings(enabled = false).withCity(sevilla).isReady)
  }

  @Test
  fun theSignatureChangesWithCityAndChains() {
    val all = PriceSettings(enabled = true).withCity(sevilla)
    assertEquals("7|10,20|1,2,3", all.signature)
    assertEquals("7|10|1", all.withChains(sevilla, setOf(10)).signature)
  }

  @Test
  fun damagedSettingsFieldsAreDropped() {
    val raw = """{"enabled":true,"city":{"id":7,"name":"Sevilla"},"chains":[null,{"id":0,"name":"X"},{"id":3,"name":"Cadena"}],"store_ids":[1,null,-2]}"""
    val settings = gson.fromJson(raw, PriceSettings::class.java).sanitized()
    assertEquals(listOf(PriceChain(3, "Cadena")), settings.chains)
    assertEquals(listOf(1), settings.storeIds)
    assertEquals("", settings.installationKey)
    val noCity = gson.fromJson("""{"enabled":true,"chains":[{"id":3,"name":"Cadena"}]}""", PriceSettings::class.java).sanitized()
    assertTrue(noCity.chains.isEmpty())
    assertFalse(noCity.isReady)
  }

  // ALIAS Y CACHE

  @Test
  fun aliasesKeepTheLatestAndDropInvalidEntries() {
    val target = PriceTarget(4, null, "Leche semidesnatada 1 L")
    val aliases = ProductAliases()
      .upsert(listOf(ProductAlias("leche", "Leche", target, ignored = false, updatedAt = 1)))
      .upsert(listOf(ProductAlias("leche", "Leche", null, ignored = true, updatedAt = 2)))
    assertEquals(1, aliases.aliases.size)
    assertTrue(aliases.find("leche")!!.ignored)

    val raw = """{"aliases":[{"key":"pan","query":"Pan","ignored":false,"updated_at":1},{"key":"sal","query":"Sal","target":{"group_id":2,"name":"Sal fina 1 kg"},"ignored":false,"updated_at":3},null]}"""
    val loaded = gson.fromJson(raw, ProductAliases::class.java).sanitized()
    assertEquals(listOf("sal"), loaded.aliases.map { it.key })
    assertEquals(PriceTarget(2, null, "Sal fina 1 kg"), loaded.find("sal")!!.target)
  }

  @Test
  fun cachedPricesExpireAndDamagedOffersAreDropped() {
    val offer = offerDto().toOffer()!!
    val entry = CachedComparison("g:1", "7|10", listOf(offer), fetchedAt = 1_000L)
    assertFalse(entry.isExpired(1_000L + PRICE_CACHE_TTL_MS))
    assertTrue(entry.isExpired(1_001L + PRICE_CACHE_TTL_MS))
    assertTrue(entry.isExpired(999L))

    val cache = gson.fromJson(gson.toJson(PriceCache(entries = listOf(entry))), PriceCache::class.java).sanitized()
    assertEquals(listOf(offer), cache.find("g:1", "7|10")!!.offers)
    assertNull(cache.find("g:1", "7|20"))

    val damaged = """{"entries":[{"target_key":"g:1","signature":"s","fetched_at":1,"offers":[{"chain_id":1,"name":"X","price":1.0,"unit_price":1.0,"unit_kind":"stone","observed_at":1,"source":"online"}]}]}"""
    assertTrue(gson.fromJson(damaged, PriceCache::class.java).sanitized().entries.single().offers.isEmpty())
  }
}
