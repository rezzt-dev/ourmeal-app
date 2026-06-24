package ourmeal.app.model

import java.text.SimpleDateFormat
import java.util.*

object WeekUtils {
  fun getCurrentWeekId(): String {
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val week = calendar.get(Calendar.WEEK_OF_YEAR)
    return String.format(Locale.getDefault(), "%d-W%02d", year, week)
  }

  fun getWeekDisplay(weekId: String): String {
    return try {
      val parts = weekId.split("-W")
      val year = parts[0].toInt()
      val week = parts[1].toInt()

      val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.WEEK_OF_YEAR, week)
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
      }

      val startDate = cal.time
      cal.add(Calendar.DAY_OF_WEEK, 6)
      val endDate = cal.time

      val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
      "Semana $week (${sdf.format(startDate)} - ${sdf.format(endDate)})"
    } catch (e: Exception) {
      "Semana $weekId"
    }
  }

  fun getNextWeek(currentWeekId: String): String {
    return try {
      val parts = currentWeekId.split("-W")
      val year = parts[0].toInt()
      val week = parts[1].toInt()

      val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.WEEK_OF_YEAR, week)
        add(Calendar.WEEK_OF_YEAR, 1)
      }

      val newYear = cal.get(Calendar.YEAR)
      val newWeek = cal.get(Calendar.WEEK_OF_YEAR)

      String.format(Locale.getDefault(), "%d-W%02d", newYear, newWeek)
    } catch (e: Exception) {
      getCurrentWeekId()
    }
  }

  fun getPreviousWeek(currentWeekId: String): String {
    return try {
      val parts = currentWeekId.split("-W")
      val year = parts[0].toInt()
      val week = parts[1].toInt()

      val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.WEEK_OF_YEAR, week)
        add(Calendar.WEEK_OF_YEAR, -1)
      }

      val newYear = cal.get(Calendar.YEAR)
      val newWeek = cal.get(Calendar.WEEK_OF_YEAR)

      String.format(Locale.getDefault(), "%d-W%02d", newYear, newWeek)
    } catch (e: Exception) {
      getCurrentWeekId()
    }
  }

  fun getWeekIdFromDate(date: Date): String {
    val cal = Calendar.getInstance().apply {
      time = date
      firstDayOfWeek = Calendar.MONDAY
      minimalDaysInFirstWeek = 4
    }

    val year = cal.get(Calendar.YEAR)
    val week = cal.get(Calendar.WEEK_OF_YEAR)

    return String.format(Locale.getDefault(), "%d-W%02d", year, week)
  }
}