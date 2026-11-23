package ourmeal.app

import android.graphics.BitmapFactory
import android.view.View
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ourmeat.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ourmeal.app.Ui.click
import ourmeal.app.Ui.clickEntry
import ourmeal.app.Ui.entryEnabled
import ourmeal.app.Ui.eventually
import ourmeal.app.Ui.onMain
import ourmeal.app.Ui.snackbarText
import ourmeal.app.Ui.waitFor
import ourmeal.app.controller.MealDataManager
import ourmeal.app.model.Meal
import ourmeal.app.model.SharedDay
import ourmeal.app.model.WeekUtils
import ourmeal.app.model.WeeklyMeals
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MenuToolsTest {
  @get:Rule
  val appData = AppDataSandbox()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val meals = MealDataManager(context)
  private val weekId = WeekUtils.getCurrentWeekId()
  private val previousId = WeekUtils.getPreviousWeek(weekId)
  /** The menu opens on today, so the day tools act on it. */
  private val today = WeeklyMeals.DAYS[
    ChronoUnit.DAYS.between(WeekUtils.startOfWeek(weekId), LocalDate.now()).toInt()
  ]
  private val todayName = today.lowercase(Locale.forLanguageTag("es-ES"))

  private fun seed(id: String, vararg days: Pair<String, Meal>) {
    meals.saveWeeklyMeal(WeeklyMeals(id).apply { days.forEach { (day, meal) -> setMealForDay(day, meal) } })
  }

  private fun stored(day: String): Meal =
    meals.loadWeeklyMeals().first { it.weekId == weekId }.getMealForDay(day)

  @Test
  fun anEmptyWeekCannotBeSharedAndSaysWhy() {
    launchMenu { scenario ->
      scenario.onActivity {
        assertFalse(it.findViewById<View>(R.id.btnShareMenu).isEnabled)
        assertTrue(it.findViewById<View>(R.id.tvPrepareShoppingHint).isVisible)
      }
      click(R.id.btnMenuTools)
      waitFor { entryEnabled(context.getString(R.string.menu_clear_week_empty)) != null }
      assertEquals(false, entryEnabled(context.getString(R.string.menu_clear_week_empty)))
      assertEquals(false, entryEnabled(context.getString(R.string.menu_copy_previous_week_empty)))
    }
  }

  @Test
  fun copyingThePreviousWeekCanKeepWhatIsWritten() {
    seed(previousId, today to Meal("Lentejas", "Tortilla"))
    seed(weekId, today to Meal("", "Pescado"))
    launchMenu { scenario ->
      scenario.onActivity { assertTrue(it.findViewById<View>(R.id.btnShareMenu).isEnabled) }
      click(R.id.btnMenuTools)
      clickEntry(context.getString(R.string.menu_copy_previous_week))
      // One meal would be overwritten: the user chooses to fill only the blank one.
      click(android.R.id.button3)
      waitFor { stored(today) == Meal("Lentejas", "Pescado") }
      assertEquals(context.resources.getQuantityString(R.plurals.menu_copied, 1, 1), awaitSnackbar())
      scenario.onActivity {
        assertEquals("Lentejas", it.findViewById<EditText>(R.id.etComida).text.toString())
      }
    }
  }

  @Test
  fun aDayIsCopiedToTheChosenDays() {
    seed(weekId, today to Meal("Arroz", "Crema"))
    val target = WeeklyMeals.DAYS.first { it != today }
    launchMenu { _ ->
      click(R.id.btnMenuTools)
      clickEntry(context.getString(R.string.menu_copy_day, todayName))
      waitFor { onMain { Ui.windowView<View>(android.R.id.button1) != null } }
      assertFalse(onMain { Ui.windowView<View>(android.R.id.button1)!!.isEnabled })
      val start = WeekUtils.startOfWeek(weekId)
      val title = "$target ${start.plusDays(WeeklyMeals.DAYS.indexOf(target).toLong()).dayOfMonth}"
      clickEntry(context.getString(R.string.menu_copy_target_empty, title))
      click(android.R.id.button1)
      waitFor { stored(target) == Meal("Arroz", "Crema") }
    }
  }

  @Test
  fun clearingTheDayCanBeUndone() {
    seed(weekId, today to Meal("Sopa", "Pizza"))
    launchMenu { _ ->
      click(R.id.btnMenuTools)
      clickEntry(context.getString(R.string.menu_clear_day, todayName))
      waitFor { stored(today) == Meal("", "") }
      assertEquals(context.getString(R.string.menu_cleared_day, todayName), awaitSnackbar())
      click(com.google.android.material.R.id.snackbar_action)
      waitFor { stored(today) == Meal("Sopa", "Pizza") }
    }
  }

  @Test
  fun theMenuImageIsDrawnAndSharedThroughTheFileProvider() {
    val days = WeeklyMeals.DAYS.map { SharedDay(it, "Merluza en salsa verde con almejas y patatas panaderas", "—") }
    val bitmap = onMain { context.renderMenuCard("23 nov – 29 nov 2026", days) }
    val expectedWidth = (400 * context.resources.displayMetrics.density).toInt()
    assertEquals(expectedWidth, bitmap.width)
    assertTrue(bitmap.height > bitmap.width)

    val uri = context.cacheSharedImage(bitmap, "menu.png")
    assertEquals("content", uri.scheme)
    assertEquals("${context.packageName}.fileprovider", uri.authority)
    val decoded = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
    assertNotNull(decoded)
    assertEquals(bitmap.width, decoded.width)
  }

  private fun awaitSnackbar(): String {
    waitFor { snackbarText().isNotEmpty() }
    return snackbarText()
  }

  private fun launchMenu(block: (ActivityScenario<MainActivity>) -> Unit) {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      eventually(scenario) { it.findViewById<View>(R.id.btnMenuTools).isEnabled }
      block(scenario)
    }
  }
}
