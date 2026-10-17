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

  fun startOfWeek(weekId: String): LocalDate {
    val match = idPattern.matchEntire(weekId)
      ?: return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return try {
      LocalDate.of(match.groupValues[1].toInt(), 1, 4)
        .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, match.groupValues[2].toLong())
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    } catch (_: Exception) {
      LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
  }

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

  private fun idFor(date: LocalDate): String =
    "%04d-W%02d".format(Locale.ROOT,
      date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
}
