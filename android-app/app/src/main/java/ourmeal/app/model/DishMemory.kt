package ourmeal.app.model

import com.google.gson.annotations.SerializedName

const val DISH_MEMORY_VERSION = 1
const val DISH_MEMORY_CAP = 300
const val DISH_INGREDIENT_CAP = 30

data class DishIngredients(
  @SerializedName("key") val key: String,
  @SerializedName("name") val name: String,
  @SerializedName("ingredients") val ingredients: List<String>,
  @SerializedName("updated_at") val updatedAt: Long
)

data class DishMemory(
  @SerializedName("version") val version: Int = DISH_MEMORY_VERSION,
  @SerializedName("dishes") val dishes: List<DishIngredients> = emptyList()
) {
  /** Replaces or removes the given dishes and keeps only the most recent [cap] entries. */
  fun upsert(changes: List<DishIngredients>, cap: Int = DISH_MEMORY_CAP): DishMemory {
    val changed = changes.associateBy { it.key }
    val kept = dishes.filterNot { it.key in changed }
    val written = changed.values.filter { it.ingredients.isNotEmpty() }
    return copy(dishes = (written + kept).sortedByDescending { it.updatedAt }.take(cap))
  }

  fun ingredientsFor(key: String): List<String>? = dishes.firstOrNull { it.key == key }?.ingredients

  /** Gson skips the Kotlin constructor, so a missing or null field arrives as null here. */
  fun sanitized(): DishMemory {
    val stored: List<DishIngredients?>? = dishes
    val valid = stored.orEmpty().mapNotNull { it?.sanitized() }
    return DishMemory(
      version = if (version > 0) version else DISH_MEMORY_VERSION,
      dishes = valid.sortedByDescending { it.updatedAt }.distinctBy { it.key }.take(DISH_MEMORY_CAP)
    )
  }

  private fun DishIngredients.sanitized(): DishIngredients? {
    val storedKey: String? = key
    val storedName: String? = name
    val storedIngredients: List<String?>? = ingredients
    if (storedKey.isNullOrBlank() || storedName == null || storedIngredients == null) return null
    val names = storedIngredients.filterNotNull().map(::normalizeName).filter { it.isNotEmpty() }
      .distinctBy(::matchKey).take(DISH_INGREDIENT_CAP)
    if (names.isEmpty()) return null
    return DishIngredients(storedKey, normalizeName(storedName), names, updatedAt)
  }
}
