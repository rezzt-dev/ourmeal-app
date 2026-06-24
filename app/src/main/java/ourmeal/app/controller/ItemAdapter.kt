package ourmeal.app.controller

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.ourmeat.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import ourmeal.app.model.ListItem
import java.text.SimpleDateFormat
import java.util.*

class ItemAdapter(
  private val items: MutableList<ListItem>,
  private val onItemLongClick: (Int) -> Unit,
  private val onItemCheckedChange: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<ItemAdapter.ItemViewHolder>() {

  inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val cardView: MaterialCardView = itemView.findViewById(R.id.cardItem)
    val checkBox: MaterialCheckBox = itemView.findViewById(R.id.cbItemCompleted)
    val tvItemName: TextView = itemView.findViewById(R.id.tvItemName)
    val tvCreatedDate: TextView = itemView.findViewById(R.id.tvItemDate)

    init {
      // Long click para eliminar item
      cardView.setOnLongClickListener {
        if (adapterPosition != RecyclerView.NO_POSITION) {
          onItemLongClick(adapterPosition)
        }
        true
      }

      // Click en checkbox para marcar/desmarcar
      checkBox.setOnCheckedChangeListener { _, isChecked ->
        val position = adapterPosition
        if (position != RecyclerView.NO_POSITION) {
          items[position].isCompleted = isChecked
          items[position].updatedAt = System.currentTimeMillis()
          updateItemAppearance(this, items[position])

          // Notificar a la Activity/Fragment
          onItemCheckedChange(position, isChecked)
        }
      }
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
    val view = LayoutInflater.from(parent.context)
      .inflate(R.layout.item_list_item, parent, false)
    return ItemViewHolder(view)
  }

  override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
    val item = items[position]

    // Configurar datos
    holder.tvItemName.text = item.name
    holder.checkBox.isChecked = item.isCompleted

    // Fecha de creación
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    holder.tvCreatedDate.text = dateFormat.format(Date(item.createdAt))

    // Actualizar apariencia según estado
    updateItemAppearance(holder, item)

    // Animación al hacer clic
    holder.cardView.setOnClickListener {
      holder.checkBox.isChecked = !holder.checkBox.isChecked
    }
  }

  override fun getItemCount(): Int = items.size

  /**
   * Actualiza la apariencia del item según su estado de completado
   */
  private fun updateItemAppearance(holder: ItemViewHolder, item: ListItem) {
    if (item.isCompleted) {
      holder.tvItemName.paintFlags =
        holder.tvItemName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
      holder.tvItemName.alpha = 0.6f
      holder.tvCreatedDate.alpha = 0.6f
      holder.cardView.alpha = 0.7f
    } else {
      holder.tvItemName.paintFlags =
        holder.tvItemName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
      holder.tvItemName.alpha = 1.0f
      holder.tvCreatedDate.alpha = 0.8f
      holder.cardView.alpha = 1.0f
    }
  }

  /** Añade un nuevo item */
  fun addItem(item: ListItem) {
    items.add(0, item)
    notifyItemInserted(0)
  }

  /** Elimina un item */
  fun removeItem(position: Int) {
    if (position in items.indices) {
      items.removeAt(position)
      notifyItemRemoved(position)
    }
  }

  /** Actualiza un item específico */
  fun updateItem(position: Int, item: ListItem) {
    if (position in items.indices) {
      items[position] = item
      notifyItemChanged(position)
    }
  }

  /** Obtiene todos los items */
  fun getItems(): List<ListItem> = items.toList()

  /** Obtiene solo los items completados */
  fun getCompletedItems(): List<ListItem> = items.filter { it.isCompleted }

  /** Obtiene solo los items pendientes */
  fun getPendingItems(): List<ListItem> = items.filter { !it.isCompleted }
}
