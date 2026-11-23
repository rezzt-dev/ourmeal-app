package ourmeal.app.controller

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.ourmeat.R
import com.google.android.material.button.MaterialButton
import ourmeal.app.model.PurchaseTrip
import ourmeal.app.model.formatPrice
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A trip as shown: still going on or finished, whether its list still exists and whether it is open. */
data class TripRow(
  val trip: PurchaseTrip,
  val inProgress: Boolean,
  val listDeleted: Boolean,
  val expanded: Boolean
)

class PurchaseTripAdapter(
  private val onRepeat: (PurchaseTrip) -> Unit,
  private val onDelete: (PurchaseTrip) -> Unit,
  private val onToggle: (PurchaseTrip) -> Unit
) : RecyclerView.Adapter<PurchaseTripAdapter.Holder>() {
  private val rows = mutableListOf<TripRow>()
  private var interactionsEnabled = true

  init {
    setHasStableIds(true)
  }

  class Holder(view: View) : RecyclerView.ViewHolder(view) {
    val status: TextView = view.findViewById(R.id.tvTripStatus)
    val date: TextView = view.findViewById(R.id.tvTripDate)
    val meta: TextView = view.findViewById(R.id.tvTripMeta)
    val spend: TextView = view.findViewById(R.id.tvTripSpend)
    val delete: ImageButton = view.findViewById(R.id.btnDeleteTrip)
    val preview: TextView = view.findViewById(R.id.tvTripPreview)
    val products: TextView = view.findViewById(R.id.tvTripProducts)
    val repeat: MaterialButton = view.findViewById(R.id.btnRepeatTrip)
    val hint: TextView = view.findViewById(R.id.tvTripHint)
    val toggle: ImageButton = view.findViewById(R.id.btnToggleProducts)
  }

  fun submitList(newRows: List<TripRow>) {
    val oldRows = rows.toList()
    val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
      override fun getOldListSize(): Int = oldRows.size
      override fun getNewListSize(): Int = newRows.size
      override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean =
        oldRows[oldPosition].trip.id == newRows[newPosition].trip.id
      override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean =
        oldRows[oldPosition] == newRows[newPosition]
    })
    rows.clear()
    rows.addAll(newRows)
    result.dispatchUpdatesTo(this)
  }

  fun setInteractionsEnabled(enabled: Boolean) {
    if (interactionsEnabled == enabled) return
    interactionsEnabled = enabled
    notifyItemRangeChanged(0, rows.size)
  }

  override fun getItemId(position: Int): Long = rows[position].trip.id

  override fun getItemCount(): Int = rows.size

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
    Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_purchase_trip, parent, false))

  override fun onBindViewHolder(holder: Holder, position: Int) {
    val row = rows[position]
    val trip = row.trip
    val context = holder.itemView.context
    val text = PurchaseText(context)
    val listName = text.listName(trip)

    holder.status.isVisible = row.inProgress
    holder.date.text = text.date(trip.startedAt)
    holder.meta.text = text.meta(trip, row.listDeleted)
    holder.spend.text = text.spend(trip)
    holder.spend.isVisible = holder.spend.text.isNotEmpty()
    holder.preview.text = text.preview(trip)
    holder.preview.isVisible = !row.expanded
    holder.products.text = text.products(trip)
    holder.products.isVisible = row.expanded

    holder.repeat.isVisible = !row.inProgress
    holder.repeat.isEnabled = interactionsEnabled
    holder.repeat.contentDescription = context.getString(R.string.purchase_repeat_description, listName)
    holder.repeat.setOnClickListener { if (interactionsEnabled) onRepeat(trip) }
    holder.hint.isVisible = row.inProgress

    // A trip going on is still being written; it can be removed once it is over.
    holder.delete.isVisible = !row.inProgress
    holder.delete.isEnabled = interactionsEnabled
    holder.delete.contentDescription = context.getString(R.string.purchase_delete_description, listName)
    holder.delete.setOnClickListener { if (interactionsEnabled) onDelete(trip) }

    holder.toggle.rotation = if (row.expanded) -90f else 90f
    holder.toggle.contentDescription =
      context.getString(if (row.expanded) R.string.purchase_hide_products else R.string.purchase_show_products)
    holder.toggle.stateDescription =
      context.getString(if (row.expanded) R.string.purchase_expanded else R.string.purchase_collapsed)
    holder.toggle.setOnClickListener { onToggle(trip) }
  }
}

