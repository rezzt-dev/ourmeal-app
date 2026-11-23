package ourmeal.app

import org.junit.Assert.assertEquals
import org.junit.Test
import ourmeal.app.model.Meal
import ourmeal.app.model.MenuShareLabels
import ourmeal.app.model.SharedDay
import ourmeal.app.model.WeeklyMeals
import ourmeal.app.model.sharedDays
import ourmeal.app.model.toShareText

class MenuShareTest {
  private val labels = MenuShareLabels(header = "OurMeal · menú de la semana", lunch = "Comida", dinner = "Cena")
  private val titles = listOf("Lunes 23", "Martes 24", "Miércoles 25", "Jueves 26", "Viernes 27", "Sábado 28", "Domingo 29")

  @Test
  fun sharesOneDayPerLineWithADashForBlankMeals() {
    val week = WeeklyMeals("2026-W48").apply {
      setMealForDay("Lunes", Meal("Lentejas", "Tortilla"))
      setMealForDay("Sábado", Meal("", "Pizza casera"))
    }

    val text = week.toShareText(labels, "23 nov – 29 nov 2026", titles)

    assertEquals(
      """
      OurMeal · menú de la semana
      23 nov – 29 nov 2026

      Lunes 23 · Comida: Lentejas · Cena: Tortilla
      Martes 24 · Comida: - · Cena: -
      Miércoles 25 · Comida: - · Cena: -
      Jueves 26 · Comida: - · Cena: -
      Viernes 27 · Comida: - · Cena: -
      Sábado 28 · Comida: - · Cena: Pizza casera
      Domingo 29 · Comida: - · Cena: -
      """.trimIndent(),
      text
    )
  }

  @Test
  fun longDishesAreKeptWholeOnOneLine() {
    val long = "Merluza en salsa verde con almejas, patatas panaderas y una ensalada de tomate de temporada"
    val week = WeeklyMeals("2026-W48").apply { setMealForDay("Martes", Meal("$long\n", "  Sopa   de ajo ")) }

    val day = week.sharedDays(titles, empty = "—")[1]

    assertEquals(SharedDay("Martes 24", long, "Sopa de ajo"), day)
  }

  @Test(expected = IllegalArgumentException::class)
  fun needsOneTitlePerDay() {
    WeeklyMeals("2026-W48").sharedDays(titles.take(3), empty = "-")
  }
}
