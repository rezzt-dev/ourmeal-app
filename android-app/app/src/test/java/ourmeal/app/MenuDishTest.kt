package ourmeal.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ourmeal.app.model.DishOccurrence
import ourmeal.app.model.Meal
import ourmeal.app.model.MealSlot
import ourmeal.app.model.MenuDish
import ourmeal.app.model.WeeklyMeals
import ourmeal.app.model.plannedDishes

class MenuDishTest {
  private fun week(vararg meals: Pair<String, Meal>): WeeklyMeals =
    WeeklyMeals("2026-W43").apply { meals.forEach { (day, meal) -> setMealForDay(day, meal) } }

  @Test
  fun emptyWeekHasNoDishes() {
    assertTrue(WeeklyMeals("2026-W43").plannedDishes().isEmpty())
  }

  @Test
  fun followsTheWeekOrderWithLunchBeforeDinner() {
    val dishes = week(
      "Martes" to Meal(comida = "Pasta"),
      "Lunes" to Meal(comida = "Lentejas", cena = "Tortilla"),
      "Domingo" to Meal(cena = "Pizza")
    ).plannedDishes()
    assertEquals(listOf("Lentejas", "Tortilla", "Pasta", "Pizza"), dishes.map { it.name })
  }

  @Test
  fun groupsCaseAccentAndSpacingVariantsUnderTheFirstName() {
    val dishes = week(
      "Lunes" to Meal(comida = "  Albóndigas  en salsa "),
      "Martes" to Meal(cena = "albondigas en salsa"),
      "Jueves" to Meal(comida = "ALBÓNDIGAS EN SALSA")
    ).plannedDishes()
    assertEquals(
      listOf(
        MenuDish(
          key = "albondigas en salsa",
          name = "Albóndigas en salsa",
          occurrences = listOf(
            DishOccurrence("Lunes", MealSlot.COMIDA),
            DishOccurrence("Martes", MealSlot.CENA),
            DishOccurrence("Jueves", MealSlot.COMIDA)
          )
        )
      ),
      dishes
    )
  }

  @Test
  fun ignoresBlankSlotsAndFlattensMultilineText() {
    val dishes = week(
      "Lunes" to Meal(comida = "   ", cena = "\n"),
      "Miércoles" to Meal(comida = "Lentejas\ncon chorizo")
    ).plannedDishes()
    assertEquals(listOf("Lentejas con chorizo"), dishes.map { it.name })
  }

  @Test
  fun aDishOnEverySlotIsOneEntryWithFourteenOccurrences() {
    val week = WeeklyMeals("2026-W43").apply {
      WeeklyMeals.DAYS.forEach { setMealForDay(it, Meal(comida = "Lentejas", cena = "lentejas")) }
    }
    val dishes = week.plannedDishes()
    assertEquals(1, dishes.size)
    assertEquals(14, dishes.single().occurrences.size)
  }
}
