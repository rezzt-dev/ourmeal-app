package ourmeal.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.ourmeat.R
import app.ourmeat.BuildConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ourmeal.app.controller.DataManager
import ourmeal.app.controller.PriceSettingsManager
import ourmeal.app.model.ListItem
import ourmeal.app.model.UserList
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Requires :backend:serveTestFixtures; goes through settings, HTTP, PostgreSQL, ingestion and UI. */
@RunWith(AndroidJUnit4::class)
class PriceComparisonEndToEndTest {
  @get:Rule val appData = AppDataSandbox()
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext

  @Test fun selectCityEnableCompareAndDisableAgain() {
    DataManager(context).saveLists(listOf(UserList(77, "Compra de prueba", mutableListOf(
      ListItem(1, "Arroz redondo", createdAt = 1), ListItem(2, "Pan", isCompleted = true, createdAt = 2)
    ), createdAt = 0)))
    testControl("fail-next-stores")
    ActivityScenario.launch(SettingsActivity::class.java).use { settings ->
      eventually { onMain { settingsValue(settings) { it.findViewById<MaterialSwitch>(R.id.switchPrices).isEnabled } } }
      settings.onActivity { it.findViewById<MaterialSwitch>(R.id.switchPrices).performClick() }
      eventually { onMain { settingsValue(settings) { it.findViewById<View>(R.id.btnSearchCity).isVisible } } }
      settings.onActivity { it.findViewById<View>(R.id.btnSearchCity).performClick() }
      eventually { onMain { dialogView<TextInputEditText>(R.id.etCity) != null } }
      onMain {
        dialogView<TextInputEditText>(R.id.etCity)!!.setText("Sevilla")
        dialogView<View>(android.R.id.button1)!!.performClick()
      }
      eventually { onMain { dialogView<android.widget.LinearLayout>(R.id.cityResults)?.childCount == 1 } }
      onMain { dialogView<android.widget.LinearLayout>(R.id.cityResults)!!.getChildAt(0).performClick() }
      eventually { settingsValue(settings) { it.findViewById<View>(R.id.btnRetryChains).isShown } }
      settings.onActivity { it.findViewById<View>(R.id.btnRetryChains).performClick() }
      eventually { PriceSettingsManager(context).load().isReady }
    }

    testControl("fail-next-resolve")
    val intent = Intent(context, ListDetailActivity::class.java).putExtra(ListDetailActivity.EXTRA_LIST_ID, 77L)
    ActivityScenario.launch<ListDetailActivity>(intent).use { list ->
      eventually {
        var failed = false
        list.onActivity { activity ->
          failed = activity.priceButton()?.text.toString().contains("Reintentar")
          assertTrue(activity.findViewById<View>(R.id.btnAddItem).isEnabled)
        }
        failed
      }
      // No retry tap: the active screen must recover when its price service comes back.
      eventually {
        var shown = false
        list.onActivity { activity ->
          val button = activity.priceButton()
          shown = button?.isVisible == true && button.text.toString().contains("Cadena B") && button.text.toString().contains("1,79")
        }
        shown
      }
      list.onActivity { activity ->
        val button = activity.priceButton()!!
        assertTrue(button.text.toString(), button.text.toString().replace("\u2060", "").contains("€/kg"))
        assertTrue(button.isFocusable)
        assertTrue(button.contentDescription.toString().contains("Cadena B"))
      }
      screenshot("price-e2e-portrait.png")
      list.onActivity { it.priceButton()!!.performClick() }
      eventually { onMain { dialogView<TextView>(R.id.tvPriceTrap)?.isVisible == true } }
      onMain {
        assertTrue(dialogView<TextView>(R.id.tvPriceTrap)!!.text.toString().contains("Cadena A"))
        assertTrue(dialogView<TextView>(R.id.tvPriceTrap)!!.text.toString().contains("1,98"))
      }
      screenshot("price-e2e-detail.png")
      onMain { dialogView<View>(android.R.id.button1)!!.performClick() }
      instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.5").close()
      try {
        list.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        eventually {
          var ready = false
          list.onActivity { activity ->
            ready = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
              activity.resources.configuration.fontScale >= 1.49f && activity.priceButton()?.let { it.isShown && it.text.toString().contains("Cadena B") } == true
          }
          ready
        }
        screenshot("price-e2e-landscape-large-font.png")
      } finally {
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.0").close()
        list.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
      }
      list.onActivity { it.findViewById<View>(R.id.btnSettings).performClick() }
      eventually { onMain { currentSettings()?.findViewById<MaterialSwitch>(R.id.switchPrices)?.isChecked == true } }
      onMain { currentSettings()!!.findViewById<MaterialSwitch>(R.id.switchPrices).performClick() }
      eventually { !PriceSettingsManager(context).load().enabled }
      onMain { currentSettings()!!.findViewById<View>(R.id.btnBack).performClick() }
      eventually {
        var hidden = false
        list.onActivity { activity ->
          val row = activity.findViewById<RecyclerView>(R.id.recyclerViewItems).findViewHolderForAdapterPosition(0)?.itemView
          hidden = row != null && !row.findViewById<View>(R.id.btnItemPrice).isVisible && row.findViewById<View>(R.id.tvItemStatus).isVisible
        }
        hidden
      }
    }
  }

  private fun ListDetailActivity.priceButton(): MaterialButton? = findViewById<RecyclerView>(R.id.recyclerViewItems)
    .findViewHolderForAdapterPosition(0)?.itemView?.findViewById(R.id.btnItemPrice)

  private fun testControl(action: String) {
    OkHttpClient().newCall(Request.Builder().url(BuildConfig.PRICE_API_URL + "__test/$action")
      .post(ByteArray(0).toRequestBody()).build()).execute().use { assertEquals(204, it.code) }
  }

  private fun currentSettings(): SettingsActivity? = ActivityLifecycleMonitorRegistry.getInstance()
    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<SettingsActivity>().singleOrNull()

  private fun screenshot(name: String) {
    require(Regex("[a-z0-9.-]+\\.png").matches(name))
    instrumentation.waitForIdleSync()
    android.os.SystemClock.sleep(150) // Let the compositor present the frame after the view assertions.
    val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: throw AssertionError("Screenshot unavailable")
    val temporary = java.io.File(context.cacheDir, "$name.writing")
    temporary.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    bitmap.recycle()
    check(temporary.renameTo(java.io.File(context.cacheDir, name)))
  }

  private fun <T> settingsValue(scenario: ActivityScenario<SettingsActivity>, read: (SettingsActivity) -> T): T {
    var value: T? = null
    scenario.onActivity { value = read(it) }
    @Suppress("UNCHECKED_CAST") return value as T
  }

  private fun <T : View> dialogView(id: Int): T? = android.view.inspector.WindowInspector.getGlobalWindowViews()
    .firstNotNullOfOrNull { it.findViewById<T>(id) }

  private fun eventually(condition: () -> Boolean) {
    repeat(600) { if (condition()) return; android.os.SystemClock.sleep(100) }
    throw AssertionError("Price flow did not finish within 60 seconds")
  }

  private fun <T> onMain(block: () -> T): T {
    var result: T? = null
    instrumentation.runOnMainSync { result = block() }
    @Suppress("UNCHECKED_CAST") return result as T
  }
}