/** Texts that describe a trip, shared by the cards and the screen's confirmations. */
internal class PurchaseText(private val context: Context) {
  private val zone = ZoneId.systemDefault()

  fun listName(trip: PurchaseTrip): String =
    trip.listName.ifBlank { context.getString(R.string.purchase_unnamed_list) }

  /** "Hoy, 18:40", "Ayer, 9:05", "Sábado 22 de noviembre, 18:40" or, another year, with the year. */
  fun date(millis: Long, today: LocalDate = LocalDate.now(zone)): String {
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val time = at.format(timeFormat)
    val day = at.toLocalDate()
    return when (day) {
      today -> context.getString(R.string.purchase_today, time)
      today.minusDays(1) -> context.getString(R.string.purchase_yesterday, time)
      else -> {
        val format = if (day.year == today.year) dayFormat else dayYearFormat
        context.getString(R.string.purchase_date, day.format(format).capitalized(), time)
      }
    }
  }

  /** "22 de noviembre", for sentences about a trip. */
  fun day(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(shortDayFormat)

  /** "23 nov", to tell a repeated list from the one it was made from. */
  fun dateLabel(date: LocalDate): String = date.format(labelFormat).trimEnd('.')

  fun meta(trip: PurchaseTrip, listDeleted: Boolean): String {
    val name = listName(trip)
    val parts = buildList {
      add(if (listDeleted) context.getString(R.string.purchase_list_deleted, name) else name)
      add(context.resources.getQuantityString(R.plurals.item_count, trip.items.size, trip.items.size))
      duration(trip.durationMs)?.let(::add)
    }
    return parts.reduce { acc, part -> context.getString(R.string.purchase_meta, acc, part) }
  }

  /** Empty when no product of the trip had a price. */
  fun spend(trip: PurchaseTrip): String {
    val spend = trip.spend()
    if (!spend.hasPrices) return ""
    val total = formatPrice(spend.totalCents)
    return if (spend.unpriced == 0) context.getString(R.string.purchase_spend, total)
    else context.resources.getQuantityString(R.plurals.purchase_spend_partial, spend.unpriced, total, spend.unpriced)
  }

  /** The first products, in the order they went into the basket, and how many more there are. */
  fun preview(trip: PurchaseTrip): String {
    val names = trip.items.map { it.name }
    val shown = names.take(PREVIEW_SIZE).joinToString(", ")
    val more = names.size - PREVIEW_SIZE
    return if (more <= 0) shown else context.resources.getQuantityString(R.plurals.purchase_preview, more, shown, more)
  }

  fun products(trip: PurchaseTrip): String = trip.items.joinToString("\n") { item ->
    val price = item.priceCents?.let(::formatPrice)
    if (price == null) item.name else context.getString(R.string.purchase_item_line, item.name, price)
  }

  /** Nothing under a minute: products marked in one go say nothing about the walk. */
  private fun duration(millis: Long): String? {
    val minutes = millis / 60_000
    return when {
      minutes < 1 -> null
      minutes < 60 -> context.getString(R.string.purchase_minutes, minutes.toInt())
      else -> context.getString(R.string.purchase_hours, (minutes / 60).toInt(), (minutes % 60).toInt())
    }
  }

  private fun String.capitalized(): String = replaceFirstChar { it.titlecase(spanish) }

  private companion object {
    const val PREVIEW_SIZE = 3
    val spanish: Locale = Locale.forLanguageTag("es-ES")
    val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", spanish)
    val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", spanish)
    val dayYearFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", spanish)
    val shortDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", spanish)
    val labelFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", spanish)
  }
}
