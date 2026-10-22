package ourmeal.app.controller

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import ourmeal.app.model.SuggestionSettings
import java.io.File
import java.io.IOException

class DishSuggestionManager(context: Context) {
  private val file = AtomicFile(File(context.filesDir, "dish_suggestions.json"))
  private val gson = Gson()

  /** Default settings when the file does not exist; IOException when it cannot be read. */
  fun load(): SuggestionSettings = synchronized(FILE_LOCK) {
    if (!file.baseFile.exists()) return SuggestionSettings()
    try {
      file.openRead().bufferedReader().use { reader ->
        gson.fromJson(reader, SuggestionSettings::class.java)?.sanitized() ?: SuggestionSettings()
      }
    } catch (error: Exception) {
      Log.e("DishSuggestionManager", "Could not read suggestion settings", error)
      throw IOException("No se pudieron leer los ajustes de sugerencias", error)
    }
  }

  /** Read-modify-write under FILE_LOCK. False when reading or writing fails. */
  fun update(change: (SuggestionSettings) -> SuggestionSettings): Boolean = synchronized(FILE_LOCK) {
    // A damaged file is never overwritten: it may still hold the user's choices.
    val current = try { load() } catch (_: IOException) { return false }
    val next = change(current)
    if (next == current) return true
    var output: java.io.FileOutputStream? = null
    try {
      output = file.startWrite()
      output.write(gson.toJson(next).toByteArray(Charsets.UTF_8))
      file.finishWrite(output)
      true
    } catch (error: Exception) {
      output?.let(file::failWrite)
      Log.e("DishSuggestionManager", "Could not save suggestion settings", error)
      false
    }
  }

  private companion object {
    /** Shared across activity-scoped instances so read-modify-write stays atomic. */
    val FILE_LOCK = Any()
  }
}
