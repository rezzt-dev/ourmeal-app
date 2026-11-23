package ourmeal.app.controller

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.ourmeat.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import ourmeal.app.model.ListItem
import ourmeal.app.model.ShownPrice
import ourmeal.app.model.formatPrice
import ourmeal.app.model.shownPrice

class ItemAdapter(
  private val onCheck: (ListItem, Boolean) -> Unit,
  private val onRename: (ListItem) -> Unit,
  private val onPrice: (ListItem) -> Unit,
  private val onDelete: (ListItem) -> Unit
) : RecyclerView.Adapter<ItemAdapter.Holder>() {
  private val rows = mutableListOf<Row>()
  private var interactionsEnabled = true
  /** Remembered prices by product key, shown for products without a price of their own. */
  private var remembered: Map<String, Long> = emptyMap()

  /** A product as shown, with the price it displays, so DiffUtil also sees price changes. */
  private data class Row(val item: ListItem, val price: ShownPrice?)

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
    val snapshot = newItems.map { item -> Row(item.copy(), item.shownPrice(remembered)) }
    val oldRows = rows.toList()
    val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
      override fun getOldListSize(): Int = oldRows.size
      override fun getNewListSize(): Int = snapshot.size
      override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean =
        oldRows[oldPosition].item.id == snapshot[newPosition].item.id
      override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean =
        oldRows[oldPosition] == snapshot[newPosition]
    })
    rows.clear()
    rows.addAll(snapshot)
    result.dispatchUpdatesTo(this)
  }

  /** Shows [prices] on the products without a price of their own, keeping the current order. */
  fun setRememberedPrices(prices: Map<String, Long>) {
    if (remembered == prices) return
    remembered = prices
    submitList(rows.map { it.item })
  }

  fun setInteractionsEnabled(enabled: Boolean) {
    if (interactionsEnabled == enabled) return
    interactionsEnabled = enabled
    notifyItemRangeChanged(0, rows.size)
  }

  override fun getItemId(position: Int): Long = rows[position].item.id

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
    Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_list_item, parent, false))

  override fun onBindViewHolder(holder: Holder, position: Int) {
    val (item, price) = rows[position]
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
      ContextCompat.getColor(
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
    holder.itemView.setRenameAction(interactionsEnabled) { onRename(item) }
    bindPrice(holder.price, item, price)
    holder.delete.contentDescription = context.getString(R.string.delete_item, item.name)
    holder.delete.setOnClickListener { onDelete(item) }
  }

  /**
   * A product without a price shows only a tag; a remembered price is muted and marked as an
   * approximation, so it never reads as a price written in this list.
   */
  private fun bindPrice(button: MaterialButton, item: ListItem, price: ShownPrice?) {
    val context = button.context
    button.isEnabled = interactionsEnabled
    button.setOnClickListener { onPrice(item) }
    if (price == null) {
      button.text = null
      button.setIconResource(R.drawable.ic_price_tag)
      button.contentDescription = context.getString(R.string.price_add_description, item.name)
      return
    }
    val amount = formatPrice(price.cents)
    button.icon = null
    button.text = if (price.remembered) "\u2248\u00A0$amount" else amount
    button.setTextColor(ContextCompat.getColor(context, if (price.remembered) R.color.muted else R.color.ink))
    button.contentDescription = context.getString(
      if (price.remembered) R.string.price_remembered_description else R.string.price_edit_description,
      item.name,
      amount
    )
  }

  override fun getItemCount(): Int = rows.size
}
