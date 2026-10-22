package ourmeal.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ourmeal.app.model.DISH_INGREDIENT_CAP
import ourmeal.app.model.DishDraft
import ourmeal.app.model.DishIngredients
import ourmeal.app.model.DishMemory
import ourmeal.app.model.IngredientEdit
import ourmeal.app.model.MenuDish
import ourmeal.app.model.hasEdits
import ourmeal.app.model.ingredientsToAdd
import ourmeal.app.model.memoryChanges
import ourmeal.app.model.toDrafts

class ShoppingDraftTest {
  private fun draft(
    name: String,
    ingredients: List<String>,
    remembered: List<String>? = null,
    included: Boolean = true
  ) = DishDraft(name.lowercase(), name, emptyList(), included, ingredients, remembered)

  @Test
  fun excludedDishesAddNothing() {
    val drafts = listOf(
      draft("Lentejas", listOf("Lentejas", "Chorizo")),
      draft("Pizza", listOf("Masa"), included = false)
    )
    assertEquals(listOf("Lentejas", "Chorizo"), drafts.ingredientsToAdd())
  }

  @Test
  fun sharedIngredientIsAddedOnceInDishOrder() {
    val drafts = listOf(
      draft("Lentejas", listOf("Aceite", "Lentejas")),
      draft("Tortilla", listOf("Huevos", "aceite"))
    )
    assertEquals(listOf("Aceite", "Lentejas", "Huevos"), drafts.ingredientsToAdd())
  }

  @Test
  fun memoryChangesOnlyHoldEditedDishesIncludingExcludedOnes() {
    val drafts = listOf(
      draft("Lentejas", listOf("Lentejas"), remembered = listOf("Lentejas")),
      draft("Tortilla", listOf("Huevos"), remembered = null),
      draft("Pizza", listOf("Masa", "Queso"), remembered = listOf("Masa"), included = false),
      draft("Sopa", emptyList(), remembered = null)
    )
    assertEquals(
      listOf(
        DishIngredients("tortilla", "Tortilla", listOf("Huevos"), 9L),
        DishIngredients("pizza", "Pizza", listOf("Masa", "Queso"), 9L)
      ),
      drafts.memoryChanges(9L)
    )
  }

  @Test
  fun emptyingARememberedDishProducesARemoval() {
    val change = listOf(draft("Lentejas", emptyList(), remembered = listOf("Lentejas"))).memoryChanges(9L)
    assertEquals(listOf(DishIngredients("lentejas", "Lentejas", emptyList(), 9L)), change)
  }

  @Test
  fun addIngredientValidatesBlankDuplicateAndLimit() {
    val lentejas = draft("Lentejas", listOf("Chorizo"))
    assertEquals(IngredientEdit.Blank, lentejas.addIngredient("   "))
    assertEquals(IngredientEdit.Duplicate, lentejas.addIngredient(" CHORIZO "))
    val added = lentejas.addIngredient("  Zanahoria  ") as IngredientEdit.Added
    assertEquals(listOf("Chorizo", "Zanahoria"), added.draft.ingredients)
    val full = draft("Lentejas", (1..DISH_INGREDIENT_CAP).map { "Ingrediente $it" })
    assertTrue(full.isFull)
    assertEquals(IngredientEdit.Full, full.addIngredient("Sal"))
  }

  @Test
  fun hasEditsWhenAnIngredientOrTheInclusionChanged() {
    assertFalse(listOf(draft("Lentejas", listOf("Lentejas"), listOf("Lentejas"))).hasEdits())
    assertFalse(listOf(draft("Sopa", emptyList())).hasEdits())
    assertTrue(listOf(draft("Sopa", emptyList(), included = false)).hasEdits())
    assertTrue(listOf(draft("Sopa", listOf("Caldo"))).hasEdits())
  }

  @Test
  fun draftsPreloadMemoryAndKeepSavedChoicesOfDishesStillPlanned() {
    val dishes = listOf(MenuDish("lentejas", "Lentejas", emptyList()), MenuDish("sopa", "Sopa", emptyList()))
    val memory = DishMemory(dishes = listOf(DishIngredients("lentejas", "Lentejas", listOf("Chorizo"), 1L)))
    val fresh = dishes.toDrafts(memory)
    assertEquals(listOf("Chorizo"), fresh[0].ingredients)
    assertEquals(listOf("Chorizo"), fresh[0].remembered)
    assertEquals(emptyList<String>(), fresh[1].ingredients)
    assertEquals(null, fresh[1].remembered)

    val saved = listOf(
      fresh[1].copy(included = false, ingredients = listOf("Caldo")),
      draft("Pizza", listOf("Masa"))
    )
    val restored = dishes.toDrafts(memory, saved)
    assertEquals(listOf("lentejas", "sopa"), restored.map { it.key })
    assertEquals(fresh[0], restored[0])
    assertFalse(restored[1].included)
    assertEquals(listOf("Caldo"), restored[1].ingredients)
  }
}
