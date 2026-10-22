package ourmeal.app

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ourmeat.R
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ourmeal.app.controller.DataManager
import ourmeal.app.controller.ShoppingRouteManager
import ourmeal.app.model.ListItem
import ourmeal.app.model.RouteStop
import ourmeal.app.model.RouteTick
import ourmeal.app.model.ShoppingRoute
import ourmeal.app.model.UserList
import java.io.File

@RunWith(AndroidJUnit4::class)
class ShoppingRouteOrderTest {
  @get:Rule
  val appData = AppDataSandbox()

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext
  private val routeFile = File(context.filesDir, "shopping_route.json")
  private val routes = ShoppingRouteManager(context)
  private val listId = 7L

  private fun seedList(vararg items: ListItem) {
    DataManager(context).saveLists(listOf(UserList(listId, "Compra", items.toMutableList(), createdAt = 0L)))
  }

  private fun seedRoute(route: ShoppingRoute) = routeFile.writeText(Gson().toJson(route))

  private fun item(id: Long, name: String, bought: Boolean = false) =
    ListItem(id, name, isCompleted = bought, createdAt = id)

  private val learnedRoute = ShoppingRoute(stops = listOf(
    RouteStop("platano", 0.1, trips = 2, updatedAt = 1L),
    RouteStop("leche", 0.9, trips = 2, updatedAt = 1L)
  ))

  @Test
  fun pendingProductsFollowTheLearnedRoute() {
    seedList(item(1, "Leche"), item(2, "Detergente"), item(3, "Pan", bought = true), item(4, "Plátano"))
    seedRoute(learnedRoute)
    launchDetail { scenario ->
      assertShown(scenario, "Plátano", "Leche", "Detergente", "Pan")
      scenario.onActivity {
        assertTrue(it.findViewById<MaterialSwitch>(R.id.switchRouteOrder).isChecked)
        assertTrue(it.findViewById<View>(R.id.btnForgetRoute).isVisible)
        assertEquals(
          context.resources.getQuantityString(R.plurals.route_status_partial, 3, 2, 3),
          it.subtitle()
        )
      }
    }
  }

  @Test
  fun switchingOffKeepsTheListOrderAndIsRemembered() {
    seedList(item(1, "Leche"), item(2, "Plátano"))
    seedRoute(learnedRoute)
    launchDetail { scenario ->
      assertShown(scenario, "Plátano", "Leche")
      scenario.onActivity {
        it.findViewById<View>(R.id.switchRouteOrder).performClick()
        assertEquals(context.getString(R.string.route_status_list_order), it.subtitle())
      }
      assertShown(scenario, "Leche", "Plátano")
      waitFor { !routes.load().sortByRoute }
    }
    launchDetail { scenario ->
      assertShown(scenario, "Leche", "Plátano")
      scenario.onActivity { assertFalse(it.findViewById<MaterialSwitch>(R.id.switchRouteOrder).isChecked) }
    }
  }

  @Test
  fun aMarkedProductMovesDownAndJoinsTheOpenTrip() {
    seedList(item(1, "Pan"), item(2, "Leche"), item(3, "Huevos"))
    launchDetail { scenario ->
      assertShown(scenario, "Pan", "Leche", "Huevos")
      scenario.onActivity {
        assertEquals(context.getString(R.string.route_status_learning), it.subtitle())
        assertFalse(it.findViewById<View>(R.id.btnForgetRoute).isVisible)
        it.checkBoxAt(0).performClick()
      }
      assertShown(scenario, "Leche", "Huevos", "Pan")
      waitFor { routes.load().trip.map { it.key } == listOf("pan") }
      scenario.onActivity { it.checkBoxAt(2).performClick() }
      waitFor { routes.load().trip.isEmpty() }
      assertShown(scenario, "Pan", "Leche", "Huevos")
    }
    assertEquals(listOf(false, false, false), DataManager(context).loadLists().single().items.map { it.isCompleted })
  }

