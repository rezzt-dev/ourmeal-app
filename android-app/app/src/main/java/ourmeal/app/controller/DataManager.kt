package ourmeal.app.controller

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import ourmeal.app.model.AppData
import ourmeal.app.model.UserList
import ourmeal.app.model.itemName
import ourmeal.app.model.withMergedItems
import java.io.File
import java.io.IOException

sealed interface MergeOutcome {
  data class Saved(val listId: Long, val added: Int, val skipped: Int) : MergeOutcome
  data object ListMissing : MergeOutcome
  data object Failed : MergeOutcome
}

class DataManager(context: Context) {
  private val file = AtomicFile(File(context.filesDir, "user_lists.json"))
  private val gson = Gson()

  fun loadLists(): List<UserList> = synchronized(FILE_LOCK) {
    if (!file.baseFile.exists()) return emptyList()
    try {
      file.openRead().bufferedReader().use { reader ->
        gson.fromJson(reader, AppData::class.java)?.lists ?: throw IOException("Archivo vacío")
      }
    } catch (error: Exception) {
      Log.e("DataManager", "Could not read shopping lists", error)
      throw IOException("No se pudieron leer las listas", error)
    }
  }

  fun saveLists(lists: List<UserList>): Boolean = synchronized(FILE_LOCK) {
    var output: java.io.FileOutputStream? = null
    try {
      output = file.startWrite()
      val payload = AppData(lists = lists.toMutableList(), lastBackup = System.currentTimeMillis())
      output.write(gson.toJson(payload).toByteArray(Charsets.UTF_8))
      file.finishWrite(output)
      true
    } catch (error: Exception) {
      output?.let(file::failWrite)
      Log.e("DataManager", "Could not save shopping lists", error)
      false
    }
  }

  fun updateList(updatedList: UserList): Boolean = synchronized(FILE_LOCK) {
    val lists = try { loadLists().toMutableList() } catch (_: IOException) { return false }
    val index = lists.indexOfFirst { it.id == updatedList.id }
    if (index < 0) return false
    updatedList.updatedAt = System.currentTimeMillis()
    lists[index] = updatedList
    saveLists(lists)
  }

  fun deleteList(listId: Long): Boolean = synchronized(FILE_LOCK) {
    val lists = try { loadLists().toMutableList() } catch (_: IOException) { return false }
    if (!lists.removeAll { it.id == listId }) return false
    saveLists(lists)
  }

  fun getListById(listId: Long): UserList? = synchronized(FILE_LOCK) {
    loadLists().find { it.id == listId }
  }

  /** Adds [names] on top of the stored list, merging against the file and not a stale copy. */
  fun mergeIntoList(listId: Long, names: List<String>): MergeOutcome = synchronized(FILE_LOCK) {
    val lists = try { loadLists().toMutableList() } catch (_: IOException) { return MergeOutcome.Failed }
    val index = lists.indexOfFirst { it.id == listId }
    if (index < 0) return MergeOutcome.ListMissing
    val result = lists[index].withMergedItems(names, System.currentTimeMillis())
    lists[index] = result.list
    if (saveLists(lists)) MergeOutcome.Saved(listId, result.added, result.skipped) else MergeOutcome.Failed
  }

  /** Creates a list holding [names], on top of the stored lists. */
  fun createListWithItems(name: String, names: List<String>): MergeOutcome = synchronized(FILE_LOCK) {
    val lists = try { loadLists() } catch (_: IOException) { return MergeOutcome.Failed }
    val now = System.currentTimeMillis()
    val id = generateSequence(now) { it + 1 }.first { candidate -> lists.none { it.id == candidate } }
    val empty = UserList(id, itemName(name), mutableListOf(), createdAt = now, updatedAt = now)
    val result = empty.withMergedItems(names, now)
    if (saveLists(listOf(result.list) + lists)) {
      MergeOutcome.Saved(id, result.added, result.skipped)
    } else {
      MergeOutcome.Failed
    }
  }

  private companion object {
    /** Shared across activity-scoped instances so read-modify-write stays atomic. */
    val FILE_LOCK = Any()
  }
}
