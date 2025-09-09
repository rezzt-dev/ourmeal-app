package app.ourmeat.controller

import android.content.Context
import app.ourmeat.model.WeeklyMeals
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.*

class MealDataManager (private val context: Context) {
  companion object {
    private const val FILE_NAME = "meal_plans.json"
  }

  private val gson = Gson()

  fun saveWeeklyMeals(weeklyMealsList: List<WeeklyMeals>) {
    try {
      context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { fos ->
        OutputStreamWriter(fos).use { osw ->
          BufferedWriter(osw).use { bw ->
            val json = gson.toJson(weeklyMealsList)
            bw.write(json)
          }
        }
      }
    } catch (e: IOException) {
      e.printStackTrace()
    }
  }

  fun loadWeeklyMeals(): MutableList<WeeklyMeals> {
    return try {
      context.openFileInput(FILE_NAME).use { fis ->
        InputStreamReader(fis).use { isr ->
          BufferedReader(isr).use { br ->
            val json = br.readText()
            val listType = object : TypeToken<List<WeeklyMeals>>() {}.type
            gson.fromJson<List<WeeklyMeals>>(json, listType)?.toMutableList()
              ?: mutableListOf()
          }
        }
      }
    } catch (e: IOException) {
      e.printStackTrace()
      mutableListOf()
    }
  }

  fun getWeeklyMeals(weekId: String): WeeklyMeals {
    val allWeeks = loadWeeklyMeals()
    return allWeeks.find { it.weekId == weekId } ?: WeeklyMeals(weekId)
  }

  fun saveWeeklyMeal(weeklyMeals: WeeklyMeals) {
    val allWeeks = loadWeeklyMeals()

    // Buscar si ya existe esta semana
    val existingIndex = allWeeks.indexOfFirst { it.weekId == weeklyMeals.weekId }

    if (existingIndex != -1) {
      allWeeks[existingIndex] = weeklyMeals
    } else {
      allWeeks.add(weeklyMeals)
    }

    saveWeeklyMeals(allWeeks)
  }
}