package ourmeal.app.model

import com.google.gson.annotations.SerializedName

data class AppData(
  @SerializedName("lists")
  val lists: MutableList<UserList>,

  @SerializedName("version")
  val version: Int = 1,

  @SerializedName("last_backup")
  val lastBackup: Long = System.currentTimeMillis()
)
