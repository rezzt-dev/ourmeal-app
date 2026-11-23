package ourmeal.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.annotation.VisibleForTesting
import app.ourmeat.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ourmeal.app.controller.MealDataManager
import ourmeal.app.model.TodayMenu
import ourmeal.app.model.todayMenu
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home screen widget with today's lunch and dinner; tapping it opens the menu on today. It reads
 * `meal_plans.json` off the main thread under the same lock as the app, and never writes it.
 *
 * It is redrawn after every save of the current week, when the app opens, when the clock, time
 * zone or language change, and once after midnight through an inexact, non-waking alarm, so the
 * day changes without any permission the next time the device is in use.
 */
class TodayMenuWidget : AppWidgetProvider() {

  override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    refreshAsync(context)
  }

  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      ACTION_REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED ->
        refreshAsync(context)
      else -> super.onReceive(context, intent)
    }
  }

  override fun onDisabled(context: Context) {
    context.getSystemService(AlarmManager::class.java)?.cancel(midnightIntent(context))
  }

  /** Reads and draws within the broadcast's time limit, which a small JSON file is far from. */
  private fun refreshAsync(context: Context) {
    val app = context.applicationContext
    val pending = goAsync()
    scope.launch {
      try {
        refreshNow(app)
      } finally {
        pending.finish()
      }
    }
  }

  /** What the widget shows: today's meals, or that the menu could not be read. */
  internal sealed interface Content {
    data class Ready(val menu: TodayMenu) : Content
    data object Unreadable : Content
  }

  companion object {
    private const val ACTION_REFRESH = "ourmeal.app.action.REFRESH_TODAY_MENU"
    /** Narrow and short widgets keep one line per dish; taller ones wrap the dish names. */
    private const val SHORT_LINES = 1
    private const val TALL_LINES = 2
    private const val WIDE_TALL_LINES = 3
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val spanish: Locale = Locale.forLanguageTag("es-ES")
    private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d", spanish)
    private val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", spanish)

    /** Redraws the placed widgets after the menu changes; does nothing when none is placed. */
    fun requestRefresh(context: Context) {
      if (widgetIds(context).isEmpty()) return
      context.sendBroadcast(Intent(context, TodayMenuWidget::class.java).setAction(ACTION_REFRESH))
    }

    private fun widgetIds(context: Context): IntArray =
      AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TodayMenuWidget::class.java))

    /** Blocking: reads the menu file, so it must run off the main thread. */
    private fun refreshNow(context: Context) {
      val ids = widgetIds(context)
      if (ids.isEmpty()) return
      val content = try {
        Content.Ready(todayMenu(MealDataManager(context).loadWeeklyMeals(), LocalDate.now()))
      } catch (_: IOException) {
        Content.Unreadable
      }
      val views = RemoteViews(
        mapOf(
          SizeF(100f, 100f) to render(context, content, R.layout.widget_today_menu_compact, SHORT_LINES),
          SizeF(100f, 170f) to render(context, content, R.layout.widget_today_menu_compact, TALL_LINES),
          SizeF(230f, 100f) to render(context, content, R.layout.widget_today_menu_wide, TALL_LINES),
          SizeF(230f, 170f) to render(context, content, R.layout.widget_today_menu_wide, WIDE_TALL_LINES)
        )
      )
      AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
      scheduleAfterMidnight(context)
    }

    /** One size of the widget; internal so tests can draw it without a launcher. */
    @VisibleForTesting
    internal fun render(context: Context, content: Content, @LayoutRes layout: Int, dishLines: Int): RemoteViews {
      val views = RemoteViews(context.packageName, layout)
      val wide = layout == R.layout.widget_today_menu_wide
      val today = LocalDate.now()
      val date = today.format(if (wide) longDate else shortDate)
      views.setTextViewText(R.id.widgetDate, date)
      views.setOnClickPendingIntent(android.R.id.background, openMenuIntent(context))
      when (content) {
        is Content.Ready -> if (content.menu.isEmpty) {
          views.showMessage(context.getString(R.string.widget_empty_title), context.getString(R.string.widget_empty_body))
          views.setContentDescription(
            android.R.id.background,
            context.getString(R.string.widget_empty_description, today.format(longDate))
          )
        } else {
          val unplanned = context.getString(R.string.widget_unplanned)
          views.setViewVisibility(R.id.widgetMeals, View.VISIBLE)
          views.setViewVisibility(R.id.widgetMessage, View.GONE)
          views.showDish(context, R.id.widgetLunch, content.menu.lunch, dishLines)
          views.showDish(context, R.id.widgetDinner, content.menu.dinner, dishLines)
          views.setContentDescription(
            android.R.id.background,
            context.getString(
              R.string.widget_planned_description,
              today.format(longDate),
              content.menu.lunch.ifEmpty { unplanned },
              content.menu.dinner.ifEmpty { unplanned }
            )
          )
        }
        Content.Unreadable -> {
          views.showMessage(context.getString(R.string.widget_error_title), context.getString(R.string.widget_error_body))
          views.setContentDescription(android.R.id.background, context.getString(R.string.widget_error_description))
        }
      }
      return views
    }

    private fun RemoteViews.showMessage(title: String, body: String) {
      setViewVisibility(R.id.widgetMeals, View.GONE)
      setViewVisibility(R.id.widgetMessage, View.VISIBLE)
      setTextViewText(R.id.widgetMessageTitle, title)
      setTextViewText(R.id.widgetMessageBody, body)
      setViewVisibility(R.id.widgetMessageBody, View.VISIBLE)
    }

    /** An empty meal reads "Sin planificar" in the muted color, so it never looks like a dish. */
    private fun RemoteViews.showDish(context: Context, id: Int, dish: String, lines: Int) {
      setTextViewText(id, dish.ifEmpty { context.getString(R.string.widget_unplanned) })
      setTextColor(id, context.getColor(if (dish.isEmpty()) R.color.muted else R.color.ink))
      setInt(id, "setMaxLines", lines)
    }

    /** Opens the weekly menu on today, over whatever screen of the app was open. */
    private fun openMenuIntent(context: Context): PendingIntent {
      val intent = Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_SHOW_TODAY)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      return PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
    }

    /**
     * An inexact alarm that does not wake the device: it fires soon after midnight, or when the
     * device is next used, which is the moment the widget must already show the new day.
     */
    private fun scheduleAfterMidnight(context: Context) {
      val alarms = context.getSystemService(AlarmManager::class.java) ?: return
      val nextDay = ZonedDateTime.now(ZoneId.systemDefault()).toLocalDate().plusDays(1)
        .atStartOfDay(ZoneId.systemDefault()).plusMinutes(1)
      alarms.set(AlarmManager.RTC, nextDay.toInstant().toEpochMilli(), midnightIntent(context))
    }

    private fun midnightIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
      context,
      0,
      Intent(context, TodayMenuWidget::class.java).setAction(ACTION_REFRESH),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
  }
}
