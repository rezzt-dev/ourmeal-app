package app.ourmeat.model

data class WeeklyMeals(
  var weekId: String = "",
  var meals: MutableMap<String, Meal> = mutableMapOf()
) {
  init {
    if (meals.isEmpty()) {
      initializeWeek()
    }
  }

  private fun initializeWeek() {
    val days = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    days.forEach { day ->
      meals[day] = Meal()
    }
  }

  fun getMealForDay(day: String): Meal {
    return meals[day] ?: Meal()
  }

  fun setMealForDay(day: String, meal: Meal) {
    meals[day] = meal
  }
}