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
 * One json file in `filesDir`, written atomically under a lock shared by every instance of the
 * same file. A missing file reads as [empty]; a damaged one fails and, unless [discardDamaged]
 * (disposable caches only), is never overwritten because it may still hold the user's data.
 */
internal class JsonFileStore<T : Any>(
  context: Context,
  private val fileName: String,
  private val type: Class<T>,
  private val empty: () -> T,
  private val sanitize: (T) -> T,
  private val discardDamaged: Boolean = false
) {
  private val file = AtomicFile(File(context.filesDir, fileName))
  private val gson = Gson()
  private val lock: Any = locks.getOrPut(fileName) { Any() }

  /** IOException when the file exists but cannot be read. */
  fun read(): T = synchronized(lock) {
    if (!file.baseFile.exists()) return empty()
    try {
      file.openRead().bufferedReader().use { reader -> gson.fromJson(reader, type)?.let(sanitize) ?: empty() }
    } catch (error: Exception) {
      Log.e(TAG, "Could not read $fileName", error)
      throw IOException("No se pudo leer $fileName", error)
    }
  }

  /** Read-modify-write under the lock. Null when reading or writing fails. */
  fun update(change: (T) -> T): T? = synchronized(lock) {
    val current = try {
      read()
    } catch (error: IOException) {
      if (!discardDamaged) return null
      empty()
    }
    val next = change(current)
    if (next == current && file.baseFile.exists()) return next
    var output: FileOutputStream? = null
    try {
      output = file.startWrite()
      output.write(gson.toJson(next).toByteArray(Charsets.UTF_8))
      file.finishWrite(output)
      next
    } catch (error: Exception) {
      output?.let(file::failWrite)
      Log.e(TAG, "Could not save $fileName", error)
      null
    }
  }

  private companion object {
    const val TAG = "JsonFileStore"
    val locks = ConcurrentHashMap<String, Any>()
  }
}
