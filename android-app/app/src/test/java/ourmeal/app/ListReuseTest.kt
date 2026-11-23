package ourmeal.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ourmeal.app.model.ITEM_NAME_MAX_LENGTH
import ourmeal.app.model.ListItem
import ourmeal.app.model.UserList
import ourmeal.app.model.allPending
import ourmeal.app.model.copyName
import ourmeal.app.model.hasOtherItemNamed
import ourmeal.app.model.withItemName
import ourmeal.app.model.withMarks
import ourmeal.app.model.withRestored
import ourmeal.app.model.withoutBought

class ListReuseTest {
  private fun list(vararg items: Pair<String, Boolean>) = UserList(
    id = 1L,
    name = "Compra",
    items = items.mapIndexed { index, (name, bought) ->
      ListItem(id = index + 10L, name = name, isCompleted = bought, createdAt = 0L, updatedAt = 5L)
    }.toMutableList(),
    createdAt = 0L
  )

  @Test
  fun uncheckingAllKeepsOrderAndCanBeUndone() {
    val original = list("Leche" to true, "Pan" to false, "Huevos" to true)

    val result = original.allPending(now = 99L)

    assertEquals(listOf("Leche", "Pan", "Huevos"), result.list.items.map { it.name })
    assertTrue(result.list.items.none { it.isCompleted })
    assertEquals(listOf(10L, 12L), result.previous.map { it.id })
    assertEquals(99L, result.list.items[0].updatedAt)
    assertEquals(5L, result.list.items[1].updatedAt)
    // The original list objects are not modified.
    assertTrue(original.items[0].isCompleted)

    val undone = result.list.withMarks(result.previous)
    assertEquals(original.items, undone.items)
  }

  @Test
  fun removingBoughtCanBeUndoneInPlace() {
    val original = list("Leche" to true, "Pan" to false, "Huevos" to true, "Arroz" to false)

    val result = original.withoutBought()
    assertEquals(listOf("Pan", "Arroz"), result.list.items.map { it.name })
    assertEquals(listOf(0, 2), result.removed.map { it.index })

    val restored = result.list.withRestored(result.removed)
    assertEquals(original.items, restored.items)
    // Restoring again does not duplicate products.
    assertEquals(original.items, restored.withRestored(result.removed).items)
  }

  @Test
  fun restoringAfterOtherRemovalsStaysWithinBounds() {
    val result = list("Leche" to false, "Pan" to false, "Huevos" to true).withoutBought()
    val shorter = result.list.copy(items = mutableListOf())

    val restored = shorter.withRestored(result.removed)

    assertEquals(listOf("Huevos"), restored.items.map { it.name })
  }

  @Test
  fun renamingKeepsStateAndPlaceAndIgnoresMissingProducts() {
    val original = list("Leche" to true, "Pan" to false)

    val renamed = original.withItemName(10L, "Leche sin lactosa", now = 50L)!!

    assertEquals("Leche sin lactosa", renamed.items[0].name)
    assertTrue(renamed.items[0].isCompleted)
    assertEquals(50L, renamed.items[0].updatedAt)
    assertEquals("Leche", original.items[0].name)
    assertNull(original.withItemName(404L, "Nada", now = 50L))
  }

  @Test
  fun duplicateNamesIgnoreTheProductBeingRenamed() {
    val original = list("Plátano" to false, "Pan" to false)

    assertFalse(original.hasOtherItemNamed("platano", exceptId = 10L))
    assertTrue(original.hasOtherItemNamed("  PLATANO ", exceptId = 11L))
  }

  @Test
  fun copyNameStaysWithinTheLimit() {
    assertEquals("Compra (copia)", copyName("Compra ", " (copia)"))
    val long = "a".repeat(ITEM_NAME_MAX_LENGTH)
    val copy = copyName(long, " (copia)")
    assertEquals(ITEM_NAME_MAX_LENGTH, copy.length)
    assertTrue(copy.endsWith(" (copia)"))
  }
}
