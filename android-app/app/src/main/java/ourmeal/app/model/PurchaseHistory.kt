package ourmeal.app.model

import com.google.gson.annotations.SerializedName
import kotlin.math.abs

const val PURCHASE_HISTORY_VERSION = 1
const val PURCHASE_TRIP_CAP = 100
const val PURCHASE_ITEM_CAP = 200
/** Same rule as the shopping route: marks further apart than this belong to different trips. */
const val PURCHASE_TRIP_GAP_MS = ROUTE_TRIP_GAP_MS

/** A product marked as bought during a trip, as it was named and priced at that moment. */
data class PurchasedItem(
  @SerializedName("item_id") val itemId: Long,
  @SerializedName("name") val name: String,
  @SerializedName("price_cents") val priceCents: Long? = null,
  @SerializedName("at") val at: Long
)

/** One visit to the shop with one list: the products marked, in the order they were marked. */
data class PurchaseTrip(
  @SerializedName("id") val id: Long,
  @SerializedName("list_id") val listId: Long,
  @SerializedName("list_name") val listName: String,
  @SerializedName("items") val items: List<PurchasedItem>
) {
  val startedAt: Long get() = items.minOfOrNull { it.at } ?: id
  val endedAt: Long get() = items.maxOfOrNull { it.at } ?: id
  val durationMs: Long get() = endedAt - startedAt

  fun spend(): PriceEstimate = estimateOf(items.map { it.priceCents })
}

/**
 * Every trip to the shop, kept after its list is deleted or reused. Unlike the shopping route,
 * a short trip still counts: products marked at home after shopping were bought all the same.
 * Bulk changes (uncheck all, remove bought) are not trips and never reach the history.
 */
data class PurchaseHistory(
  @SerializedName("version") val version: Int = PURCHASE_HISTORY_VERSION,
  /** Finished trips, newest first. */
  @SerializedName("trips") val trips: List<PurchaseTrip> = emptyList(),
  /** Trips still going on, at most one per list. */
  @SerializedName("open") val open: List<PurchaseTrip> = emptyList()
) {
  val isEmpty: Boolean get() = trips.isEmpty() && open.isEmpty()

  /** Trips still going on first, then the finished ones; newest first in each group. */
  fun timeline(): List<PurchaseTrip> = open.sortedByDescending { it.endedAt } + trips

  fun isOpen(tripId: Long): Boolean = open.any { it.id == tripId }

  /** Records [item] as bought from [listId]; marking it again moves it to the end of the trip. */
  fun mark(listId: Long, listName: String, item: PurchasedItem): PurchaseHistory {
    val settled = settle(item.at)
    val current = settled.open.firstOrNull { it.listId == listId }
    val entry = item.copy(name = normalizeName(item.name), priceCents = validPrice(item.priceCents))
    val items = (current?.items.orEmpty().filterNot { it.itemId == item.itemId } + entry).takeLast(PURCHASE_ITEM_CAP)
    val trip = PurchaseTrip(current?.id ?: settled.newTripId(item.at), listId, normalizeName(listName), items)
    return settled.copy(open = settled.open.filterNot { it.listId == listId } + trip)
  }

  /** A product put back is not part of the trip; a trip left without products disappears. */
  fun unmark(listId: Long, itemId: Long, at: Long): PurchaseHistory {
    val settled = settle(at)
    val current = settled.open.firstOrNull { it.listId == listId } ?: return settled
    if (current.items.none { it.itemId == itemId }) return settled
    val items = current.items.filterNot { it.itemId == itemId }
    val others = settled.open.filterNot { it.listId == listId }
    return settled.copy(open = if (items.isEmpty()) others else others + current.copy(items = items))
  }

  /** A price written while the trip is going on is what the product cost on that trip. */
  fun withPrice(listId: Long, itemId: Long, cents: Long?): PurchaseHistory {
    val current = open.firstOrNull { it.listId == listId } ?: return this
    if (current.items.none { it.itemId == itemId }) return this
    val updated = current.copy(items = current.items.map { item ->
      if (item.itemId == itemId) item.copy(priceCents = validPrice(cents)) else item
    })
    return copy(open = open.map { if (it.listId == listId) updated else it })
  }

  /** Closes the trips whose last mark is further from [now] than the gap that ends a trip. */
  fun settle(now: Long): PurchaseHistory {
    val (ended, going) = open.partition { abs(now - it.endedAt) > PURCHASE_TRIP_GAP_MS }
    if (ended.isEmpty()) return this
    return copy(trips = (ended + trips).sortedByDescending { it.endedAt }.take(PURCHASE_TRIP_CAP), open = going)
  }

  fun without(tripId: Long): PurchaseHistory =
    copy(trips = trips.filterNot { it.id == tripId }, open = open.filterNot { it.id == tripId })

  /** Puts a removed trip back among the finished ones; it is skipped if its id is in use again. */
  fun withRestored(trip: PurchaseTrip): PurchaseHistory {
    if ((trips + open).any { it.id == trip.id }) return this
    return copy(trips = (trips + trip).sortedByDescending { it.endedAt }.take(PURCHASE_TRIP_CAP))
  }

  private fun newTripId(at: Long): Long =
    generateSequence(at) { it + 1 }.first { candidate -> (trips + open).none { it.id == candidate } }

  /** Gson skips the Kotlin constructor, so a missing or null field arrives as null here. */
  fun sanitized(): PurchaseHistory {
    val storedTrips: List<PurchaseTrip?>? = trips
    val storedOpen: List<PurchaseTrip?>? = open
    val validOpen = storedOpen.orEmpty().mapNotNull { it?.sanitized() }
      .sortedByDescending { it.endedAt }.distinctBy { it.listId }
    val openIds = validOpen.mapTo(HashSet()) { it.id }
    val validTrips = storedTrips.orEmpty().mapNotNull { it?.sanitized() }.filterNot { it.id in openIds }
    return PurchaseHistory(
      version = if (version > 0) version else PURCHASE_HISTORY_VERSION,
      trips = validTrips.distinctBy { it.id }.sortedByDescending { it.endedAt }.take(PURCHASE_TRIP_CAP),
      open = validOpen
    )
  }

  private fun PurchaseTrip.sanitized(): PurchaseTrip? {
    val storedName: String? = listName
    val storedItems: List<PurchasedItem?>? = items
    val valid = storedItems.orEmpty().mapNotNull { item ->
      val name: String? = item?.name
      if (item == null || name.isNullOrBlank()) null
      else item.copy(name = normalizeName(name), priceCents = validPrice(item.priceCents))
    }.distinctBy { it.itemId }.takeLast(PURCHASE_ITEM_CAP)
    if (valid.isEmpty()) return null
    return PurchaseTrip(id, listId, normalizeName(storedName.orEmpty()), valid)
  }
}

/**
 * Name for a list made again from a past trip: the same name, unless a list already has it,
 * in which case [dateLabel] tells them apart.
 */
fun repeatListName(listName: String, takenNames: Collection<String>, dateLabel: String): String {
  val name = itemName(listName)
  val taken = takenNames.mapTo(HashSet(), ::matchKey)
  return if (matchKey(name) !in taken) name else copyName(name, " · $dateLabel")
}
