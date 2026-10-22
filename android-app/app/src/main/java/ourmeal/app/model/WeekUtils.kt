package ourmeal.app.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale

object WeekUtils {
  private val idPattern = Regex("^(\\d{4})-W(\\d{2})$")

  fun getCurrentWeekId(): String = idFor(LocalDate.now())
  fun getWeekIdFromDate(date: Date): String =
    weekIdFor(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate())
  fun weekIdFor(date: LocalDate): String = idFor(date)
  fun getPreviousWeek(weekId: String): String = idFor(startOfWeek(weekId).minusWeeks(1))
  fun getNextWeek(weekId: String): String = idFor(startOfWeek(weekId).plusWeeks(1))

  /** True for ids such as "2026-W43" that name a real ISO week. */
  fun isValidWeekId(weekId: String): Boolean = mondayOf(weekId) != null

  fun startOfWeek(weekId: String): LocalDate =
    mondayOf(weekId) ?: LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

  fun getWeekDisplay(weekId: String): String {
    val start = startOfWeek(weekId)
    val end = start.plusDays(6)
    val format = DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))
    return if (start.year == end.year) {
      "${start.format(format)} – ${end.format(format)} ${end.year}"
    } else {
      "${start.format(format)} ${start.year} – ${end.format(format)} ${end.year}"
    }
  }

  private fun mondayOf(weekId: String): LocalDate? {
    val match = idPattern.matchEntire(weekId) ?: return null
    val reference = LocalDate.of(match.groupValues[1].toInt(), 1, 4)
    val week = match.groupValues[2].toLong()
    // with() would silently roll a missing week 53 over into the next year.
    if (!IsoFields.WEEK_OF_WEEK_BASED_YEAR.rangeRefinedBy(reference).isValidValue(week)) return null
    return reference
      .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
      .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
  }

  private fun idFor(date: LocalDate): String =
    "%04d-W%02d".format(Locale.ROOT,
      date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
}
