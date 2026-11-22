package ourmeal.app.controller

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.ourmeat.R
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import ourmeal.app.model.ListItem

/** What the price line of a pending product shows; null hides it. */
fun interface PriceLineSource {
  fun lineFor(item: ListItem): PriceLineView?
}

/** Text, colour resource, accessible name and whether tapping the price line opens something. */
data class PriceLineView(val text: String, @ColorRes val color: Int, val description: String, val actionable: Boolean)

class ItemAdapter(
  private val onCheck: (ListItem, Boolean) -> Unit,
  private val onDelete: (ListItem) -> Unit,
  private val prices: PriceLineSource = PriceLineSource { null },
  private val onPriceClick: (ListItem) -> Unit = {}
) : RecyclerView.Adapter<ItemAdapter.Holder>() {
  private val items = mutableListOf<ListItem>()
  private var interactionsEnabled = true

  init {
    setHasStableIds(true)
  }

  class Holder(view: View) : RecyclerView.ViewHolder(view) {
    val check: MaterialCheckBox = view.findViewById(R.id.cbItemCompleted)
    val name: TextView = view.findViewById(R.id.tvItemName)
    val status: TextView = view.findViewById(R.id.tvItemStatus)
    val price: MaterialButton = view.findViewById(R.id.btnItemPrice)
    val delete: ImageButton = view.findViewById(R.id.btnDeleteItem)
  }

  fun submitList(newItems: List<ListItem>) {
    // Keep an immutable snapshot so DiffUtil can detect changes made to the model objects.
    val snapshot = newItems.map { it.copy() }
    val oldItems = items.toList()
    val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
      override fun getOldListSize(): Int = oldItems.size
      override fun getNewListSize(): Int = snapshot.size
      override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean =
        oldItems[oldPosition].id == snapshot[newPosition].id
      override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean =
        oldItems[oldPosition] == snapshot[newPosition]
    })
    items.clear()
    items.addAll(snapshot)
    result.dispatchUpdatesTo(this)
  }

  fun setInteractionsEnabled(enabled: Boolean) {
    if (interactionsEnabled == enabled) return
    interactionsEnabled = enabled
    notifyItemRangeChanged(0, items.size)
  }

  /** Redraws only the price lines, without touching checks, focus or scroll. */
  fun refreshPrices() {
    if (items.isNotEmpty()) notifyItemRangeChanged(0, items.size, PAYLOAD_PRICE)
  }

  override fun getItemId(position: Int): Long = items[position].id

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
    Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_list_item, parent, false))

  override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
    if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_PRICE }) {
      bindPrice(holder, items[position])
    } else {
      onBindViewHolder(holder, position)
    }
  }

  override fun onBindViewHolder(holder: Holder, position: Int) {
    val item = items[position]
    val context = holder.itemView.context
    holder.name.text = item.name
    holder.name.paintFlags = if (item.isCompleted) {
      holder.name.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
    } else {
      holder.name.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
    }
    holder.name.alpha = if (item.isCompleted) 0.55f else 1f
    holder.status.setText(
      if (item.isCompleted) R.string.item_status_bought else R.string.item_status_pending
    )
    holder.status.setTextColor(
      androidx.core.content.ContextCompat.getColor(
        context,
        if (item.isCompleted) R.color.sage else R.color.muted
      )
    )
    holder.check.setOnCheckedChangeListener(null)
    holder.check.isChecked = item.isCompleted
    holder.check.isEnabled = interactionsEnabled
    holder.delete.isEnabled = interactionsEnabled
    holder.itemView.isEnabled = interactionsEnabled
    holder.check.contentDescription = context.getString(
      if (item.isCompleted) R.string.mark_pending else R.string.mark_bought,
      item.name
    )
    holder.check.setOnCheckedChangeListener { _, checked -> onCheck(item, checked) }
    holder.itemView.setOnClickListener {
      if (interactionsEnabled) holder.check.toggle()
    }
    holder.delete.contentDescription = context.getString(R.string.delete_item, item.name)
    holder.delete.setOnClickListener { onDelete(item) }
    bindPrice(holder, item)
  }

  private fun bindPrice(holder: Holder, item: ListItem) {
    val line = if (item.isCompleted) null else prices.lineFor(item)
    holder.price.isVisible = line != null
    // The checkbox already says the product is pending: the price line takes the status place.
    holder.status.isVisible = line == null
    if (line == null) {
      holder.price.setOnClickListener(null)
      return
    }
    holder.price.text = line.text
    holder.price.setTextColor(ContextCompat.getColor(holder.itemView.context, line.color))
    holder.price.contentDescription = line.description
    // A line that only reports progress is read, not offered as a button.
    holder.price.isClickable = line.actionable
    holder.price.isFocusable = line.actionable
    holder.price.isEnabled = interactionsEnabled || !line.actionable
    holder.price.setOnClickListener(if (line.actionable) View.OnClickListener { onPriceClick(item) } else null)
  }

  override fun getItemCount(): Int = items.size

  private companion object {
    const val PAYLOAD_PRICE = "price"
  }
}
