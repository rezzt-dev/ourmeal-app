package ourmeal.app.model

/** One meal of the week: a day and its lunch or dinner. */
data class SlotRef(val day: String, val slot: MealSlot)

/** A meal whose text an edit changed, with what it held before and holds now. */
data class SlotChange(val ref: SlotRef, val before: String, val after: String)

/** The week after a bulk edit, with the meals it changed so that it can be undone. */
data class MenuEdit(val week: WeeklyMeals, val changes: List<SlotChange>)

enum class CopyMode {
  /** Only blank meals receive a dish; what the user already wrote stays. */
  FILL_EMPTY,
  /** Blank meals and meals with a different dish receive the copied one. */
  REPLACE
}

/** What a copy would change: blank meals it fills and written meals it would overwrite. */
data class CopyPreview(val fillable: Int, val replaceable: Int)

/** Every meal of the week in display order. */
val WEEK_SLOTS: List<SlotRef> = WeeklyMeals.DAYS.flatMap { day ->
  MealSlot.entries.map { SlotRef(day, it) }
}

fun Meal.withText(slot: MealSlot, text: String): Meal = when (slot) {
  MealSlot.COMIDA -> copy(comida = text)
  MealSlot.CENA -> copy(cena = text)
}

/** Text of one meal; Gson may leave a missing day or dish as null, read here as blank. */
fun WeeklyMeals.textAt(ref: SlotRef): String {
  val stored: Map<String, Meal?>? = meals
  val text: String? = stored?.get(ref.day)?.textFor(ref.slot)
  return text.orEmpty()
}

/** Copy that shares no [Meal] with the original, with every day present and no null dish. */
fun WeeklyMeals.deepCopy(): WeeklyMeals = WeeklyMeals(
  weekId = weekId,
  meals = WeeklyMeals.DAYS.associateWithTo(mutableMapOf()) { day ->
    Meal(comida = textAt(SlotRef(day, MealSlot.COMIDA)), cena = textAt(SlotRef(day, MealSlot.CENA)))
  }
)

/** How many meals of the week have a dish. */
fun WeeklyMeals.plannedCount(): Int = WEEK_SLOTS.count { textAt(it).isNotBlank() }

fun WeeklyMeals.isDayEmpty(day: String): Boolean =
  MealSlot.entries.all { textAt(SlotRef(day, it)).isBlank() }

/**
 * The dishes a copy brings, meal by meal. A blank source never clears a target, and a target
 * that already holds the same dish, ignoring case and accents, is left untouched.
 */
class MenuCopy private constructor(private val sources: Map<SlotRef, String>) {

  fun preview(target: WeeklyMeals): CopyPreview {
    var fillable = 0
    var replaceable = 0
    sources.forEach { (ref, dish) ->
      when (outcome(target.textAt(ref), dish)) {
        Outcome.FILL -> fillable++
        Outcome.REPLACE -> replaceable++
        Outcome.KEEP -> Unit
      }
    }
    return CopyPreview(fillable, replaceable)
  }

  fun applyTo(target: WeeklyMeals, mode: CopyMode): MenuEdit {
    val week = target.deepCopy()
    val changes = mutableListOf<SlotChange>()
    sources.forEach { (ref, dish) ->
      val current = week.textAt(ref)
      val write = when (outcome(current, dish)) {
        Outcome.FILL -> true
        Outcome.REPLACE -> mode == CopyMode.REPLACE
        Outcome.KEEP -> false
      }
      if (write) {
        week.setMealForDay(ref.day, week.getMealForDay(ref.day).withText(ref.slot, dish))
        changes += SlotChange(ref, before = current, after = dish)
      }
    }
    return MenuEdit(week, changes)
  }

  private fun outcome(current: String, dish: String): Outcome = when {
    current.isBlank() -> Outcome.FILL
    matchKey(current) == matchKey(dish) -> Outcome.KEEP
    else -> Outcome.REPLACE
  }

  private enum class Outcome { FILL, REPLACE, KEEP }

  companion object {
    /** Lunch and dinner of [from] onto each day of [to]; [from] itself is never a target. */
    fun ofDay(week: WeeklyMeals, from: String, to: Collection<String>): MenuCopy {
      val targets = WeeklyMeals.DAYS.filter { it in to && it != from }
      return MenuCopy(buildMap {
        targets.forEach { day ->
          MealSlot.entries.forEach { slot ->
            val dish = normalizeName(week.textAt(SlotRef(from, slot)))
            if (dish.isNotEmpty()) put(SlotRef(day, slot), dish)
          }
        }
      })
    }

    /** Each meal of [source] onto the same day and meal of the target week. */
    fun ofWeek(source: WeeklyMeals): MenuCopy = MenuCopy(buildMap {
      WEEK_SLOTS.forEach { ref ->
        val dish = normalizeName(source.textAt(ref))
        if (dish.isNotEmpty()) put(ref, dish)
      }
    })
  }
}

/** Blanks every meal of [days]; meals already blank are not counted as changes. */
fun WeeklyMeals.cleared(days: Collection<String> = WeeklyMeals.DAYS): MenuEdit {
  val week = deepCopy()
  val changes = WEEK_SLOTS.filter { it.day in days }.mapNotNull { ref ->
    val current = week.textAt(ref)
    if (current.isEmpty()) return@mapNotNull null
    week.setMealForDay(ref.day, week.getMealForDay(ref.day).withText(ref.slot, ""))
    SlotChange(ref, before = current, after = "")
  }
  return MenuEdit(week, changes)
}

/**
 * Undoes [changes] on the meals that still hold what the edit wrote; a meal the user has
 * edited again since keeps the newer text.
 */
fun WeeklyMeals.reverted(changes: List<SlotChange>): MenuEdit {
  val week = deepCopy()
  val undone = changes.mapNotNull { change ->
    if (week.textAt(change.ref) != change.after) return@mapNotNull null
    val day = change.ref.day
    week.setMealForDay(day, week.getMealForDay(day).withText(change.ref.slot, change.before))
    SlotChange(change.ref, before = change.after, after = change.before)
  }
  return MenuEdit(week, undone)
}
