package ourmeal.app.controller

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * One JSON document in `filesDir`, written atomically. Reads and read-modify-writes of the same
 * file share one lock across every instance, so writes from different screens never interleave.
 * A file that cannot be read is never overwritten: it may still hold the user's data.
 */
internal class JsonDocument<T : Any>(
  context: Context,
  fileName: String,
  private val type: Class<T>,
  private val empty: () -> T,
  private val sanitize: (T) -> T
) {
  private val file = AtomicFile(File(context.filesDir, fileName))
  private val lock: Any = locks.getOrPut(fileName) { Any() }
  private val tag = "JsonDocument[$fileName]"

  /** The empty document when the file does not exist; IOException when it cannot be read. */
  fun load(): T = synchronized(lock) {
    if (!file.baseFile.exists()) return empty()
    try {
      file.openRead().bufferedReader().use { reader -> gson.fromJson(reader, type)?.let(sanitize) ?: empty() }
    } catch (error: Exception) {
      Log.e(tag, "Could not read the document", error)
      throw IOException("Could not read ${file.baseFile.name}", error)
    }
  }

  /** Applies [change] to the stored document; the written result, or null when reading or writing fails. */
  fun update(change: (T) -> T): T? = synchronized(lock) {
    val current = try { load() } catch (_: IOException) { return null }
    val next = change(current)
    if (next == current) return next
    var output: FileOutputStream? = null
    try {
      output = file.startWrite()
      output.write(gson.toJson(next).toByteArray(Charsets.UTF_8))
      file.finishWrite(output)
      next
    } catch (error: Exception) {
      output?.let(file::failWrite)
      Log.e(tag, "Could not write the document", error)
      null
    }
  }

  private companion object {
    val gson = Gson()
    val locks = ConcurrentHashMap<String, Any>()
  }
}
