package ourmeal.app.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

const val SUGGESTION_LIMIT = 6
const val SUGGESTION_WINDOW_DAYS = 364L
const val HABITUAL_MIN_COUNT = 2
const val RECENT_PENALTY_DAYS = 7L
const val HINT_WINDOW_DAYS = 13L

data class DishRecord(
  val key: String,
  val name: String,
  val date: LocalDate,
  val slot: MealSlot
)

data class DishSuggestion(val key: String, val name: String)

sealed interface RepeatHint {
  data class SameWeek(val day: String, val slot: MealSlot) : RepeatHint
  data class Recent(val daysAgo: Int) : RepeatHint
}

/** One record per non-blank slot; skips invalid week ids and unknown day keys. */
fun List<WeeklyMeals>.toDishRecords(excludeWeekId: String? = null): List<DishRecord> =
  flatMap { week ->
    // Gson leaves nulls from hand-edited files in non-null fields, so read them as nullable.
    val weekId: String? = week.weekId
    if (weekId == null || weekId == excludeWeekId || !WeekUtils.isValidWeekId(weekId)) {
      return@flatMap emptyList()
    }
    val monday = WeekUtils.startOfWeek(weekId)
    week.filledSlots().map { filled ->
      DishRecord(
        key = filled.key,
        name = normalizeName(filled.text),
        date = monday.plusDays(filled.dayIndex.toLong()),
        slot = filled.slot
      )
    }
  }

/** Comparison keys of every dish planned in the week. */
fun WeeklyMeals.dishKeys(): Set<String> = filledSlots().mapTo(mutableSetOf()) { it.key }

/** Keys of the slots whose dish changed since [previous]; every planned dish when it is null. */
fun WeeklyMeals.keysWrittenSince(previous: WeeklyMeals?): Set<String> {
  val before = previous?.filledSlots().orEmpty().associate { (it.day to it.slot) to it.key }
  return filledSlots()
    .filter { before[it.day to it.slot] != it.key }
    .mapTo(mutableSetOf()) { it.key }
}

class DishHistory(records: List<DishRecord>) {
  private val dishes: Map<String, Dish> = records
    .groupBy { it.key }
    .mapValues { (key, list) -> Dish(key, list.sortedBy { it.date }) }

  fun suggestions(
    query: String,
    slot: MealSlot,
    slotDate: LocalDate,
    plannedKeys: Set<String>,
    hiddenKeys: Set<String>,
    limit: Int = SUGGESTION_LIMIT
  ): List<DishSuggestion> {
    val typed = matchKey(query)
    val windowStart = slotDate.minusDays(SUGGESTION_WINDOW_DAYS)
    return dishes.values.asSequence()
      .filter { it.key !in hiddenKeys && it.key != typed }
      .mapNotNull { dish ->
        val records = dish.records.dropWhile { it.date < windowStart }
        if (records.isEmpty()) return@mapNotNull null
        val level = if (typed.isEmpty()) {
          if (dish.key in plannedKeys || records.size < HABITUAL_MIN_COUNT) return@mapNotNull null
          0
        } else {
          matchLevel(dish.key, typed) ?: return@mapNotNull null
        }
        Candidate(
          dish = dish,
          level = level,
          isRecent = records.any {
            abs(ChronoUnit.DAYS.between(it.date, slotDate)) <= RECENT_PENALTY_DAYS
          },
          slotCount = records.count { it.slot == slot },
          totalCount = records.size,
          latest = records.last().date
        )
      }
      .sortedWith(
        compareBy<Candidate> { it.level }
          .thenBy { it.isRecent }
          .thenByDescending { it.slotCount }
          .thenByDescending { it.totalCount }
          .thenByDescending { it.latest }
          .thenBy { it.dish.key }
      )
      .take(limit)
      .map { DishSuggestion(it.dish.key, it.dish.name) }
      .toList()
  }

  /** Most recent record of [key] strictly before [date], or null. */
  fun lastBefore(key: String, date: LocalDate): DishRecord? =
    dishes[key]?.records?.lastOrNull { it.date < date }

  /** 0 when the key starts with the query, 1 when one of its later words does. */
  private fun matchLevel(key: String, query: String): Int? = when {
    key.startsWith(query) -> 0
    key.contains(" $query") -> 1
    else -> null
  }

  /** Every record of one dish, oldest first; the latest one gives the displayed name. */
  private class Dish(val key: String, val records: List<DishRecord>) {
    val name: String get() = records.last().name
  }

  private class Candidate(
    val dish: Dish,
    val level: Int,
    val isRecent: Boolean,
    val slotCount: Int,
    val totalCount: Int,
    val latest: LocalDate
  )
}

fun repeatHint(
  week: WeeklyMeals,
  day: String,
  slot: MealSlot,
  slotDate: LocalDate,
  history: DishHistory
): RepeatHint? {
  val slots = week.filledSlots()
  val key = slots.find { it.day == day && it.slot == slot }?.key ?: return null
  slots.firstOrNull { it.key == key && !(it.day == day && it.slot == slot) }
    ?.let { return RepeatHint.SameWeek(it.day, it.slot) }
  val last = history.lastBefore(key, slotDate) ?: return null
  val daysAgo = ChronoUnit.DAYS.between(last.date, slotDate)
  return if (daysAgo in 1..HINT_WINDOW_DAYS) RepeatHint.Recent(daysAgo.toInt()) else null
}

internal class FilledSlot(
  val dayIndex: Int,
  val day: String,
  val slot: MealSlot,
  val text: String,
  val key: String
)

/** Non-blank slots in weekly order; days outside [WeeklyMeals.DAYS] are ignored. */
internal fun WeeklyMeals.filledSlots(): List<FilledSlot> {
  val stored: Map<String, Meal?>? = meals
  return WeeklyMeals.DAYS.flatMapIndexed { index, day ->
    val meal = stored?.get(day) ?: return@flatMapIndexed emptyList()
    MealSlot.entries.mapNotNull { slot ->
      val text: String? = meal.textFor(slot)
      val key = text?.let(::matchKey).orEmpty()
      if (text == null || key.isEmpty()) null else FilledSlot(index, day, slot, text, key)
    }
  }
}
