package ourmeal.app.model

import com.google.gson.annotations.SerializedName

data class ListItem(
  @SerializedName("id")
  val id: Long,

  @SerializedName("name")
  var name: String,

  @SerializedName("is_completed")
  var isCompleted: Boolean = false,

  @SerializedName("created_at")
  val createdAt: Long,

  @SerializedName("updated_at")
  var updatedAt: Long = System.currentTimeMillis(),

  @SerializedName("notes")
  var notes: String? = null
)
