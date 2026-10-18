package ourmeal.app

import android.content.res.Configuration
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.EditText
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ourmeat.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreationFormTest {
  @Test
  fun blankNamesCannotSubmitAndDraftSurvivesRecreation() {
    ActivityScenario.launch(ListActivity::class.java).use { scenario ->
      awaitReady(scenario)
      scenario.onActivity {
        it.findViewById<View>(R.id.btnAddList).performClick()
      }
      androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
      scenario.onActivity {
        val input = dialogView<EditText>(R.id.etListName)
        val submit = dialogView<View>(android.R.id.button1)
        assertFalse(submit.isEnabled)
        input.setText("   ")
        assertFalse(submit.isEnabled)
        input.setText("Compra del domingo")
        input.setSelection(6)
        assertTrue(submit.isEnabled)
      }
      scenario.recreate()
      awaitReady(scenario)
      scenario.onActivity {
        val input = dialogView<EditText>(R.id.etListName)
        assertEquals("Compra del domingo", input.text.toString())
        assertEquals(6, input.selectionStart)
        assertTrue(dialogView<View>(android.R.id.button1).isEnabled)
        dialogView<View>(android.R.id.button2).performClick()
      }
    }
  }

  @Test
  fun appUsesItsDarkThemeIndependentlyOfDeviceSetting() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity {
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
        assertEquals(Configuration.UI_MODE_NIGHT_YES,
          it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
      }
    }
  }

  // Avoid Espresso 3.6 input injection, which is incompatible with API 37.
  // Real touch, keyboard and scrolling are covered by the manual emulator checks.
  private fun <T : View> dialogView(id: Int): T =
    WindowInspector.getGlobalWindowViews().firstNotNullOf { it.findViewById<T>(id) }

  private fun awaitReady(scenario: ActivityScenario<ListActivity>) {
    repeat(50) {
      var ready = false
      scenario.onActivity { ready = it.findViewById<View>(R.id.btnAddList).isEnabled }
      if (ready) return
      android.os.SystemClock.sleep(100)
    }
    throw AssertionError("Lists did not finish loading")
  }
}
