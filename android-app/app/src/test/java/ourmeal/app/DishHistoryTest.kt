package ourmeal.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ourmeal.app.model.DishHistory
import ourmeal.app.model.DishRecord
import ourmeal.app.model.Meal
import ourmeal.app.model.MealSlot
import ourmeal.app.model.RepeatHint
import ourmeal.app.model.WeeklyMeals
import ourmeal.app.model.keysWrittenSince
import ourmeal.app.model.matchKey
import ourmeal.app.model.normalizeName
import ourmeal.app.model.repeatHint
import ourmeal.app.model.toDishRecords
import java.time.LocalDate

private val TODAY: LocalDate = LocalDate.of(2026, 10, 22)

private fun record(name: String, date: LocalDate, slot: MealSlot = MealSlot.COMIDA) =
  DishRecord(matchKey(name), normalizeName(name), date, slot)

private fun daysAgo(days: Long): LocalDate = TODAY.minusDays(days)

private fun week(id: String, vararg meals: Pair<String, Meal>): WeeklyMeals =
  WeeklyMeals(id).apply { meals.forEach { (day, meal) -> setMealForDay(day, meal) } }

private fun DishHistory.names(
  query: String = "",
  slot: MealSlot = MealSlot.COMIDA,
  planned: Set<String> = emptySet(),
  hidden: Set<String> = emptySet()
): List<String> = suggestions(query, slot, TODAY, planned, hidden).map { it.name }

class DishRecordsTest {
  @Test
  fun datesFollowTheIsoWeekAndDayOrder() {
    val records = listOf(
      week("2026-W43", "Lunes" to Meal(comida = "Lentejas"), "Domingo" to Meal(cena = "Tortilla"))
    ).toDishRecords()
    assertEquals(
      listOf(
        record("Lentejas", LocalDate.of(2026, 10, 19)),
        record("Tortilla", LocalDate.of(2026, 10, 25), MealSlot.CENA)
      ),
      records
    )
  }

  @Test
  fun skipsInvalidWeeksUnknownDaysBlanksAndTheExcludedWeek() {
    val weeks = listOf(
      week("2021-W53", "Lunes" to Meal(comida = "Lentejas")),
      week("2026-W42", "Lunes" to Meal(comida = "   ", cena = "\n")),
      WeeklyMeals("2026-W41", mutableMapOf("Festivo" to Meal(comida = "Paella"))),
      week("2026-W43", "Martes" to Meal(comida = "Pasta"))
    )
    assertTrue(weeks.toDishRecords(excludeWeekId = "2026-W43").isEmpty())
    assertEquals(listOf("Pasta"), weeks.toDishRecords().map { it.name })
  }

  @Test
  fun keepsOneLineNamesAndAccentFreeKeys() {
    val records = listOf(week("2026-W43", "Lunes" to Meal(comida = " Albóndigas\nen  salsa "))).toDishRecords()
    assertEquals("Albóndigas en salsa", records.single().name)
    assertEquals("albondigas en salsa", records.single().key)
  }

  @Test
  fun writtenKeysOnlyIncludeSlotsThatChanged() {
    val before = week("2026-W43", "Lunes" to Meal(comida = "Pasta", cena = "Tortilla"))
    val after = week(
      "2026-W43",
      "Lunes" to Meal(comida = "pasta", cena = "Crema"),
      "Jueves" to Meal(comida = "Tortilla")
    )
    assertEquals(setOf("crema", "tortilla"), after.keysWrittenSince(before))
    assertEquals(setOf("pasta", "crema", "tortilla"), after.keysWrittenSince(null))
  }
}

class HabitualSuggestionsTest {
  @Test
  fun needsAtLeastTwoAppearances() {
    val history = DishHistory(
      listOf(record("Lentejas", daysAgo(30)), record("Lentejas", daysAgo(60)), record("Paella", daysAgo(30)))
    )
    assertEquals(listOf("Lentejas"), history.names())
  }

  @Test
  fun excludesPlannedAndHiddenDishes() {
    val history = DishHistory(
      listOf("Lentejas", "Pasta", "Crema").flatMap { listOf(record(it, daysAgo(30)), record(it, daysAgo(60))) }
    )
    assertEquals(listOf("Crema"), history.names(planned = setOf("lentejas"), hidden = setOf("pasta")))
  }

  @Test
  fun showsAtMostSix() {
    val history = DishHistory(
      (1..8).flatMap { listOf(record("Plato $it", daysAgo(30)), record("Plato $it", daysAgo(60))) }
    )
    assertEquals(6, history.names().size)
  }

  @Test
  fun prefersWhatIsUsuallyEatenInTheSameSlot() {
    val history = DishHistory(
      listOf(
        record("Lentejas", daysAgo(30)), record("Lentejas", daysAgo(60)),
        record("Tortilla", daysAgo(30), MealSlot.CENA), record("Tortilla", daysAgo(60), MealSlot.CENA)
      )
    )
    assertEquals(listOf("Lentejas", "Tortilla"), history.names(slot = MealSlot.COMIDA))
    assertEquals(listOf("Tortilla", "Lentejas"), history.names(slot = MealSlot.CENA))
  }

