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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ourmeal.app.Ui.click
import ourmeal.app.Ui.eventually
import ourmeal.app.Ui.snackbarText
import ourmeal.app.Ui.waitFor
import ourmeal.app.controller.DataManager
import ourmeal.app.controller.PurchaseHistoryManager
import ourmeal.app.model.ListItem
import ourmeal.app.model.PurchaseHistory
import ourmeal.app.model.PurchaseTrip
import ourmeal.app.model.PurchasedItem
import ourmeal.app.model.UserList
import java.io.File

@RunWith(AndroidJUnit4::class)
class PurchaseHistoryFlowTest {
  @get:Rule
  val appData = AppDataSandbox()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val lists = DataManager(context)
  private val history = PurchaseHistoryManager(context)
  private val historyFile = File(context.filesDir, "purchase_history.json")
  private val listId = 7L
  private val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L

  @After
  fun closeOpenedScreens() = Ui.finishResumedActivities()

  private fun finishedTrip() = PurchaseTrip(
    id = dayAgo,
    listId = 99L,
    listName = "Semana",
    items = listOf(PurchasedItem(1, "Leche", 110, dayAgo), PurchasedItem(2, "Pan", null, dayAgo + 600_000))
  )

  @Test
  fun markedProductsBecomeATripThatOutlivesItsList() {
    lists.saveLists(listOf(UserList(listId, "Compra", mutableListOf(
      ListItem(1, "Pan", createdAt = 1), ListItem(2, "Leche", createdAt = 2, priceCents = 120)
    ), createdAt = 0L)))
    val detail = Intent(context, ListDetailActivity::class.java).putExtra(ListDetailActivity.EXTRA_LIST_ID, listId)
    ActivityScenario.launch<ListDetailActivity>(detail).use { scenario ->
      eventually(scenario) { it.findViewById<RecyclerView>(R.id.recyclerViewItems).childCount == 2 }
      scenario.onActivity { it.checkBox("Leche").performClick() }
      waitFor { history.load().open.singleOrNull()?.items?.size == 1 }
      scenario.onActivity { it.checkBox("Pan").performClick() }
      waitFor { history.load().open.singleOrNull()?.items?.size == 2 }
    }
    val trip = history.load().open.single()
    assertEquals(listOf("Leche", "Pan"), trip.items.map { it.name })
    assertEquals(120L, trip.items.first().priceCents)

    assertTrue(lists.deleteList(listId))
    launchHistory { scenario ->
      eventually(scenario) { it.cardTexts().any { text -> text == context.getString(R.string.purchase_in_progress) } }
      scenario.onActivity {
        assertTrue(it.cardTexts().contains(
          "${context.getString(R.string.purchase_list_deleted, "Compra")} · " +
            context.resources.getQuantityString(R.plurals.item_count, 2, 2)
        ))
        // A trip going on can be neither repeated nor removed yet.
        assertFalse(it.cardView(R.id.btnRepeatTrip).isVisible)
        assertFalse(it.cardView(R.id.btnDeleteTrip).isVisible)
      }
    }
  }

  @Test
  fun aFinishedTripIsRepeatedAsANewPendingList() {
    history.update { PurchaseHistory(trips = listOf(finishedTrip())) }
    lists.saveLists(listOf(UserList(5L, "Semana", mutableListOf(), createdAt = 0L)))
    launchHistory { scenario ->
      eventually(scenario) { it.cardView(R.id.btnRepeatTrip).isVisible }
      scenario.onActivity { it.cardView(R.id.btnRepeatTrip).performClick() }
      waitFor { lists.loadLists().size == 2 }
    }
    val repeated = lists.loadLists().first { it.id != 5L }
    assertTrue(repeated.name.startsWith("Semana · "))
    assertEquals(listOf("Leche", "Pan"), repeated.items.map { it.name })
    assertTrue(repeated.items.none { it.isCompleted })
  }

