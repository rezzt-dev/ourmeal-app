package ourmeal.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ourmeal.app.model.CopyMode
import ourmeal.app.model.CopyPreview
import ourmeal.app.model.Meal
import ourmeal.app.model.MealSlot
import ourmeal.app.model.MenuCopy
import ourmeal.app.model.SlotRef
import ourmeal.app.model.WeeklyMeals
import ourmeal.app.model.cleared
import ourmeal.app.model.deepCopy
import ourmeal.app.model.isDayEmpty
import ourmeal.app.model.plannedCount
import ourmeal.app.model.reverted
import ourmeal.app.model.textAt

class MenuEditingTest {
  private fun week(vararg days: Pair<String, Meal>, id: String = "2026-W48") =
    WeeklyMeals(id).apply { days.forEach { (day, meal) -> setMealForDay(day, meal) } }

  private fun WeeklyMeals.at(day: String, slot: MealSlot) = textAt(SlotRef(day, slot))

  @Test
  fun copyingADayFillsOnlyBlankMealsByDefault() {
    val plan = week(
      "Lunes" to Meal("Lentejas", "Tortilla"),
      "Martes" to Meal("", "Pescado")
    )
    val copy = MenuCopy.ofDay(plan, "Lunes", listOf("Martes", "Miércoles"))

    assertEquals(CopyPreview(fillable = 3, replaceable = 1), copy.preview(plan))
    val edit = copy.applyTo(plan, CopyMode.FILL_EMPTY)

    assertEquals(3, edit.changes.size)
    assertEquals("Lentejas", edit.week.at("Martes", MealSlot.COMIDA))
    assertEquals("Pescado", edit.week.at("Martes", MealSlot.CENA))
    assertEquals("Tortilla", edit.week.at("Miércoles", MealSlot.CENA))
    // The original week is left as it was.
    assertEquals("", plan.at("Martes", MealSlot.COMIDA))
  }

  @Test
  fun replacingOverwritesWrittenMealsButNeverTheSourceDay() {
    val plan = week(
      "Lunes" to Meal("Lentejas", "Tortilla"),
      "Martes" to Meal("Arroz", "Pescado")
    )
    val edit = MenuCopy.ofDay(plan, "Lunes", listOf("Lunes", "Martes")).applyTo(plan, CopyMode.REPLACE)

    assertEquals(2, edit.changes.size)
    assertEquals(Meal("Lentejas", "Tortilla"), edit.week.getMealForDay("Martes"))
    assertEquals(Meal("Lentejas", "Tortilla"), edit.week.getMealForDay("Lunes"))
  }

  @Test
  fun sameDishIgnoringCaseAndAccentsIsNotAConflict() {
    val plan = week(
      "Lunes" to Meal("Puré de calabaza", ""),
      "Martes" to Meal("pure de  calabaza", "")
    )
    val copy = MenuCopy.ofDay(plan, "Lunes", listOf("Martes"))

    assertEquals(CopyPreview(0, 0), copy.preview(plan))
    assertTrue(copy.applyTo(plan, CopyMode.REPLACE).changes.isEmpty())
  }

  @Test
  fun blankSourcesNeverClearTargets() {
    val previous = week("Lunes" to Meal("Sopa", ""), id = "2026-W47")
    val plan = week("Lunes" to Meal("", "Ensalada"))

    val edit = MenuCopy.ofWeek(previous).applyTo(plan, CopyMode.REPLACE)

    assertEquals(Meal("Sopa", "Ensalada"), edit.week.getMealForDay("Lunes"))
    assertEquals("2026-W48", edit.week.weekId)
  }

  @Test
  fun copiedDishesAreNormalizedToOneLine() {
    val plan = week("Lunes" to Meal("  Pollo\nal horno ", ""))
    val edit = MenuCopy.ofDay(plan, "Lunes", listOf("Domingo")).applyTo(plan, CopyMode.FILL_EMPTY)
    assertEquals("Pollo al horno", edit.week.at("Domingo", MealSlot.COMIDA))
  }

  @Test
  fun clearingADayOrTheWeekRecordsWhatWasThere() {
    val plan = week(
      "Lunes" to Meal("Lentejas", ""),
      "Jueves" to Meal("Arroz", "Sopa")
    )
    val day = plan.cleared(listOf("Jueves"))
    assertEquals(2, day.changes.size)
    assertTrue(day.week.isDayEmpty("Jueves"))
    assertFalse(day.week.isDayEmpty("Lunes"))

    val all = plan.cleared()
    assertEquals(3, all.changes.size)
    assertEquals(0, all.week.plannedCount())
    assertEquals(3, plan.plannedCount())
  }

  @Test
  fun undoRestoresOnlyMealsNotEditedSince() {
    val plan = week("Lunes" to Meal("Lentejas", "Tortilla"))
    val cleared = plan.cleared()
    val editedAgain = cleared.week.deepCopy().apply { setMealForDay("Lunes", Meal("", "Crema")) }

    val undo = editedAgain.reverted(cleared.changes)

    assertEquals(1, undo.changes.size)
    assertEquals(Meal("Lentejas", "Crema"), undo.week.getMealForDay("Lunes"))
  }

  @Test
  fun deepCopyToleratesWeeksReadWithNullFields() {
    val stored = WeeklyMeals("2026-W48")
    // Gson skips the constructor, so a stored week can hold null where Kotlin expects text.
    WeeklyMeals::class.java.getDeclaredField("meals").apply { isAccessible = true }.set(stored, null)

    val copy = stored.deepCopy()

    assertEquals(0, stored.plannedCount())
    assertEquals(WeeklyMeals.DAYS.size, copy.meals.size)
    assertEquals("", copy.at("Lunes", MealSlot.CENA))
  }
}
