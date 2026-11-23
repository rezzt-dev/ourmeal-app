package ourmeal.app

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ourmeal.app.model.PURCHASE_ITEM_CAP
import ourmeal.app.model.PURCHASE_TRIP_CAP
import ourmeal.app.model.PURCHASE_TRIP_GAP_MS
import ourmeal.app.model.PurchaseHistory
import ourmeal.app.model.PurchasedItem
import ourmeal.app.model.repeatListName

class PurchaseHistoryTest {
  private val minute = 60_000L

  private fun bought(id: Long, name: String, at: Long, cents: Long? = null) = PurchasedItem(id, name, cents, at)

  private fun PurchaseHistory.markAll(listId: Long, vararg items: PurchasedItem) =
    items.fold(this) { history, item -> history.mark(listId, "Compra", item) }

  @Test
  fun marksOfOneListFormAnOpenTripInMarkingOrder() {
    val history = PurchaseHistory().markAll(1, bought(10, "Leche", 0), bought(11, "Pan", minute), bought(10, "Leche", 2 * minute))

    val trip = history.open.single()
    assertEquals(listOf("Pan", "Leche"), trip.items.map { it.name })
    assertEquals(0L, trip.id)
    assertEquals(minute, trip.durationMs)
    assertTrue(history.trips.isEmpty())
    assertTrue(history.isOpen(trip.id))
  }

  @Test
  fun eachListHasItsOwnTrip() {
    val history = PurchaseHistory().markAll(1, bought(10, "Leche", 0)).markAll(2, bought(10, "Pan", minute))

    assertEquals(listOf(2L, 1L), history.timeline().map { it.listId })
    assertEquals(setOf(0L, minute), history.open.map { it.id }.toSet())
  }

  @Test
  fun aTripClosesAfterTheGapAndEvenAShortOneIsKept() {
    val history = PurchaseHistory().markAll(1, bought(10, "Leche", 0))

    assertEquals(history, history.settle(PURCHASE_TRIP_GAP_MS))
    val settled = history.settle(PURCHASE_TRIP_GAP_MS + 1)

    assertTrue(settled.open.isEmpty())
    assertEquals(listOf("Leche"), settled.trips.single().items.map { it.name })
  }

  @Test
  fun markingLongAfterwardsStartsANewTrip() {
    val history = PurchaseHistory()
      .markAll(1, bought(10, "Leche", 0))
      .markAll(1, bought(10, "Leche", PURCHASE_TRIP_GAP_MS + minute))

    assertEquals(1, history.trips.size)
    assertEquals(1, history.open.size)
    assertEquals(PURCHASE_TRIP_GAP_MS + minute, history.open.single().id)
  }

  @Test
  fun aProductPutBackLeavesTheTripAndAnEmptyTripDisappears() {
    val history = PurchaseHistory().markAll(1, bought(10, "Leche", 0), bought(11, "Pan", minute))

    val withoutPan = history.unmark(1, 11, 2 * minute)
    assertEquals(listOf("Leche"), withoutPan.open.single().items.map { it.name })
    assertTrue(withoutPan.unmark(1, 10, 3 * minute).isEmpty)
    // Putting back something bought on a finished trip does not rewrite that trip.
    val later = history.unmark(1, 10, PURCHASE_TRIP_GAP_MS * 3)
    assertEquals(2, later.trips.single().items.size)
  }

  @Test
  fun aPriceWrittenDuringTheTripIsWhatItCost() {
    val history = PurchaseHistory().markAll(1, bought(10, "Leche", 0, cents = 110), bought(11, "Pan", minute))

    val priced = history.withPrice(1, 11, 95).withPrice(1, 10, null)
    val spend = priced.open.single().spend()

    assertEquals(95L, spend.totalCents)
    assertEquals(1, spend.unpriced)
    assertEquals(priced, priced.withPrice(1, 99, 50))
    assertEquals(priced, priced.withPrice(5, 10, 50))
  }

  @Test
  fun historyIsCappedAndATripCanBeRemovedAndRestored() {
    var history = PurchaseHistory()
    repeat(PURCHASE_TRIP_CAP + 3) { index ->
      history = history.markAll(1, bought(10, "Leche", index * (PURCHASE_TRIP_GAP_MS + minute)))
    }
    history = history.settle(Long.MAX_VALUE / 2)
    assertEquals(PURCHASE_TRIP_CAP, history.trips.size)

    val newest = history.trips.first()
    val removed = history.without(newest.id)
    assertFalse(removed.trips.any { it.id == newest.id })
    assertEquals(history.trips, removed.withRestored(newest).trips)
    assertEquals(history, history.withRestored(newest))
  }

  @Test
  fun aTripKeepsItsLatestProducts() {
    var history = PurchaseHistory()
    repeat(PURCHASE_ITEM_CAP + 10) { index -> history = history.markAll(1, bought(index.toLong(), "P$index", index.toLong())) }

    val items = history.open.single().items
    assertEquals(PURCHASE_ITEM_CAP, items.size)
    assertEquals("P${PURCHASE_ITEM_CAP + 9}", items.last().name)
  }

  @Test
  fun aDamagedFileKeepsWhatIsValid() {
    val stored = Gson().fromJson(
      """{"trips":[null,{"id":1,"list_id":3,"list_name":" Semana ","items":[
          {"item_id":1,"name":" Pan ","at":5,"price_cents":-2},{"item_id":2,"at":6},null]},
        {"id":2,"list_id":3,"items":[]}],
        "open":[{"id":9,"list_id":4,"list_name":"Fiesta","items":[{"item_id":1,"name":"Hielo","at":9}]}]}""",
      PurchaseHistory::class.java
    ).sanitized()

    val trip = stored.trips.single()
    assertEquals("Semana", trip.listName)
    assertEquals(listOf("Pan"), trip.items.map { it.name })
    assertEquals(null, trip.items.single().priceCents)
    assertEquals(listOf(9L), stored.open.map { it.id })
    assertEquals(1, stored.version)
  }

  @Test
  fun aRepeatedListKeepsItsNameUnlessItIsTaken() {
    assertEquals("Compra semanal", repeatListName(" Compra  semanal ", listOf("Fiesta"), "23 nov"))
    assertEquals("Compra semanal · 23 nov", repeatListName("Compra semanal", listOf("COMPRA SEMANAL"), "23 nov"))
  }
}
