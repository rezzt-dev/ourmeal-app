package ourmeal.app.controller

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ourmeal.app.model.AppData
import ourmeal.app.model.ListItem
import ourmeal.app.model.UserList
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

class DataManager(private val context: Context) {

  private val gson = Gson()
  private val fileName = "user_lists.json"

  /**
   * Guarda las listas en un archivo JSON
   */
  fun saveLists(lists: List<UserList>): Boolean {
    return try {
      val file = File(context.filesDir, fileName)
      val appData = AppData(
        lists = lists.toMutableList(),
        lastBackup = System.currentTimeMillis()
      )

      FileWriter(file).use { writer ->
        gson.toJson(appData, writer)
      }
      true
    } catch (e: IOException) {
      e.printStackTrace()
      false
    }
  }

  /**
   * Carga las listas desde el archivo JSON
   */
  fun loadLists(): List<UserList> {
    return try {
      val file = File(context.filesDir, fileName)

      if (!file.exists()) {
        // Si el archivo no existe, crear datos de ejemplo
        return createSampleData()
      }

      FileReader(file).use { reader ->
        val type = object : TypeToken<AppData>() {}.type
        val appData: AppData = gson.fromJson(reader, type)
        appData.lists
      }
    } catch (e: Exception) {
      e.printStackTrace()
      // Si hay error, devolver datos de ejemplo
      createSampleData()
    }
  }

  /**
   * Crea datos de ejemplo para la primera vez que se abre la app
   */
  private fun createSampleData(): List<UserList> {
    val sampleLists = listOf(
      UserList(
        id = 1,
        name = "Lista de la compra",
        items = mutableListOf(
          ListItem(1, "Leche", false, System.currentTimeMillis()),
          ListItem(2, "Pan", true, System.currentTimeMillis()),
          ListItem(3, "Huevos", false, System.currentTimeMillis()),
          ListItem(4, "Tomates", false, System.currentTimeMillis())
        ),
        createdAt = System.currentTimeMillis()
      ),
      UserList(
        id = 2,
        name = "Tareas del hogar",
        items = mutableListOf(
          ListItem(5, "Limpiar cocina", true, System.currentTimeMillis()),
          ListItem(6, "Aspirar salón", false, System.currentTimeMillis()),
          ListItem(7, "Lavar ropa", false, System.currentTimeMillis())
        ),
        createdAt = System.currentTimeMillis()
      ),
      UserList(
        id = 3,
        name = "Objetivos del mes",
        items = mutableListOf(
          ListItem(8, "Hacer ejercicio 3 veces por semana", false, System.currentTimeMillis()),
          ListItem(9, "Leer un libro", false, System.currentTimeMillis()),
          ListItem(10, "Organizar escritorio", true, System.currentTimeMillis())
        ),
        createdAt = System.currentTimeMillis()
      )
    )

    // Guardar los datos de ejemplo
    saveLists(sampleLists)
    return sampleLists
  }

  /**
   * Elimina una lista específica
   */
  fun deleteList(listId: Long): Boolean {
    val currentLists = loadLists().toMutableList()
    val listToRemove = currentLists.find { it.id == listId }

    return if (listToRemove != null) {
      currentLists.remove(listToRemove)
      saveLists(currentLists)
    } else {
      false
    }
  }

  /**
   * Actualiza una lista específica
   */
  fun updateList(updatedList: UserList): Boolean {
    val currentLists = loadLists().toMutableList()
    val index = currentLists.indexOfFirst { it.id == updatedList.id }

    return if (index != -1) {
      updatedList.updatedAt = System.currentTimeMillis()
      currentLists[index] = updatedList
      saveLists(currentLists)
    } else {
      false
    }
  }

  /**
   * Obtiene una lista específica por ID
   */
  fun getListById(listId: Long): UserList? {
    return loadLists().find { it.id == listId }
  }

  /**
   * Exporta los datos a formato JSON como string
   */
  fun exportDataAsJson(): String {
    val allLists = loadLists()
    val appData = AppData(
      lists = allLists.toMutableList(),
      lastBackup = System.currentTimeMillis()
    )
    return gson.toJson(appData)
  }

  /**
   * Importa datos desde un JSON string
   */
  fun importDataFromJson(jsonString: String): Boolean {
    return try {
      val type = object : TypeToken<AppData>() {}.type
      val appData: AppData = gson.fromJson(jsonString, type)
      saveLists(appData.lists)
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }
}