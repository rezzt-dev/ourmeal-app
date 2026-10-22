package ourmeal.app

import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ourmeat.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ourmeal.app.controller.DishSuggestionManager
import ourmeal.app.controller.MealDataManager
import ourmeal.app.model.Meal
import ourmeal.app.model.WeekUtils
import ourmeal.app.model.WeeklyMeals
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MealSuggestionsTest {
  @get:Rule
  val appData = AppDataSandbox()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val meals = MealDataManager(context)
  private val settingsFile = File(context.filesDir, "dish_suggestions.json")
  private val currentWeek = WeekUtils.getCurrentWeekId()
  private val today = WeeklyMeals.DAYS[
    ChronoUnit.DAYS.between(WeekUtils.startOfWeek(currentWeek), LocalDate.now()).toInt()
  ]

  /** Two weeks far enough back that no dish counts as recent for today. */
  private fun seedHabitualDishes() {
    var weekId = currentWeek
    repeat(4) { index ->
      weekId = WeekUtils.getPreviousWeek(weekId)
      if (index < 2) return@repeat
      meals.saveWeeklyMeal(WeeklyMeals(weekId).apply {
        setMealForDay("Lunes", Meal(comida = "Lentejas"))
        setMealForDay("Martes", Meal(comida = "Albóndigas en salsa"))
        if (index == 3) setMealForDay("Miércoles", Meal(cena = "Pasta"))
      })
    }
  }

  @Test
  fun focusShowsHabitualsAndTypingFilters() {
    seedHabitualDishes()
    launchMenu { scenario ->
      scenario.onActivity { it.lunch().requestFocus() }
      awaitChips(scenario, listOf("Albóndigas en salsa", "Lentejas"))
      scenario.onActivity { it.lunch().setText("alb") }
      awaitChips(scenario, listOf("Albóndigas en salsa"))
      scenario.onActivity { it.lunch().setText("zzz") }
      eventually(scenario) { !it.lunchStrip().isVisible }
    }
  }

  @Test
  fun pickingFillsTheFieldHidesTheStripAndSaves() {
    seedHabitualDishes()
    launchMenu { scenario ->
      scenario.onActivity { it.lunch().requestFocus() }
      awaitChips(scenario, listOf("Albóndigas en salsa", "Lentejas"))
      scenario.onActivity {
        it.lunchChips().first().performClick()
        assertEquals("Albóndigas en salsa", it.lunch().text.toString())
        assertTrue(it.lunch().hasFocus())
        assertFalse(it.lunchStrip().isVisible)
      }
      eventually(scenario) { savedLunch() == "Albóndigas en salsa" }
    }
  }

  @Test
  fun sameDishTwiceInTheWeekShowsAHint() {
    launchMenu { scenario ->
      scenario.onActivity {
        it.lunch().setText("Lentejas")
        it.dinner().setText("lentejas")
        val expected = it.getString(
          R.string.history_repeat_same_week,
          today.lowercase(Locale.forLanguageTag("es-ES")),
          it.getString(R.string.day_lunch).lowercase(Locale.forLanguageTag("es-ES"))
        )
        assertEquals(expected, it.dinnerHint().text.toString())
        assertTrue(it.dinnerHint().isVisible)
      }
    }
  }

  @Test
  fun dishFromLastWeekShowsRecentHintAndSurvivesRecreation() {
    meals.saveWeeklyMeal(WeeklyMeals(WeekUtils.getPreviousWeek(currentWeek)).apply {
      setMealForDay(today, Meal(cena = "Tortilla"))
    })
    launchMenu { scenario ->
      val expected = context.resources.getQuantityString(R.plurals.history_repeat_recent, 7, 7)
      scenario.onActivity {
        it.lunch().setText("Tortilla")
        assertEquals(expected, it.lunchHint().text.toString())
      }
      eventually(scenario) { savedLunch() == "Tortilla" }
      scenario.recreate()
      awaitMenu(scenario)
      scenario.onActivity {
        assertEquals(expected, it.lunchHint().text.toString())
        assertTrue(it.lunchHint().isVisible)
      }
    }
  }

  @Test
  fun longPressHidesASuggestionAndUndoRestoresIt() {
    seedHabitualDishes()
    launchMenu { scenario ->
      scenario.onActivity { it.lunch().requestFocus() }
      awaitChips(scenario, listOf("Albóndigas en salsa", "Lentejas"))
      scenario.onActivity { it.lunchChips().last().performLongClick() }
      awaitChips(scenario, listOf("Albóndigas en salsa"))
      eventually(scenario) { DishSuggestionManager(context).load().hidden == listOf("lentejas") }
      eventually(scenario) { it.findViewById<View>(com.google.android.material.R.id.snackbar_action) != null }
      scenario.onActivity {
        it.findViewById<View>(com.google.android.material.R.id.snackbar_action).performClick()
      }
      awaitChips(scenario, listOf("Albóndigas en salsa", "Lentejas"))
      eventually(scenario) { DishSuggestionManager(context).load().hidden.isEmpty() }
    }
  }

  @Test
  fun writingAHiddenDishAgainUnhidesIt() {
    seedHabitualDishes()
    settingsFile.writeText("""{"version":1,"hidden":["lentejas"]}""")
    launchMenu { scenario ->
      scenario.onActivity { it.lunch().requestFocus() }
      awaitChips(scenario, listOf("Albóndigas en salsa"))
      scenario.onActivity { it.lunch().setText("Lentejas") }
      eventually(scenario) { savedLunch() == "Lentejas" }
      eventually(scenario) { DishSuggestionManager(context).load().hidden.isEmpty() }
    }
  }

  @Test
  fun damagedSettingsKeepSuggestionsButNeverOverwriteTheFile() {
    seedHabitualDishes()
    settingsFile.writeText("esto no es json")
    launchMenu { scenario ->
      scenario.onActivity { it.lunch().requestFocus() }
      awaitChips(scenario, listOf("Albóndigas en salsa", "Lentejas"))
      scenario.onActivity { assertFalse(it.lunchChips().last().performLongClick()) }
      InstrumentationRegistry.getInstrumentation().waitForIdleSync()
      scenario.onActivity { assertEquals(listOf("Albóndigas en salsa", "Lentejas"), it.lunchChipNames()) }
      scenario.onActivity { it.lunch().setText("Lentejas") }
      eventually(scenario) { savedLunch() == "Lentejas" }
      assertEquals("esto no es json", settingsFile.readText())
    }
  }

  @Test
  fun noHistoryLeavesTheBoardAsBefore() {
    launchMenu { scenario ->
      scenario.onActivity {
        it.lunch().requestFocus()
        it.lunch().setText("Pa")
      }
      InstrumentationRegistry.getInstrumentation().waitForIdleSync()
      scenario.onActivity {
        assertFalse(it.lunchStrip().isVisible)
        assertFalse(it.lunchHint().isVisible)
      }
    }
  }

  private fun savedLunch(): String? =
    meals.loadWeeklyMeals().find { it.weekId == currentWeek }?.getMealForDay(today)?.comida

  private fun MainActivity.lunch() = findViewById<EditText>(R.id.etComida)
  private fun MainActivity.dinner() = findViewById<EditText>(R.id.etCena)
  private fun MainActivity.lunchStrip() = findViewById<View>(R.id.comidaSuggestionsScroll)
  private fun MainActivity.lunchHint() = findViewById<TextView>(R.id.tvComidaRepeat)
  private fun MainActivity.dinnerHint() = findViewById<TextView>(R.id.tvCenaRepeat)
  private fun MainActivity.lunchChips(): List<Chip> =
    findViewById<ChipGroup>(R.id.chipsComidaSuggestions).children.filterIsInstance<Chip>().toList()
  private fun MainActivity.lunchChipNames(): List<String> = lunchChips().map { it.text.toString() }

  private fun launchMenu(block: (ActivityScenario<MainActivity>) -> Unit) {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      awaitMenu(scenario)
      block(scenario)
    }
  }

  private fun awaitMenu(scenario: ActivityScenario<MainActivity>) =
    eventually(scenario) { it.findViewById<View>(R.id.menuBoard).isVisible }

  private fun awaitChips(scenario: ActivityScenario<MainActivity>, names: List<String>) =
    eventually(scenario) { it.lunchStrip().isVisible && it.lunchChipNames() == names }

  // Avoid Espresso input injection, which is incompatible with API 37; see CreationFormTest.
  private fun eventually(
    scenario: ActivityScenario<MainActivity>,
    condition: (MainActivity) -> Boolean
  ) {
    repeat(50) {
      var met = false
      scenario.onActivity { met = condition(it) }
      if (met) return
      android.os.SystemClock.sleep(100)
    }
    throw AssertionError("Condition not met in time")
  }
}
