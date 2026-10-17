package ourmeal.app.controller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.ourmeat.R
import ourmeal.app.model.UserList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListAdapter(
  private val onOpen: (UserList) -> Unit,
  private val onDelete: (UserList) -> Unit
) : RecyclerView.Adapter<ListAdapter.Holder>() {
  private val items = mutableListOf<UserList>()
  private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("es-ES"))

  init {
    setHasStableIds(true)
  }

  class Holder(view: View) : RecyclerView.ViewHolder(view) {
    val name: TextView = view.findViewById(R.id.tvListName)
    val date: TextView = view.findViewById(R.id.tvCreatedDate)
    val count: TextView = view.findViewById(R.id.tvItemCount)
    val progress: TextView = view.findViewById(R.id.tvProgress)
    val bar: ProgressBar = view.findViewById(R.id.progressCompletion)
    val delete: ImageButton = view.findViewById(R.id.btnDeleteList)
  }

  fun submitList(newItems: List<UserList>) {
    val oldItems = items.toList()
    val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
      override fun getOldListSize(): Int = oldItems.size
      override fun getNewListSize(): Int = newItems.size
      override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean =
        oldItems[oldPosition].id == newItems[newPosition].id
      override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean =
        oldItems[oldPosition] == newItems[newPosition]
    })
    items.clear()
    items.addAll(newItems)
    result.dispatchUpdatesTo(this)
  }

  override fun getItemId(position: Int): Long = items[position].id

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
    Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_list, parent, false))

  override fun onBindViewHolder(holder: Holder, position: Int) {
    val list = items[position]
    val context = holder.itemView.context
    val total = list.getTotalCount()
    val done = list.getCompletedCount()
    holder.name.text = list.name
    holder.date.text = context.getString(
      R.string.list_created_date,
      dateFormat.format(Date(list.createdAt))
    )
    holder.count.text = context.resources.getQuantityString(R.plurals.item_count, total, total)
    holder.progress.text = if (total == 0) {
      context.getString(R.string.items_empty_label)
    } else {
      context.resources.getQuantityString(R.plurals.list_progress, total, done, total)
    }
    holder.bar.progress = list.getCompletionPercentage()
    holder.bar.contentDescription = context.getString(R.string.progress_completion)
    holder.itemView.setOnClickListener { onOpen(list) }
    holder.delete.contentDescription = context.getString(R.string.delete_list, list.name)
    holder.delete.setOnClickListener { onDelete(list) }
  }

  override fun getItemCount(): Int = items.size
}
