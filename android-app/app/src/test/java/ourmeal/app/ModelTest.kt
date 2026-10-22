package ourmeal.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import ourmeal.app.model.ListItem
import ourmeal.app.model.UserList
import ourmeal.app.model.WeeklyMeals
import ourmeal.app.model.matchKey
import ourmeal.app.model.normalizeName

class UserListTest {
  private fun listWith(vararg completed: Boolean): UserList = UserList(
    id = 1L,
    name = "Prueba",
    items = completed.mapIndexed { index, done ->
      ListItem(id = index.toLong(), name = "Producto $index", isCompleted = done, createdAt = 0L)
    }.toMutableList(),
    createdAt = 0L
  )

  @Test
  fun emptyListHasNoProgress() {
    val list = listWith()
    assertEquals(0, list.getTotalCount())
    assertEquals(0, list.getCompletedCount())
    assertEquals(0, list.getCompletionPercentage())
  }

  @Test
  fun countsCompletedItemsAndRoundsDown() {
    val list = listWith(true, false, false)
    assertEquals(3, list.getTotalCount())
    assertEquals(1, list.getCompletedCount())
    assertEquals(33, list.getCompletionPercentage())
  }

  @Test
  fun fullListIsComplete() {
    val list = listWith(true, true)
    assertEquals(100, list.getCompletionPercentage())
  }
}

class WeeklyMealsTest {
  @Test
  fun startsWithSevenEmptyDays() {
    val week = WeeklyMeals(weekId = "2026-W42")
    assertEquals(WeeklyMeals.DAYS.size, week.meals.size)
    WeeklyMeals.DAYS.forEach { day ->
      val meal = week.getMealForDay(day)
      assertEquals("", meal.comida)
      assertEquals("", meal.cena)
    }
  }

  @Test
  fun missingDayDoesNotMutateTheWeek() {
    val week = WeeklyMeals(weekId = "2026-W42")
    week.getMealForDay("Festivo")
    assertFalse(week.meals.containsKey("Festivo"))
  }

  @Test
  fun storesMealsPerDay() {
    val week = WeeklyMeals(weekId = "2026-W42")
    week.setMealForDay("Lunes", ourmeal.app.model.Meal(comida = "Lentejas", cena = "Tortilla"))
    assertEquals("Lentejas", week.getMealForDay("Lunes").comida)
    assertEquals("Tortilla", week.getMealForDay("Lunes").cena)
  }
}

class TextNormalizerTest {
  @Test
  fun trimsAndCollapsesWhitespace() {
    assertEquals("Compra de la semana", normalizeName("  Compra   de  la semana  "))
  }

  @Test
  fun keepsSingleWords() {
    assertEquals("Tomates", normalizeName("Tomates"))
  }

  @Test
  fun matchKeyIgnoresCaseAccentsAndSpacingButKeepsEnye() {
    assertEquals("albondigas en salsa", matchKey("  Albóndigas  en salsa "))
    assertEquals("piña", matchKey("PIÑA"))
    assertEquals("creme", matchKey("Crème"))
    assertEquals("pinguino", matchKey("Pingüino"))
    assertEquals("", matchKey(""))
  }
}
