package ourmeal.app.model

import com.google.gson.annotations.SerializedName
import kotlin.math.abs
import kotlin.math.max

const val SHOPPING_ROUTE_VERSION = 1
const val ROUTE_STOP_CAP = 500
const val ROUTE_TRIP_TICK_CAP = 200
/** Ticks further apart than this belong to different shopping trips. */
const val ROUTE_TRIP_GAP_MS = 45 * 60 * 1000L
/** A shorter trip is products marked at home after shopping, not a walk through the shop. */
const val ROUTE_MIN_TRIP_MS = 2 * 60 * 1000L
const val ROUTE_MIN_TRIP_STOPS = 3
/** Lowest weight of a new trip, so the route follows a change of shop within a few trips. */
const val ROUTE_LEARNING_FLOOR = 0.3

data class RouteStop(
  @SerializedName("key") val key: String,
  /** 0 where the walk starts and 1 where it ends. */
  @SerializedName("position") val position: Double,
  @SerializedName("trips") val trips: Int,
  @SerializedName("updated_at") val updatedAt: Long
)

data class RouteTick(
  @SerializedName("key") val key: String,
  @SerializedName("at") val at: Long
)

/** A route after a change, with how many products the closed trip taught, if any. */
data class RouteChange(val route: ShoppingRoute, val learned: Int = 0)

/**
 * The order in which the user picks products up, learned from the order they mark them as
 * bought. The open [trip] is folded into [stops] once it is over.
 */
data class ShoppingRoute(
  @SerializedName("version") val version: Int = SHOPPING_ROUTE_VERSION,
  /** Stored inverted so that a file without the field, read by Gson as false, sorts by route. */
  @SerializedName("list_order") val keepListOrder: Boolean = false,
  @SerializedName("stops") val stops: List<RouteStop> = emptyList(),
  @SerializedName("trip") val trip: List<RouteTick> = emptyList()
) {
  val sortByRoute: Boolean get() = !keepListOrder
  val hasStops: Boolean get() = stops.isNotEmpty()

  fun positions(): Map<String, Double> = stops.associate { it.key to it.position }

  /** Records a product picked up; a tick long after the previous one starts a new trip. */
  fun tick(key: String, at: Long): RouteChange {
    if (key.isEmpty()) return RouteChange(this)
    val settled = settle(at)
    val ticks = settled.route.trip.filterNot { it.key == key } + RouteTick(key, at)
    return settled.copy(route = settled.route.copy(trip = ticks.takeLast(ROUTE_TRIP_TICK_CAP)))
  }

  /** A product put back is not part of the walk; what earlier trips taught is kept. */
  fun untick(key: String, at: Long): RouteChange {
    val settled = settle(at)
    if (settled.route.trip.none { it.key == key }) return settled
    return settled.copy(route = settled.route.copy(trip = settled.route.trip.filterNot { it.key == key }))
  }

  /** Folds the open trip into the route once [now] is past the gap that ends it. */
  fun settle(now: Long): RouteChange {
    val last = trip.lastOrNull() ?: return RouteChange(this)
    if (abs(now - last.at) <= ROUTE_TRIP_GAP_MS) return RouteChange(this)
    val learned = learnedFrom(trip)
    val learnedKeys = learned.mapTo(HashSet()) { it.key }
    val merged = (learned + stops.filterNot { it.key in learnedKeys })
      .sortedByDescending { it.updatedAt }
      .take(ROUTE_STOP_CAP)
    return RouteChange(copy(stops = merged, trip = emptyList()), learned.size)
  }

  /** Forgets what was learned, for example after changing shop; the sort choice stays. */
  fun forget(): ShoppingRoute = copy(stops = emptyList(), trip = emptyList())

  /**
   * Each product of the trip is observed at its rank, from 0 to 1, and moves its stored position
   * toward it: the first trips weigh the same and later ones never less than the floor.
   */
  private fun learnedFrom(ticks: List<RouteTick>): List<RouteStop> {
    if (ticks.size < ROUTE_MIN_TRIP_STOPS) return emptyList()
    if (ticks.last().at - ticks.first().at < ROUTE_MIN_TRIP_MS) return emptyList()
    val known = stops.associateBy { it.key }
    return ticks.mapIndexed { index, tick ->
      val observed = index.toDouble() / ticks.lastIndex
      val stop = known[tick.key]
      if (stop == null) {
        RouteStop(tick.key, observed, trips = 1, updatedAt = tick.at)
      } else {
        val weight = max(1.0 / (stop.trips + 1), ROUTE_LEARNING_FLOOR)
        RouteStop(
          key = tick.key,
          position = stop.position + (observed - stop.position) * weight,
          trips = stop.trips + 1,
          updatedAt = tick.at
        )
      }
    }
  }

  /** Gson skips the Kotlin constructor, so a missing or null field arrives as null here. */
  fun sanitized(): ShoppingRoute {
    val storedStops: List<RouteStop?>? = stops
    val storedTrip: List<RouteTick?>? = trip
    val validStops = storedStops.orEmpty().mapNotNull { stop ->
      val key: String? = stop?.key
      if (stop == null || key.isNullOrBlank() || stop.trips < 1 || stop.position.isNaN()) null
      else stop.copy(position = stop.position.coerceIn(0.0, 1.0))
    }
    val validTicks = storedTrip.orEmpty().filterNotNull().filter { tick ->
      val key: String? = tick.key
      !key.isNullOrBlank()
    }
    return ShoppingRoute(
      version = if (version > 0) version else SHOPPING_ROUTE_VERSION,
      keepListOrder = keepListOrder,
      stops = validStops.sortedByDescending { it.updatedAt }.distinctBy { it.key }.take(ROUTE_STOP_CAP),
      // Like tick(): only the latest tick of each product counts, in walking order.
      trip = validTicks.sortedByDescending { it.at }.distinctBy { it.key }.asReversed()
        .takeLast(ROUTE_TRIP_TICK_CAP)
    )
  }
}

/** How many pending products the route already knows where to place. */
data class RoutePlacement(val placed: Int, val pending: Int)

/**
 * Pending products in the order of the route, then the ones it cannot place yet in list order,
 * then the bought ones, also in list order.
 */
fun List<ListItem>.inRouteOrder(route: ShoppingRoute): List<ListItem> {
  val positions = route.positions()
  val (bought, pending) = partition { it.isCompleted }
  val (placed, unplaced) = pending.partition { matchKey(it.name) in positions }
  return placed.sortedBy { positions.getValue(matchKey(it.name)) } + unplaced + bought
}

fun List<ListItem>.routePlacement(route: ShoppingRoute): RoutePlacement {
  val positions = route.positions()
  val pending = filterNot { it.isCompleted }
  return RoutePlacement(placed = pending.count { matchKey(it.name) in positions }, pending = pending.size)
}
