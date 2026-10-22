package ourmeal.app

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ourmeat.R
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ourmeal.app.controller.DataManager
import ourmeal.app.controller.DishMemoryManager
import ourmeal.app.controller.MealDataManager
import ourmeal.app.model.DishIngredients
import ourmeal.app.model.ListItem
import ourmeal.app.model.Meal
import ourmeal.app.model.UserList
import ourmeal.app.model.WeekUtils
import ourmeal.app.model.WeeklyMeals
import java.io.File

@RunWith(AndroidJUnit4::class)
class MenuShoppingTest {
  @get:Rule
  val appData = AppDataSandbox()

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext
  private val meals = MealDataManager(context)
  private val lists = DataManager(context)
  private val memoryFile = File(context.filesDir, "dish_ingredients.json")
  private val weekId = "2026-W43"

  private fun seedWeek(vararg dishes: Pair<String, Meal>) {
    meals.saveWeeklyMeal(WeeklyMeals(weekId).apply { dishes.forEach { (day, meal) -> setMealForDay(day, meal) } })
  }

  @Test
  fun newListGetsTheIngredientsAndMemoryRemembersThem() {
    seedWeek("Lunes" to Meal(comida = "Lentejas"), "Martes" to Meal(cena = "Tortilla"))
    launchShopping { scenario ->
      scenario.onActivity {
        assertEquals(
          context.getString(R.string.menu_shopping_default_list_name, WeekUtils.getWeekDisplay(weekId)),
          it.findViewById<EditText>(R.id.etNewListName).text.toString()
        )
        assertFalse(it.findViewById<View>(R.id.btnConfirmShopping).isEnabled)
        it.addIngredients(0, "Lentejas", "Chorizo")
        it.addIngredients(1, "Huevos", "aceite", "Aceite")
        assertEquals(listOf("Huevos", "aceite"), it.chipNames(1))
        assertEquals(
          context.getString(R.string.menu_shopping_ingredient_exists),
          it.card(1).findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.ingredientField)
            .error.toString()
        )
        assertTrue(it.card(1).findViewById<EditText>(R.id.etIngredient).isEnabled)
        assertEquals(
          context.resources.getQuantityString(R.plurals.menu_shopping_confirm, 4, 4),
          it.findViewById<TextView>(R.id.btnConfirmShopping).text.toString()
        )
      }
      withDetail(confirm(scenario)) { detail ->
        assertEquals(
          context.resources.getQuantityString(R.plurals.menu_shopping_added, 4, 4),
          detail.awaitSnackbar()
        )
      }
    }
    val list = lists.loadLists().single()
    assertEquals(
      context.getString(R.string.menu_shopping_default_list_name, WeekUtils.getWeekDisplay(weekId)),
      list.name
    )
    assertEquals(listOf("Lentejas", "Chorizo", "Huevos", "aceite"), list.items.map { it.name })
    val memory = DishMemoryManager(context).load()
    assertEquals(listOf("Lentejas", "Chorizo"), memory.ingredientsFor("lentejas"))
    assertEquals(listOf("Huevos", "aceite"), memory.ingredientsFor("tortilla"))
  }

  @Test
  fun rememberedIngredientsArePreloaded() {
    seedWeek("Lunes" to Meal(comida = "lentejas"), "Martes" to Meal(comida = "Sopa"))
    DishMemoryManager(context).save(listOf(DishIngredients("lentejas", "Lentejas", listOf("Lentejas", "Chorizo"), 1L)))
    launchShopping { scenario ->
      scenario.onActivity {
        assertEquals(listOf("Lentejas", "Chorizo"), it.chipNames(0))
        assertTrue(it.card(0).findViewById<View>(R.id.tvRememberedBadge).isVisible)
        assertFalse(it.card(1).findViewById<View>(R.id.tvRememberedBadge).isVisible)
        assertTrue(it.card(1).findViewById<View>(R.id.tvNoIngredients).isVisible)
        assertTrue(it.findViewById<View>(R.id.btnConfirmShopping).isEnabled)
      }
    }
  }

  @Test
  fun existingListSkipsWhatItAlreadyHolds() {
    seedWeek("Lunes" to Meal(comida = "Batido"))
    DishMemoryManager(context).save(listOf(DishIngredients("batido", "Batido", listOf("platano", "Leche"), 1L)))
    lists.saveLists(listOf(
      UserList(7L, "Compra semanal", mutableListOf(ListItem(1L, "Plátano", createdAt = 0L)), createdAt = 0L)
    ))
    launchShopping { scenario ->
      scenario.onActivity {
        it.findViewById<View>(R.id.rbExistingList).performClick()
        assertEquals(context.getString(R.string.menu_shopping_pick_list), it.summary())
        assertFalse(it.findViewById<View>(R.id.btnConfirmShopping).isEnabled)
        val dropdown = it.findViewById<android.widget.AutoCompleteTextView>(R.id.actvExistingList)
        dropdown.onItemClickListener.onItemClick(null, null, 0, 0L)
        val expected = context.getString(
          R.string.menu_shopping_summary_pair,
          context.resources.getQuantityString(R.plurals.menu_shopping_summary_new, 1, 1),
          context.resources.getQuantityString(R.plurals.menu_shopping_summary_skipped, 1, 1)
        )
        assertEquals(expected, it.summary())
      }
      withDetail(confirm(scenario)) { detail ->
        val expected = context.resources.getQuantityString(R.plurals.menu_shopping_added, 1, 1) + " " +
          context.resources.getQuantityString(R.plurals.menu_shopping_result_skipped, 1, 1)
        assertEquals(expected, detail.awaitSnackbar())
      }
    }
    assertEquals(listOf("Leche", "Plátano"), lists.loadLists().single().items.map { it.name })
  }

  @Test
  fun excludedDishAddsNothing() {
    seedWeek("Lunes" to Meal(comida = "Lentejas", cena = "Pizza"))
    launchShopping { scenario ->
      scenario.onActivity {
        it.addIngredients(0, "Chorizo")
        it.addIngredients(1, "Masa")
        it.card(1).findViewById<MaterialCheckBox>(R.id.cbIncludeDish).performClick()
        assertFalse(it.card(1).findViewById<View>(R.id.dishBody).isVisible)
        assertTrue(
          it.card(1).findViewById<TextView>(R.id.tvDishOccurrences).text
            .endsWith(context.getString(R.string.menu_shopping_excluded))
        )
      }
      withDetail(confirm(scenario)) {}
    }
    assertEquals(listOf("Chorizo"), lists.loadLists().single().items.map { it.name })
    // What was written for the excluded dish is still remembered for next time.
    assertEquals(listOf("Masa"), DishMemoryManager(context).load().ingredientsFor("pizza"))
  }

  @Test
  fun draftSurvivesRecreation() {
    seedWeek("Lunes" to Meal(comida = "Lentejas", cena = "Pizza"))
    launchShopping { scenario ->
      scenario.onActivity {
        it.addIngredients(0, "Chorizo", "Zanahoria")
        it.card(1).findViewById<MaterialCheckBox>(R.id.cbIncludeDish).performClick()
        it.findViewById<EditText>(R.id.etNewListName).setText("Compra del sábado")
      }
      scenario.recreate()
      awaitReady(scenario)
      scenario.onActivity {
        assertEquals(listOf("Chorizo", "Zanahoria"), it.chipNames(0))
        assertFalse(it.card(1).findViewById<MaterialCheckBox>(R.id.cbIncludeDish).isChecked)
        assertEquals("Compra del sábado", it.findViewById<EditText>(R.id.etNewListName).text.toString())
      }
    }
  }

  @Test
  fun leavingWithEditsAsksFirstAndWithoutEditsDoesNot() {
    seedWeek("Lunes" to Meal(comida = "Lentejas"))
    launchShopping { scenario ->
      scenario.onActivity {
        it.addIngredients(0, "Chorizo")
        it.onBackPressedDispatcher.onBackPressed()
        assertFalse(it.isFinishing)
      }
      instrumentation.waitForIdleSync()
      scenario.onActivity { dialogButton(android.R.id.button2).performClick() }
      scenario.onActivity {
        it.card(0).findViewById<ChipGroup>(R.id.chipGroupIngredients).children
          .filterIsInstance<Chip>().single().performCloseIconClick()
        it.onBackPressedDispatcher.onBackPressed()
      }
      waitFor { scenario.state == Lifecycle.State.DESTROYED }
    }
  }

  @Test
  fun weekWithoutDishesShowsTheEmptyState() {
    launchShopping(awaitContent = false) { scenario ->
      eventually(scenario) { it.findViewById<View>(R.id.statePanel).isVisible }
      scenario.onActivity {
        assertEquals(context.getString(R.string.menu_shopping_empty_title), it.stateTitle())
        assertFalse(it.findViewById<View>(R.id.actionBar).isVisible)
      }
    }
  }

  @Test
  fun invalidWeekShowsItsOwnState() {
    launchShopping(week = "2021-W53", awaitContent = false) { scenario ->
      eventually(scenario) { it.findViewById<View>(R.id.statePanel).isVisible }
      scenario.onActivity {
        assertEquals(context.getString(R.string.menu_shopping_invalid_week), it.stateTitle())
        it.findViewById<View>(R.id.btnStateAction).performClick()
      }
      waitFor { scenario.state == Lifecycle.State.DESTROYED }
    }
  }

  @Test
  fun damagedMemoryIsNeverOverwritten() {
    seedWeek("Lunes" to Meal(comida = "Lentejas"))
    memoryFile.writeText("esto no es json")
    launchShopping { scenario ->
      scenario.onActivity {
        assertTrue(it.findViewById<View>(R.id.tvMemoryUnavailable).isVisible)
        it.addIngredients(0, "Chorizo")
      }
      withDetail(confirm(scenario)) {}
    }
    assertEquals(listOf("Chorizo"), lists.loadLists().single().items.map { it.name })
    assertEquals("esto no es json", memoryFile.readText())
  }

  @Test
  fun menuButtonFollowsThePlannedDishesAndOpensTheScreen() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      waitFor {
        var shown = false
        scenario.onActivity { shown = it.findViewById<View>(R.id.btnPrepareShopping).isShown }
        shown
      }
      scenario.onActivity {
        assertFalse(it.findViewById<View>(R.id.btnPrepareShopping).isEnabled)
        assertTrue(it.findViewById<View>(R.id.tvPrepareShoppingHint).isVisible)
        it.findViewById<EditText>(R.id.etComida).setText("Lentejas")
        assertTrue(it.findViewById<View>(R.id.btnPrepareShopping).isEnabled)
        assertFalse(it.findViewById<View>(R.id.tvPrepareShoppingHint).isVisible)
      }
      val monitor = instrumentation.addMonitor(MenuShoppingActivity::class.java.name, null, false)
      scenario.onActivity { it.findViewById<View>(R.id.btnPrepareShopping).performClick() }
      val shopping = instrumentation.waitForMonitorWithTimeout(monitor, 5_000) as MenuShoppingActivity?
      instrumentation.removeMonitor(monitor)
      checkNotNull(shopping) { "The shopping screen did not open" }
      // The week was written before the screen opened, so the dish is already there.
      waitFor { onMain { shopping.findViewById<View>(R.id.actionBar).isVisible } }
      instrumentation.runOnMainSync {
        assertEquals("Lentejas", shopping.card(0).findViewById<TextView>(R.id.tvDishName).text.toString())
        shopping.finish()
      }
    }
  }

  private fun MenuShoppingActivity.card(index: Int): View =
    findViewById<ViewGroup>(R.id.layoutDishes).getChildAt(index)

  private fun MenuShoppingActivity.chipNames(index: Int): List<String> =
    card(index).findViewById<ChipGroup>(R.id.chipGroupIngredients).children
      .filterIsInstance<Chip>().map { it.text.toString() }.toList()

  private fun MenuShoppingActivity.addIngredients(index: Int, vararg names: String) {
    val input = card(index).findViewById<EditText>(R.id.etIngredient)
    names.forEach {
      input.setText(it)
      input.onEditorAction(EditorInfo.IME_ACTION_DONE)
    }
  }

  private fun MenuShoppingActivity.summary(): String =
    findViewById<TextView>(R.id.tvShoppingSummary).text.toString()

  private fun MenuShoppingActivity.stateTitle(): String =
    findViewById<TextView>(R.id.tvStateTitle).text.toString()

  private fun ListDetailActivity.awaitSnackbar(): String {
    var text = ""
    waitFor {
      onMain { text = findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text?.toString().orEmpty() }
      text.isNotEmpty()
    }
    return text
  }

  private fun launchShopping(
    week: String = weekId,
    awaitContent: Boolean = true,
    block: (ActivityScenario<MenuShoppingActivity>) -> Unit
  ) {
    val intent = Intent(context, MenuShoppingActivity::class.java)
      .putExtra(MenuShoppingActivity.EXTRA_WEEK_ID, week)
    ActivityScenario.launch<MenuShoppingActivity>(intent).use { scenario ->
      if (awaitContent) awaitReady(scenario)
      block(scenario)
    }
  }

  private fun awaitReady(scenario: ActivityScenario<MenuShoppingActivity>) =
    eventually(scenario) { it.findViewById<View>(R.id.actionBar).isVisible }

  /** Confirms and returns the list screen it opens; the shopping screen closes itself. */
  private fun confirm(scenario: ActivityScenario<MenuShoppingActivity>): ListDetailActivity {
    val monitor = instrumentation.addMonitor(ListDetailActivity::class.java.name, null, false)
    scenario.onActivity { it.findViewById<View>(R.id.btnConfirmShopping).performClick() }
    val detail = instrumentation.waitForMonitorWithTimeout(monitor, 5_000) as ListDetailActivity?
    instrumentation.removeMonitor(monitor)
    waitFor { scenario.state == Lifecycle.State.DESTROYED }
    return checkNotNull(detail) { "The list did not open" }
  }

  /** Runs [block] on the opened list and closes it, so the next test starts clean. */
  private fun withDetail(detail: ListDetailActivity, block: (ListDetailActivity) -> Unit) {
    try {
      block(detail)
    } finally {
      instrumentation.runOnMainSync { detail.finish() }
      waitFor { onMain { detail.isDestroyed } }
    }
  }

  private fun dialogButton(id: Int): View =
    android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOf { it.findViewById<View>(id) }

  // Avoid Espresso input injection, which is incompatible with API 37; see CreationFormTest.
  private fun eventually(
    scenario: ActivityScenario<MenuShoppingActivity>,
    condition: (MenuShoppingActivity) -> Boolean
  ) = waitFor {
    var met = false
    scenario.onActivity { met = condition(it) }
    met
  }

  /** Polls from the test thread; conditions that touch views go through [onMain]. */
  private fun waitFor(condition: () -> Boolean) {
    repeat(50) {
      if (condition()) return
      android.os.SystemClock.sleep(100)
    }
    throw AssertionError("Condition not met in time")
  }

  private fun <T> onMain(block: () -> T): T {
    var result: T? = null
    instrumentation.runOnMainSync { result = block() }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }
}
