package ourmeal.app.model

enum class MealSlot { COMIDA, CENA }

fun Meal.textFor(slot: MealSlot): String = when (slot) {
  MealSlot.COMIDA -> comida
  MealSlot.CENA -> cena
}
