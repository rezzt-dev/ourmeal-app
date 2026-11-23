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
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ourmeal.app.Ui.click
import ourmeal.app.Ui.eventually
import ourmeal.app.Ui.onMain
import ourmeal.app.Ui.snackbarText
import ourmeal.app.Ui.waitFor
import ourmeal.app.Ui.windowView
import ourmeal.app.controller.DataManager
import ourmeal.app.controller.ProductPriceManager
import ourmeal.app.model.ListItem
import ourmeal.app.model.ProductPrices
import ourmeal.app.model.UserList
import ourmeal.app.model.formatPrice
import java.io.File

@RunWith(AndroidJUnit4::class)
class ItemPriceFlowTest {
  @get:Rule
  val appData = AppDataSandbox()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val lists = DataManager(context)
  private val prices = ProductPriceManager(context)
  private val pricesFile = File(context.filesDir, "product_prices.json")
  private val listId = 7L

  @After
  fun closeOpenedScreens() = Ui.finishResumedActivities()

  private fun seed(vararg items: ListItem) {
    lists.saveLists(listOf(UserList(listId, "Compra", items.toMutableList(), createdAt = 0L)))
  }

  private fun item(id: Long, name: String, cents: Long? = null) =
    ListItem(id, name, createdAt = id, priceCents = cents)

  private fun stored(): UserList = lists.getListById(listId)!!

  @Test
  fun aWrittenPriceIsSavedRememberedAndAddedToTheTotal() {
    seed(item(1, "Leche"), item(2, "Pan"))
    launchDetail { scenario ->
      scenario.onActivity {
        assertEquals(false, it.findViewById<View>(R.id.tvEstimate).isVisible)
        it.priceButton("Leche").performClick()
      }
      typePrice("1,2")
      click(android.R.id.button1)
      waitFor { stored().items.first().priceCents == 120L }
      waitFor { prices.load().byKey()["leche"] == 120L }
      assertEquals(context.getString(R.string.price_saved), awaitSnackbar())
      eventually(scenario) {
        it.estimate() == context.resources.getQuantityString(R.plurals.price_estimate_unpriced, 1, formatPrice(120), 1)
      }
      scenario.onActivity { assertEquals(formatPrice(120), it.priceButton("Leche").text.toString()) }
    }
  }

  @Test
  fun zeroAndTooLargeAmountsStayInTheForm() {
    seed(item(1, "Leche"))
    launchDetail { scenario ->
      scenario.onActivity { it.priceButton("Leche").performClick() }
      typePrice("0")
      click(android.R.id.button1)
      waitFor { fieldError() == context.getString(R.string.price_zero) }
      typePrice("12000")
      click(android.R.id.button1)
      waitFor { fieldError() == context.getString(R.string.price_too_large) }
      assertNull(stored().items.single().priceCents)
    }
  }

  @Test
  fun aRememberedPriceShowsInAnotherListAndCanBeRemoved() {
    prices.update { ProductPrices().remember("Leche", 110, now = 1) }
    seed(item(1, "leche"), item(2, "Pan", cents = 95))
    launchDetail { scenario ->
      eventually(scenario) { it.priceButton("leche").text.toString() == "≈ ${formatPrice(110)}" }
      eventually(scenario) { it.estimate() == context.getString(R.string.price_estimate, formatPrice(205)) }
      scenario.onActivity { it.priceButton("leche").performClick() }
      waitFor { onMain { windowView<EditText>(R.id.etPrice)?.text?.toString() } == "1,10" }
      click(android.R.id.button3)
      waitFor { prices.load().byKey().isEmpty() }
      assertEquals(context.getString(R.string.price_removed), awaitSnackbar())
      eventually(scenario) { it.estimate() == context.resources.getQuantityString(R.plurals.price_estimate_unpriced, 1, formatPrice(95), 1) }
    }
  }

  @Test
  fun aDamagedPriceMemoryIsNotOverwrittenAndTheListPriceIsStillSaved() {
    seed(item(1, "Leche"))
    pricesFile.writeText("{dañado")
    launchDetail { scenario ->
      scenario.onActivity { it.priceButton("Leche").performClick() }
      typePrice("2.50")
      click(android.R.id.button1)
      waitFor { stored().items.single().priceCents == 250L }
      assertEquals(context.getString(R.string.price_memory_failed), awaitSnackbar())
    }
    assertEquals("{dañado", pricesFile.readText())
  }

  private fun typePrice(text: String) {
    waitFor { onMain { windowView<EditText>(R.id.etPrice) != null } }
    onMain { windowView<EditText>(R.id.etPrice)!!.setText(text) }
  }

  private fun fieldError(): String? = onMain { windowView<TextInputLayout>(R.id.priceField)?.error?.toString() }

  private fun awaitSnackbar(): String {
    waitFor { snackbarText().isNotEmpty() }
    return snackbarText()
  }

  private fun ListDetailActivity.estimate(): String? =
    findViewById<TextView>(R.id.tvEstimate).takeIf { it.isVisible }?.text?.toString()

  private fun ListDetailActivity.priceButton(name: String): MaterialButton {
    val recycler = findViewById<RecyclerView>(R.id.recyclerViewItems)
    return (0 until recycler.childCount).map(recycler::getChildAt)
      .first { it.findViewById<TextView>(R.id.tvItemName).text.toString() == name }
      .findViewById(R.id.btnItemPrice)
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
