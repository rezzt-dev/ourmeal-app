package ourmeal.app

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ourmeal.app.model.ListItem
import ourmeal.app.model.ROUTE_MIN_TRIP_MS
import ourmeal.app.model.ROUTE_TRIP_GAP_MS
import ourmeal.app.model.RoutePlacement
import ourmeal.app.model.RouteStop
import ourmeal.app.model.SHOPPING_ROUTE_VERSION
import ourmeal.app.model.ShoppingRoute
import ourmeal.app.model.inRouteOrder
import ourmeal.app.model.routePlacement

class ShoppingRouteTest {
  private val gson = Gson()
  private val minute = 60_000L

  /** Ticks [keys] one minute apart starting at [start], as a walk through the shop. */
  private fun ShoppingRoute.walk(start: Long, vararg keys: String): ShoppingRoute =
    keys.foldIndexed(this) { index, route, key -> route.tick(key, start + index * minute).route }

  private fun ShoppingRoute.closed(after: Long): ShoppingRoute = settle(after + ROUTE_TRIP_GAP_MS + 1).route

  private fun item(id: Long, name: String, bought: Boolean = false) =
    ListItem(id, name, isCompleted = bought, createdAt = id)

  @Test
  fun tickAppendsAndMovesARepeatedProductToTheEnd() {
    val route = ShoppingRoute().walk(0L, "pan", "leche", "pan")
    assertEquals(listOf("leche", "pan"), route.trip.map { it.key })
  }

  @Test
  fun untickRemovesTheProductFromTheOpenTripOnly() {
    val learned = ShoppingRoute().walk(0L, "pan", "leche", "huevos").closed(after = 2 * minute)
    val route = learned.walk(10 * ROUTE_TRIP_GAP_MS, "pan", "leche").untick("pan", 10 * ROUTE_TRIP_GAP_MS + 2 * minute).route
    assertEquals(listOf("leche"), route.trip.map { it.key })
    assertEquals(setOf("pan", "leche", "huevos"), route.positions().keys)
  }

  @Test
  fun aTripIsLearnedOnceTheGapHasPassed() {
    val open = ShoppingRoute().walk(0L, "fruta", "pan", "leche")
    val within = open.settle(2 * minute + ROUTE_TRIP_GAP_MS)
    assertEquals(0, within.learned)
    assertEquals(3, within.route.trip.size)

    val change = open.settle(2 * minute + ROUTE_TRIP_GAP_MS + 1)
    assertEquals(3, change.learned)
    assertTrue(change.route.trip.isEmpty())
    assertEquals(mapOf("fruta" to 0.0, "pan" to 0.5, "leche" to 1.0), change.route.positions())
  }

  @Test
  fun aTickAfterTheGapClosesThePreviousTripAndStartsANewOne() {
    val route = ShoppingRoute().walk(0L, "fruta", "pan", "leche").tick("agua", 3 * minute + ROUTE_TRIP_GAP_MS)
    assertEquals(3, route.learned)
    assertEquals(listOf("agua"), route.route.trip.map { it.key })
  }

  @Test
  fun productsMarkedAtHomeInAFewSecondsAreNotLearned() {
    val quick = listOf("fruta", "pan", "leche").foldIndexed(ShoppingRoute()) { index, route, key ->
      route.tick(key, index * 1_000L).route
    }
    val change = quick.settle(ROUTE_TRIP_GAP_MS * 2)
    assertEquals(0, change.learned)
    assertFalse(change.route.hasStops)
    assertTrue(change.route.trip.isEmpty())
    assertTrue(ROUTE_MIN_TRIP_MS > 2_000L)
  }

  @Test
  fun tripsWithFewerThanThreeProductsAreNotLearned() {
    val change = ShoppingRoute().walk(0L, "pan", "leche").settle(ROUTE_TRIP_GAP_MS * 2)
    assertEquals(0, change.learned)
    assertFalse(change.route.hasStops)
  }

