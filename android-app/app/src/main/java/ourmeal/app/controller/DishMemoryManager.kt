package ourmeal.app.controller

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import ourmeal.app.model.DishIngredients
import ourmeal.app.model.DishMemory
import java.io.File
import java.io.IOException

class DishMemoryManager(context: Context) {
  private val file = AtomicFile(File(context.filesDir, "dish_ingredients.json"))
  private val gson = Gson()

  /** Empty memory when the file does not exist; IOException when it cannot be read. */
  fun load(): DishMemory = synchronized(FILE_LOCK) {
    if (!file.baseFile.exists()) return DishMemory()
    try {
      file.openRead().bufferedReader().use { reader ->
        gson.fromJson(reader, DishMemory::class.java)?.sanitized() ?: DishMemory()
      }
    } catch (error: Exception) {
      Log.e("DishMemoryManager", "Could not read dish ingredients", error)
      throw IOException("No se pudieron leer los ingredientes recordados", error)
    }
  }

  /** Read-modify-write under FILE_LOCK. False when reading or writing fails. */
  fun save(changes: List<DishIngredients>): Boolean = synchronized(FILE_LOCK) {
    if (changes.isEmpty()) return true
    // A damaged file is never overwritten: it may still hold the user's ingredients.
    val current = try { load() } catch (_: IOException) { return false }
    var output: java.io.FileOutputStream? = null
    try {
      output = file.startWrite()
      output.write(gson.toJson(current.upsert(changes)).toByteArray(Charsets.UTF_8))
      file.finishWrite(output)
      true
    } catch (error: Exception) {
      output?.let(file::failWrite)
      Log.e("DishMemoryManager", "Could not save dish ingredients", error)
      false
    }
  }

  private companion object {
    /** Shared across activity-scoped instances so read-modify-write stays atomic. */
    val FILE_LOCK = Any()
  }
}
