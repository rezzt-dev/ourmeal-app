package ourmeal.app

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import ourmeal.app.controller.DataManager
import ourmeal.app.controller.DishSuggestionManager
import ourmeal.app.controller.MealDataManager
import ourmeal.app.controller.ShoppingRouteManager
import java.io.File

/**
 * Runs each test on empty app data and puts the device's own files back afterwards,
 * so instrumented tests never read or overwrite real lists and menus.
 */
class AppDataSandbox : ExternalResource() {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val filesDir = context.filesDir
  private val backupDir = File(context.noBackupFilesDir, "test-data-backup")

  override fun before() {
    // A previous run killed mid-test leaves the real data in the backup: recover it first.
    restoreBackup()
    backupDir.mkdirs()
    filesDir.listFiles()?.forEach { file ->
      check(file.renameTo(File(backupDir, file.name))) { "Could not move ${file.name} aside" }
    }
  }

  override fun after() {
    // Screens save on pause; wait for any write still holding a file lock before restoring.
    runCatching { MealDataManager(context).loadWeeklyMeals() }
    runCatching { DataManager(context).loadLists() }
    runCatching { DishSuggestionManager(context).load() }
    runCatching { ShoppingRouteManager(context).load() }
    restoreBackup()
  }

  private fun restoreBackup() {
    val saved = backupDir.listFiles() ?: return
    filesDir.listFiles()?.forEach { it.deleteRecursively() }
    saved.forEach { file ->
      check(file.renameTo(File(filesDir, file.name))) { "Could not restore ${file.name}" }
    }
    backupDir.deleteRecursively()
  }
}
