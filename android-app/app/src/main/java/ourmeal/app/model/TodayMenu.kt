package ourmeal.app.model

import java.time.LocalDate

/** Lunch and dinner planned for [date], each on one line; empty when nothing is planned. */
data class TodayMenu(val date: LocalDate, val lunch: String, val dinner: String) {
  val isEmpty: Boolean get() = lunch.isEmpty() && dinner.isEmpty()
}

/** The meals of [date] among the stored [weeks]; missing weeks, days or fields count as empty. */
fun todayMenu(weeks: List<WeeklyMeals?>, date: LocalDate): TodayMenu {
  val weekId = WeekUtils.weekIdFor(date)
  val day = WeeklyMeals.DAYS[date.dayOfWeek.value - 1]
  // Gson skips the Kotlin constructor, so stored weeks, days and meals may hold nulls.
  val meals: Map<String, Meal?>? = weeks.lastOrNull { it?.weekId == weekId }?.meals
  val meal: Meal? = meals?.get(day)
  val lunch: String? = meal?.comida
  val dinner: String? = meal?.cena
  return TodayMenu(date, normalizeName(lunch.orEmpty()), normalizeName(dinner.orEmpty()))
}
