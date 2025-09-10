package ourmeal.app.controller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import app.ourmeat.R
import com.google.android.material.card.MaterialCardView
import ourmeal.app.model.UserList
import java.text.SimpleDateFormat
import java.util.*

class ListAdapter(
  private val lists: MutableList<UserList>,
  private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<ListAdapter.ListViewHolder>() {

  inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val cardView: MaterialCardView = itemView.findViewById(R.id.cardList)
    val tvListName: TextView = itemView.findViewById(R.id.tvListName)
    val tvItemCount: TextView = itemView.findViewById(R.id.tvItemCount)
    val tvCreatedDate: TextView = itemView.findViewById(R.id.tvCreatedDate)
    val progressBar: ProgressBar = itemView.findViewById(R.id.progressCompletion)
    val tvProgress: TextView = itemView.findViewById(R.id.tvProgress)

    init {
      cardView.setOnClickListener {
        onItemClick(adapterPosition)
      }
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
    val view = LayoutInflater.from(parent.context)
      .inflate(R.layout.item_list, parent, false)
    return ListViewHolder(view)
  }

  override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
    val list = lists[position]

    // Nombre de la lista
    holder.tvListName.text = list.name

    // Contador de items
    val totalItems = list.getTotalCount()
    val completedItems = list.getCompletedCount()
    holder.tvItemCount.text = "$totalItems items"

    // Fecha de creación
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    holder.tvCreatedDate.text = dateFormat.format(Date(list.createdAt))

    // Progress bar
    val progress = list.getCompletionPercentage()
    holder.progressBar.progress = progress
    holder.tvProgress.text = "$completedItems/$totalItems ($progress%)"

    // Color del progress bar según el progreso
    val progressColor = when {
      progress == 100 -> android.graphics.Color.parseColor("#4CAF50") // Verde
      progress >= 50 -> android.graphics.Color.parseColor("#fa9154") // Naranja
      else -> android.graphics.Color.parseColor("#757575") // Gris
    }
    holder.progressBar.progressDrawable.setTint(progressColor)

    // Animación suave al hacer clic
    holder.cardView.setOnClickListener {
      it.animate()
        .scaleX(0.95f)
        .scaleY(0.95f)
        .setDuration(100)
        .withEndAction {
          it.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(100)
            .withEndAction {
              onItemClick(position)
            }
        }
    }
  }

  override fun getItemCount(): Int = lists.size

  /**
   * Actualiza los datos del adapter
   */
  fun updateData(newLists: List<UserList>) {
    lists.clear()
    lists.addAll(newLists)
    notifyDataSetChanged()
  }

  /**
   * Añade una nueva lista
   */
  fun addList(list: UserList) {
    lists.add(0, list)
    notifyItemInserted(0)
  }

  /**
   * Elimina una lista
   */
  fun removeList(position: Int) {
    if (position in 0 until lists.size) {
      lists.removeAt(position)
      notifyItemRemoved(position)
    }
  }
}