  @Test
  fun laterTripsMoveTheStoredPositionTowardTheNewObservation() {
    val first = ShoppingRoute().walk(0L, "leche", "fruta", "pan").closed(after = 2 * minute)
    assertEquals(0.0, first.positions().getValue("leche"), 1e-9)
    // Second trip: leche at the end. Two trips weigh the same: halfway.
    val second = first.walk(ROUTE_TRIP_GAP_MS * 4, "fruta", "pan", "leche").closed(after = ROUTE_TRIP_GAP_MS * 4 + 2 * minute)
    assertEquals(0.5, second.positions().getValue("leche"), 1e-9)
    assertEquals(2, second.stops.first { it.key == "leche" }.trips)
  }

  @Test
  fun anEstablishedRouteStillFollowsANewShopAtTheFloorWeight() {
    val settled = ShoppingRoute(stops = listOf(RouteStop("leche", 0.0, trips = 20, updatedAt = 0L)))
    val next = settled.walk(minute, "fruta", "pan", "leche").closed(after = 3 * minute)
    assertEquals(0.3, next.positions().getValue("leche"), 1e-9)
  }

  @Test
  fun forgetClearsTheRouteButKeepsTheSortChoice() {
    val route = ShoppingRoute(keepListOrder = true).walk(0L, "fruta", "pan", "leche").closed(after = 2 * minute)
      .walk(ROUTE_TRIP_GAP_MS * 4, "agua")
    val forgotten = route.forget()
    assertFalse(forgotten.hasStops)
    assertTrue(forgotten.trip.isEmpty())
    assertFalse(forgotten.sortByRoute)
  }

  @Test
  fun pendingProductsFollowTheRouteThenTheUnknownOnesThenTheBoughtOnes() {
    val route = ShoppingRoute(stops = listOf(
      RouteStop("platano", 0.1, 1, 0L),
      RouteStop("leche", 0.9, 1, 0L),
      RouteStop("pan", 0.5, 1, 0L)
    ))
    val items = listOf(
      item(1, "Leche"),
      item(2, "Detergente"),
      item(3, "Pan", bought = true),
      item(4, "Plátano"),
      item(5, "Sal")
    )
    assertEquals(
      listOf("Plátano", "Leche", "Detergente", "Sal", "Pan"),
      items.inRouteOrder(route).map { it.name }
    )
    assertEquals(RoutePlacement(placed = 2, pending = 4), items.routePlacement(route))
  }

  @Test
  fun anEmptyRouteKeepsListOrderAndMovesBoughtProductsDown() {
    val items = listOf(item(1, "Pan", bought = true), item(2, "Leche"), item(3, "Sal"))
    assertEquals(listOf("Leche", "Sal", "Pan"), items.inRouteOrder(ShoppingRoute()).map { it.name })
  }

  @Test
  fun sanitizedRepairsMissingFieldsAndDropsInvalidEntries() {
    val json = """
      {"stops":[{"key":"pan","position":1.4,"trips":2,"updated_at":5},{"key":"","position":0.2,"trips":1},
       {"key":"sal","position":0.2},null,{"key":"pan","position":0.1,"trips":1,"updated_at":1}],
       "trip":[{"key":"agua","at":9},{"at":3},{"key":"agua","at":4},{"key":"te","at":5}]}
    """.trimIndent()
    val route = gson.fromJson(json, ShoppingRoute::class.java).sanitized()
    assertEquals(SHOPPING_ROUTE_VERSION, route.version)
    assertEquals(listOf(RouteStop("pan", 1.0, 2, 5L)), route.stops)
    assertEquals(listOf("te", "agua"), route.trip.map { it.key })
  }

  @Test
  fun sanitizedHandlesAnEmptyObject() {
    val route = gson.fromJson("{}", ShoppingRoute::class.java).sanitized()
    assertTrue(route.sortByRoute)
    assertFalse(route.hasStops)
    assertTrue(route.trip.isEmpty())
  }

  @Test
  fun roundTripsThroughGson() {
    val route = ShoppingRoute(keepListOrder = true).walk(0L, "fruta", "pan", "leche")
    assertEquals(route, gson.fromJson(gson.toJson(route), ShoppingRoute::class.java).sanitized())
  }
}
