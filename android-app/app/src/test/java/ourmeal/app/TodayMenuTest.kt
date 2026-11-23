package ourmeal.app

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ourmeal.app.model.Meal
import ourmeal.app.model.WeeklyMeals
import ourmeal.app.model.todayMenu
import java.time.LocalDate

class TodayMenuTest {
  /** Sunday of ISO week 2026-W47. */
  private val sunday = LocalDate.of(2026, 11, 22)

  @Test
  fun todayIsReadFromItsWeekAndDay() {
    val week = WeeklyMeals("2026-W47").apply {
      setMealForDay("Domingo", Meal(comida = "  Paella\nde marisco ", cena = "Tortilla"))
      setMealForDay("Sábado", Meal(comida = "Lentejas"))
    }

    val menu = todayMenu(listOf(WeeklyMeals("2026-W46"), week), sunday)

    assertEquals("Paella de marisco", menu.lunch)
    assertEquals("Tortilla", menu.dinner)
    assertFalse(menu.isEmpty)
    assertEquals("Lentejas", todayMenu(listOf(week), sunday.minusDays(1)).lunch)
  }

  @Test
  fun aMissingWeekOrDayIsAnEmptyMenu() {
    assertTrue(todayMenu(emptyList(), sunday).isEmpty)
    assertTrue(todayMenu(listOf(WeeklyMeals("2026-W48")), sunday).isEmpty)
    // Monday of the next ISO week reads the next week.
    val next = WeeklyMeals("2026-W48").apply { setMealForDay("Lunes", Meal(cena = "Sopa")) }
    assertEquals("Sopa", todayMenu(listOf(next), sunday.plusDays(1)).dinner)
  }

  @Test
  fun nullsLeftByAnOldOrDamagedFileAreEmpty() {
    val type = object : TypeToken<List<WeeklyMeals?>>() {}.type
    val weeks: List<WeeklyMeals?> = Gson().fromJson(
      """[null,{"weekId":"2026-W47","meals":{"Domingo":{"comida":null},"Lunes":null}}]""",
      type
    )

    val menu = todayMenu(weeks, sunday)

    assertEquals("", menu.lunch)
    assertEquals("", menu.dinner)
  }
}
