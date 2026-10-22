package ourmeal.app.controller

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import ourmeal.app.model.RouteChange
import ourmeal.app.model.ShoppingRoute
import java.io.File
import java.io.IOException

class ShoppingRouteManager(context: Context) {
  private val file = AtomicFile(File(context.filesDir, "shopping_route.json"))
  private val gson = Gson()

  /** Empty route when the file does not exist; IOException when it cannot be read. */
  fun load(): ShoppingRoute = synchronized(FILE_LOCK) {
    if (!file.baseFile.exists()) return ShoppingRoute()
    try {
      file.openRead().bufferedReader().use { reader ->
        gson.fromJson(reader, ShoppingRoute::class.java)?.sanitized() ?: ShoppingRoute()
      }
    } catch (error: Exception) {
      Log.e("ShoppingRouteManager", "Could not read shopping route", error)
      throw IOException("No se pudo leer el recorrido de compra", error)
    }
  }

  /** Read-modify-write under FILE_LOCK. Null when reading or writing fails. */
  fun update(change: (ShoppingRoute) -> RouteChange): RouteChange? = synchronized(FILE_LOCK) {
    // A damaged file is never overwritten: it may still hold the learned route.
    val current = try { load() } catch (_: IOException) { return null }
    val next = change(current)
    if (next.route == current) return next
    var output: java.io.FileOutputStream? = null
    try {
      output = file.startWrite()
      output.write(gson.toJson(next.route).toByteArray(Charsets.UTF_8))
      file.finishWrite(output)
      next
    } catch (error: Exception) {
      output?.let(file::failWrite)
      Log.e("ShoppingRouteManager", "Could not save shopping route", error)
      null
    }
  }

  private companion object {
    /** Shared across activity-scoped instances so read-modify-write stays atomic. */
    val FILE_LOCK = Any()
  }
}
