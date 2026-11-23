package ourmeal.app.model

/** Completion of a product before a bulk change, so the change can be undone. */
data class ItemMark(val id: Long, val isCompleted: Boolean, val updatedAt: Long)

/** The list after marking everything as pending, with what each changed product was before. */
data class BulkUncheck(val list: UserList, val previous: List<ItemMark>)

/** The list without its bought products, with each removed product and where it was. */
data class BoughtRemoval(val list: UserList, val removed: List<IndexedValue<ListItem>>)

/** Deep copy: editing the copy never changes the products of the original. */
fun UserList.deepCopy(): UserList = copy(items = items.map { it.copy() }.toMutableList())

/**
 * Marks every product as pending. It is not a walk through the shop, so callers must not teach
 * it to the shopping route.
 */
fun UserList.allPending(now: Long): BulkUncheck {
  val previous = items.filter { it.isCompleted }.map { ItemMark(it.id, it.isCompleted, it.updatedAt) }
  val updated = copy(items = items.map { item ->
    if (item.isCompleted) item.copy(isCompleted = false, updatedAt = now) else item.copy()
  }.toMutableList())
  return BulkUncheck(updated, previous)
}

/** Puts back the completion of [marks] on the products that still exist. */
fun UserList.withMarks(marks: List<ItemMark>): UserList {
  val byId = marks.associateBy { it.id }
  return copy(items = items.map { item ->
    val mark = byId[item.id] ?: return@map item.copy()
    item.copy(isCompleted = mark.isCompleted, updatedAt = mark.updatedAt)
  }.toMutableList())
}

fun UserList.withoutBought(): BoughtRemoval {
  val removed = items.withIndex().filter { it.value.isCompleted }.map { IndexedValue(it.index, it.value.copy()) }
  val kept = items.filterNot { it.isCompleted }.map { it.copy() }.toMutableList()
  return BoughtRemoval(copy(items = kept), removed)
}

/** Inserts [removed] back at their former places; a product already in the list is skipped. */
fun UserList.withRestored(removed: List<IndexedValue<ListItem>>): UserList {
  val restored = items.map { it.copy() }.toMutableList()
  val present = restored.mapTo(HashSet()) { it.id }
  removed.sortedBy { it.index }.forEach { (index, item) ->
    if (item.id in present) return@forEach
    restored.add(index.coerceIn(0, restored.size), item.copy())
    present += item.id
  }
  return copy(items = restored)
}

/** Renames the product [itemId]; the result is null when it no longer exists. */
fun UserList.withItemName(itemId: Long, name: String, now: Long): UserList? {
  if (items.none { it.id == itemId }) return null
  return copy(items = items.map { item ->
    if (item.id == itemId) item.copy(name = name, updatedAt = now) else item.copy()
  }.toMutableList())
}

/** True when another product of the list already has [name], ignoring case, accents and spacing. */
fun UserList.hasOtherItemNamed(name: String, exceptId: Long): Boolean {
  val key = matchKey(name)
  return items.any { it.id != exceptId && matchKey(it.name) == key }
}

/** Suggested name for a copy of [name], within the name length limit. */
fun copyName(name: String, suffix: String): String {
  val room = ITEM_NAME_MAX_LENGTH - suffix.length
  return itemName(normalizeName(name).take(room).trimEnd() + suffix)
}
