package ourmeal.app.controller

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ourmeal.app.model.WeeklyMeals
import java.io.File
import java.io.IOException

class MealDataManager(context: Context) {
  private val file = AtomicFile(File(context.filesDir, "meal_plans.json"))
  private val gson = Gson()
  private val listType = object : TypeToken<List<WeeklyMeals>>() {}.type

  fun loadWeeklyMeals(): MutableList<WeeklyMeals> = synchronized(FILE_LOCK) {
    if (!file.baseFile.exists()) return mutableListOf()
    try {
      file.openRead().bufferedReader().use { reader ->
        gson.fromJson<List<WeeklyMeals>>(reader, listType)?.toMutableList() ?: mutableListOf()
      }
    } catch (error: Exception) {
      Log.e("MealDataManager", "Could not read meal plans", error)
      throw IOException("No se pudo leer el menú guardado", error)
    }
  }

  fun getWeeklyMeals(weekId: String): WeeklyMeals = synchronized(FILE_LOCK) {
    loadWeeklyMeals().find { it.weekId == weekId } ?: WeeklyMeals(weekId)
  }

  fun saveWeeklyMeal(weeklyMeals: WeeklyMeals): Boolean = synchronized(FILE_LOCK) {
    val allWeeks = try { loadWeeklyMeals() } catch (_: IOException) { return false }
    val index = allWeeks.indexOfFirst { it.weekId == weeklyMeals.weekId }
    if (index >= 0) allWeeks[index] = weeklyMeals else allWeeks.add(weeklyMeals)
    var output: java.io.FileOutputStream? = null
    try {
      output = file.startWrite()
      output.write(gson.toJson(allWeeks).toByteArray(Charsets.UTF_8))
      file.finishWrite(output)
      true
    } catch (error: Exception) {
      output?.let(file::failWrite)
      Log.e("MealDataManager", "Could not save meal plans", error)
      false
    }
  }

  private companion object {
    /** Shared across activity-scoped instances so read-modify-write stays atomic. */
    val FILE_LOCK = Any()
  }
}
