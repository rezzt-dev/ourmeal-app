package ourmeal.app

import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.ourmeat.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ourmeal.app.Ui.eventually
import ourmeal.app.Ui.onMain
import ourmeal.app.model.TodayMenu
import ourmeal.app.model.WeekUtils
import java.time.LocalDate

/** What the widget draws, inflated as a launcher would, and what it opens in the app. */
@RunWith(AndroidJUnit4::class)
class TodayMenuWidgetTest {
  @get:Rule
  val appData = AppDataSandbox()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  @After
  fun closeOpenedScreens() = Ui.finishResumedActivities()

  @Test
  fun tappingTheWidgetBringsTheMenuBackToToday() {
    val thisWeek = WeekUtils.getWeekDisplay(WeekUtils.getCurrentWeekId())
    val nextWeek = WeekUtils.getWeekDisplay(WeekUtils.getNextWeek(WeekUtils.getCurrentWeekId()))
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      eventually(scenario) { it.findViewById<View>(R.id.btnMenuTools).isEnabled }
      scenario.onActivity { it.findViewById<View>(R.id.btnNextWeek).performClick() }
      eventually(scenario) { it.weekTitle() == nextWeek && it.findViewById<View>(R.id.btnMenuTools).isEnabled }

      // The same intent the widget's pending intent carries, started from outside the app like a launcher.
      context.startActivity(
        Intent(context, MainActivity::class.java)
          .setAction(MainActivity.ACTION_SHOW_TODAY)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      )
      eventually(scenario) { it.weekTitle() == thisWeek && !it.findViewById<View>(R.id.btnToday).isVisible }
    }
  }

  @Test
  fun refreshingWithoutPlacedWidgetsDoesNothing() {
    // No widget is placed on a test device: the request must return without broadcasting or failing.
    TodayMenuWidget.requestRefresh(context)
  }

  @Test
  fun aPlannedDayShowsBothMealsAndAnEmptyMealSaysSo() {
    val menu = TodayMenu(LocalDate.now(), lunch = "Lentejas estofadas con chorizo y verduras de temporada", dinner = "")
    for (layout in listOf(R.layout.widget_today_menu_compact, R.layout.widget_today_menu_wide)) {
      val view = draw(TodayMenuWidget.Content.Ready(menu), layout, widthDp = 320, heightDp = 190)
      onMain {
        assertTrue(view.findViewById<View>(R.id.widgetMeals).isVisible)
        assertTrue(!view.findViewById<View>(R.id.widgetMessage).isVisible)
        assertEquals(menu.lunch, view.text(R.id.widgetLunch))
        assertEquals(context.getString(R.string.widget_unplanned), view.text(R.id.widgetDinner))
        val description = view.findViewById<View>(android.R.id.background).contentDescription.toString()
        assertTrue(description.contains(menu.lunch))
        assertTrue(description.contains(context.getString(R.string.widget_unplanned)))
      }
    }
  }

  @Test
  fun anEmptyDayAndAnUnreadableMenuExplainThemselves() {
    val empty = draw(TodayMenuWidget.Content.Ready(TodayMenu(LocalDate.now(), "", "")), R.layout.widget_today_menu_wide, 320, 190)
    val unreadable = draw(TodayMenuWidget.Content.Unreadable, R.layout.widget_today_menu_compact, 170, 190)
    onMain {
      assertTrue(!empty.findViewById<View>(R.id.widgetMeals).isVisible)
      assertEquals(context.getString(R.string.widget_empty_title), empty.text(R.id.widgetMessageTitle))
      assertEquals(context.getString(R.string.widget_error_title), unreadable.text(R.id.widgetMessageTitle))
      assertEquals(
        context.getString(R.string.widget_error_description),
        unreadable.findViewById<View>(android.R.id.background).contentDescription.toString()
      )
    }
  }

  /** Inflates one size of the widget through RemoteViews, as a launcher does, and lays it out. */
  private fun draw(content: TodayMenuWidget.Content, layout: Int, widthDp: Int, heightDp: Int): View = onMain {
    val density = context.resources.displayMetrics.density
    val parent = FrameLayout(context)
    val view = TodayMenuWidget.render(context, content, layout, dishLines = 2).apply(context, parent)
    view.measure(
      View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY)
    )
    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    view
  }

  private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

  private fun MainActivity.weekTitle(): String = findViewById<TextView>(R.id.tvWeekTitle).text.toString()
}
