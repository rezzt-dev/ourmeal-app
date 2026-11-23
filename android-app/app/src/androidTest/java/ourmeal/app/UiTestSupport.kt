package ourmeal.app

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.ListView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/**
 * Drives popups, dialogs and snackbars without Espresso input injection, which is incompatible
 * with API 37; see CreationFormTest. Real touch and keyboard use is covered by manual checks.
 */
internal object Ui {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  /** Polls from the test thread; conditions that touch views go through [onMain]. */
  fun waitFor(condition: () -> Boolean) {
    repeat(50) {
      if (condition()) return
      android.os.SystemClock.sleep(100)
    }
    throw AssertionError("Condition not met in time")
  }

  fun <T> onMain(block: () -> T): T {
    var result: T? = null
    instrumentation.runOnMainSync { result = block() }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  fun <A : Activity> eventually(scenario: ActivityScenario<A>, condition: (A) -> Boolean) = waitFor {
    var met = false
    scenario.onActivity { met = condition(it) }
    met
  }

  /** A view with [id] in any window: dialogs and popups live outside the activity's own. */
  fun <T : View> windowView(id: Int): T? =
    WindowInspector.getGlobalWindowViews().asReversed().firstNotNullOfOrNull { it.findViewById<T>(id) }

  fun click(id: Int) {
    waitFor { onMain { windowView<View>(id)?.isEnabled == true } }
    onMain { windowView<View>(id)!!.performClick() }
  }

  /** Taps the entry of a popup menu or dialog list whose text is [text]. */
  fun clickEntry(text: String) {
    waitFor { onMain { findEntry(text) != null } }
    onMain {
      val (list, row) = findEntry(text)!!
      val position = list.getPositionForView(row)
      list.performItemClick(row, position, list.getItemIdAtPosition(position))
    }
  }

  /**
   * Whether the popup or dialog entry [text] can be tapped; null while it is not shown. A popup
   * adapter reports every row as enabled, so the row view, which mirrors its item, is checked.
   */
  fun entryEnabled(text: String): Boolean? = onMain {
    val (_, row) = findEntry(text) ?: return@onMain null
    row.isEnabled
  }

  fun snackbarText(): String = onMain {
    windowView<TextView>(com.google.android.material.R.id.snackbar_text)?.text?.toString().orEmpty()
  }

  /** Activities a test opened indirectly, such as a duplicated list, are closed here. */
  fun finishResumedActivities() = onMain {
    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach { it.finish() }
  }

  private fun findEntry(text: String): Pair<ListView, View>? =
    WindowInspector.getGlobalWindowViews().asReversed().firstNotNullOfOrNull { root ->
      listViews(root).firstNotNullOfOrNull { list ->
        (0 until list.childCount).map(list::getChildAt).firstOrNull { row -> row.hasText(text) }?.let { list to it }
      }
    }

  private fun listViews(view: View): List<ListView> = when (view) {
    is ListView -> listOf(view)
    is ViewGroup -> (0 until view.childCount).flatMap { listViews(view.getChildAt(it)) }
    else -> emptyList()
  }

  private fun View.hasText(text: String): Boolean = when (this) {
    is TextView -> this.text.toString() == text
    is ViewGroup -> (0 until childCount).any { getChildAt(it).hasText(text) }
    else -> false
  }
}
