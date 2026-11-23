package ourmeal.app.model

/** Texts of the shared menu, read from resources so this file stays free of Android. */
data class MenuShareLabels(
  val header: String,
  val lunch: String,
  val dinner: String,
  /** Shown for a meal without a dish. */
  val empty: String = "-"
)

/** A day of the shared menu: its title, such as "Lunes 24", and both dishes or [MenuShareLabels.empty]. */
data class SharedDay(val title: String, val lunch: String, val dinner: String)

/**
 * The days of the week in order, with each dish on one line: a dish written over several lines
 * is joined with spaces, never cut.
 */
fun WeeklyMeals.sharedDays(dayTitles: List<String>, empty: String): List<SharedDay> {
  require(dayTitles.size == WeeklyMeals.DAYS.size) { "One title per day is needed" }
  return WeeklyMeals.DAYS.mapIndexed { index, day ->
    fun dish(slot: MealSlot) = normalizeName(textAt(SlotRef(day, slot))).ifEmpty { empty }
    SharedDay(dayTitles[index], dish(MealSlot.COMIDA), dish(MealSlot.CENA))
  }
}

/**
 * Plain text of the week for anyone without the app: a header with the app name and the week
 * range, then one day per line with its lunch and dinner.
 */
fun WeeklyMeals.toShareText(labels: MenuShareLabels, weekRange: String, dayTitles: List<String>): String =
  buildString {
    appendLine(labels.header)
    appendLine(weekRange)
    appendLine()
    sharedDays(dayTitles, labels.empty).forEach { day ->
      appendLine("${day.title} · ${labels.lunch}: ${day.lunch} · ${labels.dinner}: ${day.dinner}")
    }
  }.trimEnd()
