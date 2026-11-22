package ourmeal.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ourmeal.app.model.PRICE_FRESH_MS
import ourmeal.app.model.PriceOffer
import ourmeal.app.model.PriceSource
import ourmeal.app.model.UnitKind
import ourmeal.app.model.compareOffers
import ourmeal.app.model.unitPriceOf

class PriceComparisonTest {
  private val now = 1_000L * PRICE_FRESH_MS

  private fun offer(
    chain: Int,
    price: Double,
    pack: Double,
    kind: UnitKind = UnitKind.MASS,
    observedAt: Long = now
  ) = PriceOffer(
    chainId = chain,
    productName = "Producto $chain",
    price = price,
    unitPrice = unitPriceOf(price, kind, pack)!!,
    unitKind = kind,
    packTotal = pack,
    onOffer = false,
    observedAt = observedAt,
    source = PriceSource.ONLINE
  )

  @Test
  fun unitPriceIsPerKiloPerLitreAndPerUnit() {
    assertEquals(2.30, unitPriceOf(1.15, UnitKind.MASS, 500.0)!!, 1e-9)
    assertEquals(0.98, unitPriceOf(1.47, UnitKind.VOLUME, 1500.0)!!, 1e-9)
    assertEquals(0.25, unitPriceOf(3.0, UnitKind.COUNT, 12.0)!!, 1e-9)
  }

  @Test
  fun unitPriceRejectsImpossibleInputs() {
    assertNull(unitPriceOf(0.0, UnitKind.MASS, 500.0))
    assertNull(unitPriceOf(1.0, UnitKind.MASS, 0.0))
    assertNull(unitPriceOf(Double.NaN, UnitKind.MASS, 500.0))
    assertNull(unitPriceOf(1.0, UnitKind.MASS, Double.POSITIVE_INFINITY))
  }

  @Test
  fun theCheaperLookingPackLosesWhenItIsDearerPerKilo() {
    // 250 g at 0,99 € looks cheaper than 1 kg at 2,49 €, but costs 3,96 €/kg against 2,49 €/kg.
    val small = offer(chain = 1, price = 0.99, pack = 250.0)
    val big = offer(chain = 2, price = 2.49, pack = 1000.0)
    val comparison = compareOffers(listOf(small, big), setOf(1, 2), now)
    assertEquals(2, comparison.best?.chainId)
    assertEquals(listOf(2, 1), comparison.offers.map { it.chainId })
    assertEquals(1, comparison.cheaperPackElsewhere?.chainId)
    assertEquals(1 - 2.49 / 3.96, comparison.savingOverNext!!, 1e-9)
  }

  @Test
  fun noTrapWhenTheCheapestPackAlsoWins() {
    val comparison = compareOffers(
      listOf(offer(chain = 1, price = 1.0, pack = 1000.0), offer(chain = 2, price = 2.0, pack = 1000.0)),
      setOf(1, 2), now
    )
    assertNull(comparison.cheaperPackElsewhere)
    assertNull(comparison.cheaperButOld)
  }

  @Test
  fun onlyChosenChainsAreCompared() {
    val comparison = compareOffers(
      listOf(offer(chain = 1, price = 1.0, pack = 1000.0), offer(chain = 9, price = 0.1, pack = 1000.0)),
      setOf(1), now
    )
    assertEquals(listOf(1), comparison.offers.map { it.chainId })
    assertEquals(1, comparison.best?.chainId)
    assertNull(comparison.savingOverNext)
  }

  @Test
  fun eachChainKeepsItsCheapestOfferPerUnit() {
    val comparison = compareOffers(
      listOf(
        offer(chain = 1, price = 1.0, pack = 250.0),
        offer(chain = 1, price = 2.0, pack = 1000.0),
        offer(chain = 2, price = 2.5, pack = 1000.0)
      ),
      setOf(1, 2), now
    )
    assertEquals(2, comparison.offers.size)
    assertEquals(2.0, comparison.offers.first { it.chainId == 1 }.unitPrice, 1e-9)
  }

  @Test
  fun aFreshPriceWinsOverAnOlderCheaperOne() {
    val old = offer(chain = 1, price = 0.5, pack = 1000.0, observedAt = now - PRICE_FRESH_MS - 1)
    val fresh = offer(chain = 2, price = 1.0, pack = 1000.0)
    val comparison = compareOffers(listOf(old, fresh), setOf(1, 2), now)
    assertEquals(2, comparison.best?.chainId)
    // Still listed, cheapest first, so the user sees it with its date.
    assertEquals(listOf(1, 2), comparison.offers.map { it.chainId })
    // Explained as old, never as a pack that is dearer per kilo.
    assertEquals(1, comparison.cheaperButOld?.chainId)
    assertNull(comparison.cheaperPackElsewhere)
  }

  @Test
  fun anOldPriceIsRecommendedOnlyWhenNothingIsFresh() {
    val old = offer(chain = 1, price = 0.5, pack = 1000.0, observedAt = now - PRICE_FRESH_MS - 1)
    val comparison = compareOffers(listOf(old), setOf(1), now)
    assertEquals(1, comparison.best?.chainId)
    assertTrue(!comparison.best!!.isFresh(now))
  }

  @Test
  fun kilosAreNeverComparedWithLitres() {
    val comparison = compareOffers(
      listOf(
        offer(chain = 1, price = 1.0, pack = 1000.0, kind = UnitKind.MASS),
        offer(chain = 2, price = 1.5, pack = 1000.0, kind = UnitKind.MASS),
        offer(chain = 3, price = 0.1, pack = 1000.0, kind = UnitKind.VOLUME)
      ),
      setOf(1, 2, 3), now
    )
    assertEquals(setOf(UnitKind.MASS), comparison.offers.map { it.unitKind }.toSet())
    assertEquals(1, comparison.best?.chainId)
  }

  @Test
  fun nothingToCompareGivesNoRecommendation() {
    val comparison = compareOffers(emptyList(), setOf(1), now)
    assertNull(comparison.best)
    assertTrue(comparison.offers.isEmpty())
  }
}
