package ourmeal.app.model

import com.google.gson.annotations.SerializedName

const val SUGGESTION_SETTINGS_VERSION = 1
const val HIDDEN_DISH_CAP = 200

data class SuggestionSettings(
  @SerializedName("version") val version: Int = SUGGESTION_SETTINGS_VERSION,
  @SerializedName("hidden") val hidden: List<String> = emptyList()
) {
  val hiddenKeys: Set<String> get() = hidden.toSet()

  /** Idempotent; the newest key goes last and the oldest ones drop past [HIDDEN_DISH_CAP]. */
  fun hide(key: String): SuggestionSettings =
    if (key in hidden) this else copy(hidden = (hidden + key).takeLast(HIDDEN_DISH_CAP))

  fun unhide(keys: Set<String>): SuggestionSettings =
    if (hidden.none { it in keys }) this else copy(hidden = hidden.filterNot { it in keys })

  /** Gson skips the Kotlin constructor, so a missing or null field arrives as null here. */
  fun sanitized(): SuggestionSettings {
    val stored: List<String?>? = hidden
    val keys = stored.orEmpty().filterNotNull().filter { it.isNotBlank() }.distinct()
    return SuggestionSettings(
      version = if (version > 0) version else SUGGESTION_SETTINGS_VERSION,
      hidden = keys.takeLast(HIDDEN_DISH_CAP)
    )
  }
}
