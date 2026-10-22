package ourmeal.app.model

/** Same limit as the product and list name fields. */
const val ITEM_NAME_MAX_LENGTH = 80

data class MergeResult(val list: UserList, val added: Int, val skipped: Int)

/** Normalized name cut to [ITEM_NAME_MAX_LENGTH] characters. */
fun itemName(raw: String): String = normalizeName(raw).take(ITEM_NAME_MAX_LENGTH).trimEnd()

/**
 * Returns a copy of the list with the new names on top, skipping matchKey duplicates.
 * [MergeResult.skipped] counts the names that were already in the list.
 */
fun UserList.withMergedItems(names: List<String>, now: Long): MergeResult {
  val existingKeys = items.mapTo(HashSet()) { matchKey(it.name) }
  val newKeys = HashSet<String>()
  val usedIds = items.mapTo(HashSet()) { it.id }
  var nextId = now
  var skipped = 0
  val added = mutableListOf<ListItem>()
  names.forEach { raw ->
    val name = itemName(raw)
    val key = matchKey(name)
    when {
      name.isEmpty() || key in newKeys -> Unit
      key in existingKeys -> skipped++
      else -> {
        newKeys += key
        while (nextId in usedIds) nextId++
        usedIds += nextId
        added += ListItem(id = nextId, name = name, isCompleted = false, createdAt = now, updatedAt = now)
      }
    }
  }
  val merged = copy(
    items = (added + items.map { it.copy() }).toMutableList(),
    updatedAt = if (added.isEmpty()) updatedAt else now
  )
  return MergeResult(merged, added.size, skipped)
}
