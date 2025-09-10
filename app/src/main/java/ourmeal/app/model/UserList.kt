package ourmeal.app.model

import com.google.gson.annotations.SerializedName


data class UserList(
  @SerializedName("id")
  val id: Long,

  @SerializedName("name")
  var name: String,

  @SerializedName("items")
  val items: MutableList<ListItem>,

  @SerializedName("created_at")
  val createdAt: Long,

  @SerializedName("updated_at")
  var updatedAt: Long = System.currentTimeMillis()
) {
  // Método para obtener el número de items completados
  fun getCompletedCount(): Int = items.count { it.isCompleted }

  // Método para obtener el total de items
  fun getTotalCount(): Int = items.size

  // Método para obtener el porcentaje de completado
  fun getCompletionPercentage(): Int {
    return if (items.isEmpty()) 0 else (getCompletedCount() * 100) / getTotalCount()
  }
}
