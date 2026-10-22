package ourmeal.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import ourmeal.app.model.ListItem
import ourmeal.app.model.UserList
import ourmeal.app.model.withMergedItems

class ShoppingMergeTest {
  private val now = 1_000L

  private fun list(vararg items: Pair<Long, String>): UserList = UserList(
    id = 1L,
    name = "Compra semanal",
    items = items.map { (id, name) -> ListItem(id = id, name = name, createdAt = 0L, updatedAt = 0L) }
      .toMutableList(),
    createdAt = 0L,
    updatedAt = 0L
  )

  @Test
  fun emptyListReceivesEveryName() {
    val result = list().withMergedItems(listOf("Lentejas", "Chorizo"), now)
    assertEquals(listOf("Lentejas", "Chorizo"), result.list.items.map { it.name })
    assertEquals(2, result.added)
    assertEquals(0, result.skipped)
    result.list.items.forEach {
      assertEquals(false, it.isCompleted)
      assertEquals(now, it.createdAt)
      assertEquals(now, it.updatedAt)
    }
    assertEquals(now, result.list.updatedAt)
  }

  @Test
  fun repeatedInputNamesAreAddedOnce() {
    val result = list().withMergedItems(listOf("Aceite", "aceite ", "ACEITE"), now)
    assertEquals(listOf("Aceite"), result.list.items.map { it.name })
    assertEquals(1, result.added)
    assertEquals(0, result.skipped)
  }

  @Test
  fun skipsNamesAlreadyInTheListIgnoringCaseAndAccents() {
    val result = list(5L to "Plátano", 6L to "Aceite").withMergedItems(listOf("platano", "Pan"), now)
    assertEquals(listOf("Pan", "Plátano", "Aceite"), result.list.items.map { it.name })
    assertEquals(1, result.added)
    assertEquals(1, result.skipped)
  }

  @Test
  fun idsSkipTheOnesAlreadyInUse() {
    val result = list(now to "Pan", now + 1 to "Leche").withMergedItems(listOf("Huevos", "Sal"), now)
    val ids = result.list.items.map { it.id }
    assertEquals(listOf(now + 2, now + 3, now, now + 1), ids)
  }

  @Test
  fun leavesTheOriginalListUntouched() {
    val original = list(2L to "Pan")
    val result = original.withMergedItems(listOf("Leche"), now)
    assertEquals(listOf("Pan"), original.items.map { it.name })
    assertEquals(0L, original.updatedAt)
    assertNotSame(original.items.single(), result.list.items.last())
  }

  @Test
  fun keepsUpdatedAtWhenNothingIsAdded() {
    val result = list(2L to "Pan").withMergedItems(listOf("pan", "  "), now)
    assertEquals(0, result.added)
    assertEquals(1, result.skipped)
    assertEquals(0L, result.list.updatedAt)
  }

  @Test
  fun cutsNamesToEightyCharacters() {
    val result = list().withMergedItems(listOf("a".repeat(79) + " " + "b".repeat(10)), now)
    assertEquals("a".repeat(79), result.list.items.single().name)
  }
}
