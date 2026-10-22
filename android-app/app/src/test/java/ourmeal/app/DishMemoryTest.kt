package ourmeal.app

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ourmeal.app.model.DISH_MEMORY_VERSION
import ourmeal.app.model.DishIngredients
import ourmeal.app.model.DishMemory

class DishMemoryTest {
  private val gson = Gson()

  private fun entry(key: String, at: Long, vararg ingredients: String) =
    DishIngredients(key, key.replaceFirstChar(Char::uppercase), ingredients.toList(), at)

  @Test
  fun upsertReplacesByKeyAndKeepsTheNewestFirst() {
    val memory = DishMemory(dishes = listOf(entry("lentejas", 1L, "Lentejas"), entry("pasta", 2L, "Pasta")))
    val updated = memory.upsert(listOf(entry("lentejas", 3L, "Lentejas", "Chorizo")))
    assertEquals(listOf("lentejas", "pasta"), updated.dishes.map { it.key })
    assertEquals(listOf("Lentejas", "Chorizo"), updated.ingredientsFor("lentejas"))
  }

  @Test
  fun upsertRemovesDishesWithoutIngredients() {
    val memory = DishMemory(dishes = listOf(entry("lentejas", 1L, "Lentejas")))
    val updated = memory.upsert(listOf(entry("lentejas", 2L)))
    assertNull(updated.ingredientsFor("lentejas"))
    assertEquals(emptyList<DishIngredients>(), updated.dishes)
  }

  @Test
  fun upsertKeepsTheMostRecentUpToTheCap() {
    val memory = DishMemory(dishes = (1..3).map { entry("plato $it", it.toLong(), "Sal") })
    val updated = memory.upsert(listOf(entry("nuevo", 10L, "Sal")), cap = 3)
    assertEquals(listOf("nuevo", "plato 3", "plato 2"), updated.dishes.map { it.key })
  }

  @Test
  fun survivesAGsonRoundTrip() {
    val memory = DishMemory(dishes = listOf(entry("lentejas", 5L, "Lentejas", "Chorizo")))
    val json = gson.toJson(memory)
    assertEquals(
      """{"version":1,"dishes":[{"key":"lentejas","name":"Lentejas","ingredients":["Lentejas","Chorizo"],"updated_at":5}]}""",
      json
    )
    assertEquals(memory, gson.fromJson(json, DishMemory::class.java).sanitized())
  }

  @Test
  fun sanitizesMissingAndNullValues() {
    assertEquals(DishMemory(), gson.fromJson("{}", DishMemory::class.java).sanitized())
    val json = """
      {"version":0,"dishes":[
        null,
        {"name":"Sin clave","ingredients":["Sal"],"updated_at":1},
        {"key":"sopa","name":"Sopa","updated_at":1},
        {"key":"vacio","name":"Vacío","ingredients":[" ",null],"updated_at":1},
        {"key":"pasta","name":" Pasta ","ingredients":[null,"Tomate","tomate"," Queso "],"updated_at":2}
      ]}
    """.trimIndent()
    assertEquals(
      DishMemory(DISH_MEMORY_VERSION, listOf(DishIngredients("pasta", "Pasta", listOf("Tomate", "Queso"), 2L))),
      gson.fromJson(json, DishMemory::class.java).sanitized()
    )
  }
}
