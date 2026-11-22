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
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ourmeal.app.controller.DataManager
import ourmeal.app.model.CachedComparison
import ourmeal.app.model.ListItem
import ourmeal.app.model.PriceCache
import ourmeal.app.model.PriceChain
import ourmeal.app.model.PriceCity
import ourmeal.app.model.PriceOffer
import ourmeal.app.model.PriceSettings
import ourmeal.app.model.PriceSource
import ourmeal.app.model.PriceTarget
import ourmeal.app.model.ProductAlias
import ourmeal.app.model.ProductAliases
import ourmeal.app.model.UnitKind
import ourmeal.app.model.formatUnitPrice
import ourmeal.app.model.unitPriceOf
import ourmeal.app.model.UserList
import java.io.File

/**
 * The price line under each pending product, from saved aliases and fresh saved prices, so no
 * request reaches the price service.
 */
@RunWith(AndroidJUnit4::class)
class PriceComparisonListTest {
  @get:Rule
  val appData = AppDataSandbox()

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext
  private val gson = Gson()
  private val listId = 11L
  private val rice = PriceTarget(1, null, "Arroz redondo")
  private val chainA = PriceChain(1, "Cadena A")
  private val chainB = PriceChain(2, "Cadena B")

  private fun seedList() {
    val items = mutableListOf(
      ListItem(1, "Arroz", createdAt = 1),
      ListItem(2, "Pan", isCompleted = true, createdAt = 2)
    )
    DataManager(context).saveLists(listOf(UserList(listId, "Compra", items, createdAt = 0L)))
  }

  private fun write(name: String, value: Any) = File(context.filesDir, name).writeText(gson.toJson(value))

  private fun offer(chain: Int, price: Double, pack: Double) = PriceOffer(
    chain, "Arroz redondo", price, unitPriceOf(price, UnitKind.MASS, pack)!!, UnitKind.MASS, pack,
    onOffer = false, observedAt = System.currentTimeMillis(), source = PriceSource.ONLINE
  )

  private fun seedPrices(enabled: Boolean) {
    val settings = PriceSettings(
      enabled = enabled, installationKey = "prueba", city = PriceCity(7, "Sevilla", null),
      chains = listOf(chainA, chainB), storeIds = listOf(10, 20)
    )
    write("price_settings.json", settings)
    write("product_aliases.json", ProductAliases(aliases = listOf(ProductAlias("arroz", "Arroz", rice, false, 1L))))
    // The small pack looks cheaper, but costs more per kilo.
    val offers = listOf(offer(1, 0.99, 500.0), offer(2, 1.79, 1000.0))
    write("price_cache.json", PriceCache(entries = listOf(CachedComparison(rice.key, settings.signature, offers, System.currentTimeMillis()))))
  }

  @Test
  fun withTheComparisonOffTheListLooksAsBefore() {
    seedList()
    seedPrices(enabled = false)
    launchDetail { scenario ->
      scenario.onActivity {
        assertFalse(it.priceButtonAt(0).isVisible)
        assertEquals(context.getString(R.string.item_status_pending), it.statusAt(0).text.toString())
        assertTrue(it.statusAt(0).isVisible)
        assertFalse(it.findViewById<View>(R.id.priceNotice).isVisible)
      }
    }
  }

  @Test
  fun pendingProductsShowTheCheapestSupermarketPerKilo() {
    seedList()
    seedPrices(enabled = true)
    val expected = context.getString(R.string.prices_line_best, chainB.name, formatUnitPrice(1.79, UnitKind.MASS))
    launchDetail { scenario ->
      eventually(scenario) { it.priceButtonAt(0).isVisible && it.priceButtonAt(0).text.toString() == expected }
      scenario.onActivity {
        assertFalse(it.statusAt(0).isVisible)
        // A bought product keeps its status and no price line.
        assertFalse(it.priceButtonAt(1).isVisible)
        assertEquals(context.getString(R.string.item_status_bought), it.statusAt(1).text.toString())
        assertEquals(
          context.getString(R.string.prices_notice_ready, "Sevilla"),
          it.findViewById<TextView>(R.id.tvPriceNotice).text.toString()
        )
        it.priceButtonAt(0).performClick()
      }
      // The detail warns that the cheaper-looking pack is dearer per kilo.
      waitFor { onMain { dialogView<TextView>(R.id.tvPriceTrap)?.isVisible == true } }
      val trap = onMain { dialogView<TextView>(R.id.tvPriceTrap)!!.text.toString() }
      assertTrue(trap, trap.contains(chainA.name) && trap.contains(formatUnitPrice(1.98, UnitKind.MASS)))
      onMain { dialogView<View>(android.R.id.button1)!!.performClick() }
    }
  }

  private fun ListDetailActivity.rowAt(position: Int): View =
    findViewById<RecyclerView>(R.id.recyclerViewItems).findViewHolderForAdapterPosition(position)!!.itemView

  private fun ListDetailActivity.priceButtonAt(position: Int): MaterialButton = rowAt(position).findViewById(R.id.btnItemPrice)

  private fun ListDetailActivity.statusAt(position: Int): TextView = rowAt(position).findViewById(R.id.tvItemStatus)

  private fun launchDetail(block: (ActivityScenario<ListDetailActivity>) -> Unit) {
    val intent = Intent(context, ListDetailActivity::class.java).putExtra(ListDetailActivity.EXTRA_LIST_ID, listId)
    ActivityScenario.launch<ListDetailActivity>(intent).use { scenario ->
      eventually(scenario) {
        val recycler = it.findViewById<RecyclerView>(R.id.recyclerViewItems)
        recycler.isVisible && recycler.findViewHolderForAdapterPosition(1) != null
      }
      block(scenario)
    }
  }

  private fun <T : View> dialogView(id: Int): T? =
    android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { it.findViewById<T>(id) }

  // Avoid Espresso input injection, which is incompatible with API 37; see CreationFormTest.
  private fun eventually(
    scenario: ActivityScenario<ListDetailActivity>,
    condition: (ListDetailActivity) -> Boolean
  ) = waitFor {
    var met = false
    scenario.onActivity { met = condition(it) }
    met
  }

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
