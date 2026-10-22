package ourmeal.app.model

data class DishOccurrence(val day: String, val slot: MealSlot)

data class MenuDish(
  val key: String,
  val name: String,
  val occurrences: List<DishOccurrence>
)

/** Planned dishes grouped by matchKey, in week order (day, then lunch before dinner). */
fun WeeklyMeals.plannedDishes(): List<MenuDish> =
  filledSlots()
    .groupBy { it.key }
    .map { (key, slots) ->
      MenuDish(
        key = key,
        name = normalizeName(slots.first().text),
        occurrences = slots.map { DishOccurrence(it.day, it.slot) }
      )
    }