  @Test
  fun aTripCanBeRemovedFromTheHistoryAndPutBack() {
    history.update { PurchaseHistory(trips = listOf(finishedTrip())) }
    launchHistory { scenario ->
      eventually(scenario) { it.cardView(R.id.btnDeleteTrip).isVisible }
      scenario.onActivity { it.cardView(R.id.btnDeleteTrip).performClick() }
      click(android.R.id.button1)
      waitFor { history.load().isEmpty }
      eventually(scenario) { it.findViewById<View>(R.id.statePanel).isVisible }
      waitFor { snackbarText() == context.getString(R.string.purchase_deleted) }
      click(com.google.android.material.R.id.snackbar_action)
      waitFor { history.load().trips.map { it.id } == listOf(dayAgo) }
      eventually(scenario) { it.findViewById<RecyclerView>(R.id.recyclerViewPurchases).isVisible }
    }
  }

  @Test
  fun theProductsOfATripOpenWithTheirPrices() {
    history.update { PurchaseHistory(trips = listOf(finishedTrip())) }
    launchHistory { scenario ->
      eventually(scenario) { it.cardView(R.id.btnToggleProducts).isVisible }
      scenario.onActivity {
        assertFalse(it.cardView(R.id.tvTripProducts).isVisible)
        it.cardView(R.id.btnToggleProducts).performClick()
      }
      eventually(scenario) { it.cardView(R.id.tvTripProducts).isVisible }
      scenario.onActivity {
        val products = (it.cardView(R.id.tvTripProducts) as TextView).text.toString()
        assertEquals("Leche · 1,10 €\nPan", products)
        assertEquals(
          context.resources.getQuantityString(R.plurals.purchase_spend_partial, 1, "1,10 €", 1),
          (it.cardView(R.id.tvTripSpend) as TextView).text.toString()
        )
      }
    }
  }

  @Test
  fun aDamagedHistoryShowsAnErrorAndIsNotOverwritten() {
    historyFile.writeText("{dañado")
    launchHistory { scenario ->
      eventually(scenario) {
        it.findViewById<TextView>(R.id.tvStateTitle).text.toString() == context.getString(R.string.purchases_error_title)
      }
    }
    assertEquals("{dañado", historyFile.readText())
  }

  @Test
  fun anEmptyHistoryExplainsHowTripsAreSaved() {
    launchHistory { scenario ->
      eventually(scenario) {
        it.findViewById<TextView>(R.id.tvStateTitle).text.toString() == context.getString(R.string.purchases_empty_title)
      }
    }
    assertFalse(historyFile.exists())
  }

  private fun ListDetailActivity.checkBox(name: String): View {
    val recycler = findViewById<RecyclerView>(R.id.recyclerViewItems)
    return (0 until recycler.childCount).map(recycler::getChildAt)
      .first { it.findViewById<TextView>(R.id.tvItemName).text.toString() == name }
      .findViewById(R.id.cbItemCompleted)
  }

  private fun PurchaseHistoryActivity.firstCard(): View =
    findViewById<RecyclerView>(R.id.recyclerViewPurchases).getChildAt(0)

  private fun PurchaseHistoryActivity.cardView(id: Int): View = firstCard().findViewById(id)

  private fun PurchaseHistoryActivity.cardTexts(): List<String> {
    val card = findViewById<RecyclerView>(R.id.recyclerViewPurchases).getChildAt(0) ?: return emptyList()
    return listOf(R.id.tvTripStatus, R.id.tvTripDate, R.id.tvTripMeta)
      .map { card.findViewById<TextView>(it) }
      .filter { it.isVisible }
      .map { it.text.toString() }
  }

  private fun launchHistory(block: (ActivityScenario<PurchaseHistoryActivity>) -> Unit) {
    ActivityScenario.launch(PurchaseHistoryActivity::class.java).use { scenario ->
      eventually(scenario) { !it.findViewById<View>(R.id.loadingPanel).isVisible }
      block(scenario)
    }
  }
}
