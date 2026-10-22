package ourmeal.app.model

data class DishDraft(
  val key: String,
  val name: String,
  val occurrences: List<DishOccurrence>,
  val included: Boolean,
  val ingredients: List<String>,
  /** Null when the dish had no memory entry. */
  val remembered: List<String>?
) {
  val isChanged: Boolean get() = ingredients != remembered.orEmpty()
  val isFull: Boolean get() = ingredients.size >= DISH_INGREDIENT_CAP

  fun addIngredient(raw: String): IngredientEdit {
    val name = itemName(raw)
    val key = matchKey(name)
    return when {
      name.isEmpty() -> IngredientEdit.Blank
      isFull -> IngredientEdit.Full
      ingredients.any { matchKey(it) == key } -> IngredientEdit.Duplicate
      else -> IngredientEdit.Added(copy(ingredients = ingredients + name))
    }
  }

  fun withoutIngredient(name: String): DishDraft = copy(ingredients = ingredients - name)
}

sealed interface IngredientEdit {
  data class Added(val draft: DishDraft) : IngredientEdit
  data object Blank : IngredientEdit
  data object Duplicate : IngredientEdit
  data object Full : IngredientEdit
}

/**
 * One draft per planned dish, with the remembered ingredients preloaded. [saved] drafts from a
 * recreated screen keep their choices for the dishes that are still on the menu.
 */
fun List<MenuDish>.toDrafts(memory: DishMemory?, saved: List<DishDraft> = emptyList()): List<DishDraft> {
  val previous = saved.associateBy { it.key }
  return map { dish ->
    val remembered = memory?.ingredientsFor(dish.key)
    val draft = previous[dish.key]
    DishDraft(
      key = dish.key,
      name = dish.name,
      occurrences = dish.occurrences,
      included = draft?.included ?: true,
      ingredients = draft?.ingredients ?: remembered.orEmpty(),
      remembered = remembered
    )
  }
}

/** True when leaving would lose something the user decided on this screen. */
fun List<DishDraft>.hasEdits(): Boolean = any { !it.included || it.isChanged }

/** Ingredients to add, in dish order and de-duplicated by matchKey. */
fun List<DishDraft>.ingredientsToAdd(): List<String> =
  filter { it.included }.flatMap { it.ingredients }.distinctBy(::matchKey)

/** Dish entries to write to memory: only dishes whose ingredients changed. */
fun List<DishDraft>.memoryChanges(now: Long): List<DishIngredients> =
  filter { it.isChanged }.map { DishIngredients(it.key, it.name, it.ingredients, now) }