  @Test
  fun aFinishedTripIsLearnedWhenTheListOpens() {
    val start = System.currentTimeMillis() - 3 * 60 * 60 * 1000L
    seedList(item(1, "Leche"), item(2, "Pan"), item(3, "Fruta"))
    seedRoute(ShoppingRoute(trip = listOf(
      RouteTick("fruta", start),
      RouteTick("pan", start + 3 * 60_000L),
      RouteTick("leche", start + 6 * 60_000L)
    )))
    launchDetail { scenario ->
      assertEquals(
        context.resources.getQuantityString(R.plurals.route_learned, 3, 3),
        awaitSnackbar(scenario)
      )
      assertShown(scenario, "Fruta", "Pan", "Leche")
      scenario.onActivity { assertEquals(context.getString(R.string.route_status_all), it.subtitle()) }
    }
    assertTrue(routes.load().trip.isEmpty())
    assertEquals(setOf("fruta", "pan", "leche"), routes.load().positions().keys)
  }

  @Test
  fun aDamagedRouteFileIsNeitherUsedNorOverwritten() {
    seedList(item(1, "Pan", bought = true), item(2, "Leche"))
    routeFile.writeText("{dañado")
    launchDetail { scenario ->
      assertShown(scenario, "Pan", "Leche")
      scenario.onActivity {
        assertFalse(it.findViewById<View>(R.id.routeOrder).isVisible)
        assertEquals(context.getString(R.string.route_status_unavailable), it.subtitle())
        it.checkBoxAt(1).performClick()
      }
      waitFor { DataManager(context).loadLists().single().items.all { it.isCompleted } }
    }
    assertEquals("{dañado", routeFile.readText())
  }

  @Test
  fun forgettingTheRouteCanBeUndone() {
    seedList(item(1, "Leche"), item(2, "Plátano"))
    seedRoute(learnedRoute)
    launchDetail { scenario ->
      assertShown(scenario, "Plátano", "Leche")
      scenario.onActivity { it.findViewById<View>(R.id.btnForgetRoute).performClick() }
      waitFor { onMain { dialogButton(android.R.id.button1) != null } }
      onMain { dialogButton(android.R.id.button1)!!.performClick() }
      waitFor { !routes.load().hasStops }
      assertShown(scenario, "Leche", "Plátano")
      assertEquals(context.getString(R.string.route_forgotten), awaitSnackbar(scenario))
      onMain { dialogButton(com.google.android.material.R.id.snackbar_action)!!.performClick() }
      waitFor { routes.load().stops == learnedRoute.stops }
      assertShown(scenario, "Plátano", "Leche")
    }
  }

  /** Names in adapter order; empty until every row has been laid out. */
  private fun ListDetailActivity.shownNames(): List<String> {
    val recycler = findViewById<RecyclerView>(R.id.recyclerViewItems)
    val count = recycler.adapter?.itemCount ?: 0
    return (0 until count).map { position ->
      val holder = recycler.findViewHolderForAdapterPosition(position) ?: return emptyList()
      holder.itemView.findViewById<TextView>(R.id.tvItemName).text.toString()
    }
  }

  private fun assertShown(scenario: ActivityScenario<ListDetailActivity>, vararg names: String) =
    eventually(scenario) { it.shownNames() == names.toList() }

  private fun ListDetailActivity.checkBoxAt(position: Int): MaterialCheckBox =
    findViewById<RecyclerView>(R.id.recyclerViewItems).findViewHolderForAdapterPosition(position)!!
      .itemView.findViewById(R.id.cbItemCompleted)

  private fun ListDetailActivity.subtitle(): String =
    findViewById<TextView>(R.id.tvListSubtitle).text.toString()

  private fun awaitSnackbar(scenario: ActivityScenario<ListDetailActivity>): String {
    var text = ""
    eventually(scenario) {
      text = it.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text?.toString().orEmpty()
      text.isNotEmpty()
    }
    return text
  }

  private fun launchDetail(block: (ActivityScenario<ListDetailActivity>) -> Unit) {
    val intent = Intent(context, ListDetailActivity::class.java).putExtra(ListDetailActivity.EXTRA_LIST_ID, listId)
    ActivityScenario.launch<ListDetailActivity>(intent).use { scenario ->
      eventually(scenario) { it.findViewById<View>(R.id.recyclerViewItems).isVisible }
      block(scenario)
    }
  }

  private fun dialogButton(id: Int): View? =
    android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { it.findViewById<View>(id) }

  // Avoid Espresso input injection, which is incompatible with API 37; see CreationFormTest.
  private fun eventually(
    scenario: ActivityScenario<ListDetailActivity>,
    condition: (ListDetailActivity) -> Boolean
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
