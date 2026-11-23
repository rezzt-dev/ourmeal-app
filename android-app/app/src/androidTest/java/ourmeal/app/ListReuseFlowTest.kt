package ourmeal.app

import android.content.Intent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ourmeat.R
import com.google.android.material.textfield.TextInputLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import ourmeal.app.Ui.windowView
import ourmeal.app.controller.DataManager
import ourmeal.app.model.ListItem
import ourmeal.app.model.UserList
import java.io.File

@RunWith(AndroidJUnit4::class)
class ListReuseFlowTest {
  @get:Rule
  val appData = AppDataSandbox()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val lists = DataManager(context)
  private val routeFile = File(context.filesDir, "shopping_route.json")
  private val listId = 7L

  @After
  fun closeOpenedScreens() = Ui.finishResumedActivities()

  private fun seed(vararg items: ListItem) {
    lists.saveLists(listOf(UserList(listId, "Compra", items.toMutableList(), createdAt = 0L)))
  }

  private fun item(id: Long, name: String, bought: Boolean = false) =
    ListItem(id, name, isCompleted = bought, createdAt = id)

  private fun stored(): UserList = lists.getListById(listId)!!

  @Test
  fun aProductIsRenamedInPlaceAndTheRenameCanBeUndone() {
    seed(item(1, "Pan"), item(2, "LecHe", bought = true))
    launchDetail { scenario ->
      scenario.onActivity { it.rowNamed("LecHe").performLongClick() }
      val input = awaitInput(R.id.etItemName)
      onMain {
        assertEquals("LecHe", input.text.toString())
        input.setText("pan")
      }
      click(android.R.id.button1)
      // Another product already has that name, ignoring case.
      waitFor { onMain { windowView<TextInputLayout>(R.id.itemNameField)?.error != null } }
      onMain { input.setText("Leche entera") }
      click(android.R.id.button1)
      waitFor { stored().items.map { it.name } == listOf("Pan", "Leche entera") }
      assertTrue(stored().items[1].isCompleted)
      assertEquals(context.getString(R.string.item_renamed), awaitSnackbar())
      click(com.google.android.material.R.id.snackbar_action)
      waitFor { stored().items[1].name == "LecHe" }
    }
  }

  @Test
  fun uncheckingEverythingTeachesNothingAndCanBeUndone() {
    seed(item(1, "Pan", bought = true), item(2, "Leche", bought = true), item(3, "Huevos"))
    launchDetail { _ ->
      openActions()
      clickEntry(context.getString(R.string.list_uncheck_all))
      waitFor { stored().items.none { it.isCompleted } }
      assertFalse("A bulk uncheck is not a shopping trip", routeFile.exists())
      assertEquals(context.resources.getQuantityString(R.plurals.list_unchecked, 2, 2), awaitSnackbar())
      click(com.google.android.material.R.id.snackbar_action)
      waitFor { stored().items.map { it.isCompleted } == listOf(true, true, false) }
    }
  }

  @Test
  fun bulkActionsAreDisabledWithoutBoughtProducts() {
    seed(item(1, "Pan"))
    launchDetail { _ ->
      openActions()
      waitFor { entryEnabled(context.getString(R.string.list_uncheck_all)) != null }
      assertEquals(false, entryEnabled(context.getString(R.string.list_uncheck_all)))
      assertEquals(false, entryEnabled(context.getString(R.string.list_remove_bought)))
      assertEquals(true, entryEnabled(context.getString(R.string.list_duplicate)))
    }
  }

  @Test
  fun removingBoughtAsksFirstAndUndoPutsThemBackInPlace() {
    seed(item(1, "Pan", bought = true), item(2, "Leche"), item(3, "Huevos", bought = true))
    launchDetail { _ ->
      openActions()
      clickEntry(context.getString(R.string.list_remove_bought))
      click(android.R.id.button1)
      waitFor { stored().items.map { it.name } == listOf("Leche") }
      assertEquals(context.resources.getQuantityString(R.plurals.list_bought_removed, 2, 2), awaitSnackbar())
      click(com.google.android.material.R.id.snackbar_action)
      waitFor { stored().items.map { it.name } == listOf("Pan", "Leche", "Huevos") }
    }
  }

  @Test
  fun duplicatingCreatesAPendingCopyAndKeepsTheOriginal() {
    seed(item(1, "Pan", bought = true), item(2, "Leche"))
    launchDetail { _ ->
      openActions()
      clickEntry(context.getString(R.string.list_duplicate))
      val input = awaitInput(R.id.etListName)
      onMain { assertEquals("Compra (copia)", input.text.toString()) }
      click(android.R.id.button1)
      waitFor { lists.loadLists().size == 2 }
    }
    val copy = lists.loadLists().first { it.id != listId }
    assertEquals("Compra (copia)", copy.name)
    assertEquals(listOf("Pan", "Leche"), copy.items.map { it.name })
    assertTrue(copy.items.none { it.isCompleted })
    assertEquals(listOf(true, false), stored().items.map { it.isCompleted })
  }

  private fun openActions() = click(R.id.btnListActions)

  private fun awaitInput(id: Int): EditText {
    waitFor { onMain { windowView<EditText>(id) != null } }
    return onMain { windowView<EditText>(id)!! }
  }

  private fun awaitSnackbar(): String {
    waitFor { snackbarText().isNotEmpty() }
    return snackbarText()
  }

  private fun ListDetailActivity.rowNamed(name: String): View {
    val recycler = findViewById<RecyclerView>(R.id.recyclerViewItems)
    return (0 until recycler.childCount).map(recycler::getChildAt)
      .first { it.findViewById<TextView>(R.id.tvItemName).text.toString() == name }
  }

  private fun launchDetail(block: (ActivityScenario<ListDetailActivity>) -> Unit) {
    val intent = Intent(context, ListDetailActivity::class.java).putExtra(ListDetailActivity.EXTRA_LIST_ID, listId)
    ActivityScenario.launch<ListDetailActivity>(intent).use { scenario ->
      eventually(scenario) {
        val recycler = it.findViewById<RecyclerView>(R.id.recyclerViewItems)
        recycler.isVisible && recycler.childCount > 0
      }
      block(scenario)
    }
  }
}