  @Test
  fun recentDishesGoBehindEquallyFrequentOlderOnes() {
    val history = DishHistory(
      listOf(
        record("Pasta", daysAgo(3)), record("Pasta", daysAgo(40)),
        record("Arroz", daysAgo(30)), record("Arroz", daysAgo(60)),
        record("Crema", TODAY.plusDays(7)), record("Crema", daysAgo(90))
      )
    )
    assertEquals(listOf("Arroz", "Crema", "Pasta"), history.names())
  }

  @Test
  fun onlyCountsTheLastYear() {
    val history = DishHistory(
      listOf(
        record("Lentejas", daysAgo(364)), record("Lentejas", daysAgo(370)), record("Lentejas", daysAgo(30)),
        record("Paella", daysAgo(365)), record("Paella", daysAgo(400))
      )
    )
    assertEquals(listOf("Lentejas"), history.names())
    assertTrue(history.names(query = "pae").isEmpty())
  }
}

class SearchSuggestionsTest {
  private val history = DishHistory(
    listOf(
      record("Albóndigas en salsa", daysAgo(20)),
      record("Salsa de tomate", daysAgo(30)),
      record("Lentejas", daysAgo(30)),
      record("Lentejas con chorizo", daysAgo(40))
    )
  )

  @Test
  fun matchesTheStartIgnoringCaseAndAccents() {
    assertEquals(listOf("Albóndigas en salsa"), history.names(query = "ALBON"))
  }

  @Test
  fun wordMatchesGoBehindStartMatches() {
    assertEquals(listOf("Salsa de tomate", "Albóndigas en salsa"), history.names(query = "salsa"))
    assertEquals(listOf("Albóndigas en salsa"), history.names(query = "en sal"))
  }

  @Test
  fun doesNotMatchInsideWords() {
    assertTrue(history.names(query = "ond").isEmpty())
  }

  @Test
  fun neverSuggestsTheTextAlreadyWritten() {
    assertEquals(listOf("Lentejas con chorizo"), history.names(query = " lentejas "))
  }

  @Test
  fun includesDishesAlreadyPlannedThisWeek() {
    assertEquals(listOf("Lentejas", "Lentejas con chorizo"), history.names(query = "len", planned = setOf("lentejas")))
  }

  @Test
  fun showsTheLatestWritingOfADish() {
    val history = DishHistory(listOf(record("albondigas", daysAgo(60)), record("Albóndigas", daysAgo(30))))
    assertEquals(listOf("Albóndigas"), history.names(query = "alb"))
  }

  @Test
  fun breaksTiesByKey() {
    val history = DishHistory(listOf(record("Pisto", daysAgo(30)), record("Pasta", daysAgo(30))))
    assertEquals(listOf("Pasta", "Pisto"), history.names(query = "p"))
  }
}

class RepeatHintTest {
  private val weekId = "2026-W43"
  private val thursday = LocalDate.of(2026, 10, 22)

  @Test
  fun sameWeekComesBeforeRecentDays() {
    val week = week(weekId, "Lunes" to Meal(cena = "Pasta"), "Jueves" to Meal(comida = "pasta"))
    val history = DishHistory(listOf(record("Pasta", thursday.minusDays(2))))
    assertEquals(
      RepeatHint.SameWeek("Lunes", MealSlot.CENA),
      repeatHint(week, "Jueves", MealSlot.COMIDA, thursday, history)
    )
  }

  @Test
  fun findsTheOtherMealOfTheSameDay() {
    val week = week(weekId, "Jueves" to Meal(comida = "Crema", cena = "crema"))
    assertEquals(
      RepeatHint.SameWeek("Jueves", MealSlot.COMIDA),
      repeatHint(week, "Jueves", MealSlot.CENA, thursday, DishHistory(emptyList()))
    )
  }

  @Test
  fun recentDaysCoverThirteenDaysBack() {
    val week = week(weekId, "Jueves" to Meal(comida = "Tortilla"))
    assertEquals(
      RepeatHint.Recent(13),
      repeatHint(week, "Jueves", MealSlot.COMIDA, thursday, DishHistory(listOf(record("Tortilla", thursday.minusDays(13)))))
    )
    assertNull(
      repeatHint(week, "Jueves", MealSlot.COMIDA, thursday, DishHistory(listOf(record("Tortilla", thursday.minusDays(14)))))
    )
  }

  @Test
  fun ignoresLaterWeeksAndBlankSlots() {
    val week = week(weekId, "Jueves" to Meal(comida = "Tortilla"))
    val history = DishHistory(listOf(record("Tortilla", thursday.plusDays(5))))
    assertNull(repeatHint(week, "Jueves", MealSlot.COMIDA, thursday, history))
    assertNull(repeatHint(week, "Jueves", MealSlot.CENA, thursday, history))
  }
}
