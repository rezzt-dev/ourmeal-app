package ourmeal.app

import org.junit.Assert.assertEquals
import org.junit.Test
import ourmeal.app.model.WeekUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

class WeekUtilsTest {
  @Test
  fun crossesIsoWeekYearWithoutRepeatingWeeks() {
    val date = Date.from(LocalDate.of(2020, 12, 31).atStartOfDay(ZoneId.systemDefault()).toInstant())
    assertEquals("2020-W53", WeekUtils.getWeekIdFromDate(date))
    assertEquals("2021-W01", WeekUtils.getNextWeek("2020-W53"))
    assertEquals("2020-W53", WeekUtils.getPreviousWeek("2021-W01"))
    assertEquals(LocalDate.of(2020, 12, 28), WeekUtils.startOfWeek("2020-W53"))
  }

  @Test
  fun buildsWeekIdFromLocalDate() {
    assertEquals("2026-W42", WeekUtils.weekIdFor(LocalDate.of(2026, 10, 17)))
  }

  @Test
  fun showsRangeAcrossYears() {
    assertEquals("28 dic 2020 – 3 ene 2021", WeekUtils.getWeekDisplay("2020-W53"))
  }

  @Test
  fun invalidWeekIdFallsBackToCurrentMonday() {
    assertEquals(DayOfWeek.MONDAY, WeekUtils.startOfWeek("not-a-week").dayOfWeek)
  }
}
