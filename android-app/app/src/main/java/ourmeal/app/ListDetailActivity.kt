package ourmeal.app

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import app.ourmeat.R
import ourmeal.app.controller.DataManager
import ourmeal.app.controller.ItemAdapter
import ourmeal.app.model.UserList
import ourmeal.app.model.ListItem

class ListDetailActivity : AppCompatActivity() {

  private lateinit var recyclerView: RecyclerView
  private lateinit var itemAdapter: ItemAdapter
  private lateinit var dataManager: DataManager
  private lateinit var tvListTitle: TextView
  private lateinit var btnBack: ImageButton

  private var currentList: UserList? = null
  private var listId: Long = -1

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_list_detail)

    // Obtener ID de la lista
    listId = intent.getLongExtra("LIST_ID", -1)
    if (listId == -1L) {
      Toast.makeText(this, "Error: Lista no encontrada", Toast.LENGTH_SHORT).show()
      finish()
      return
    }

    initComponents()
    loadListData()
    setupRecyclerView()
    setupFab()
    setupBackButton()
  }

  private fun initComponents() {
    recyclerView = findViewById(R.id.recyclerViewItems)
    tvListTitle = findViewById(R.id.tvListTitle)
    btnBack = findViewById(R.id.btnBack)
    dataManager = DataManager(this)
  }

  private fun loadListData() {
    val allLists = dataManager.loadLists()
    currentList = allLists.find { it.id == listId }

    currentList?.let { list ->
      tvListTitle.text = list.name
    } ?: run {
      Toast.makeText(this, "Lista no encontrada", Toast.LENGTH_SHORT).show()
      finish()
    }
  }

  private fun setupRecyclerView() {
    currentList?.let { list ->
      itemAdapter = ItemAdapter(
        items = list.items,
        onItemLongClick = { position ->
          showDeleteItemDialog(position)
        },
        onItemCheckedChange = { position, isChecked ->
          currentList?.items?.get(position)?.isCompleted = isChecked
          saveCurrentList()
        }
      )

      recyclerView.apply {
        layoutManager = LinearLayoutManager(this@ListDetailActivity)
        adapter = itemAdapter
      }
    }
  }

  private fun setupFab() {
    val fab = findViewById<FloatingActionButton>(R.id.fabAddItem)
    fab.setOnClickListener {
      showAddItemDialog()
    }
  }

  private fun setupBackButton() {
    btnBack.setOnClickListener {
      finish()
    }
  }

  private fun showAddItemDialog() {
    val dialogView = layoutInflater.inflate(R.layout.dialog_add_item, null)
    val etItemName = dialogView.findViewById<TextInputEditText>(R.id.etItemName)

    val dialog = MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
      .setTitle("Nuevo Item")
      .setView(dialogView)
      .setPositiveButton("Agregar") { _, _ ->
        val itemName = etItemName.text.toString().trim()
        if (itemName.isNotEmpty()) {
          addNewItem(itemName)
        } else {
          Toast.makeText(this, "Por favor ingresa un nombre", Toast.LENGTH_SHORT).show()
        }
      }
      .setNegativeButton("Cancelar", null)
      .show()

    // Colores de botones coherentes con la app
    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#fa9154"))
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.LTGRAY)
  }

  private fun addNewItem(name: String) {
    currentList?.let { list ->
      val newItem = ListItem(
        id = System.currentTimeMillis(),
        name = name,
        isCompleted = false,
        createdAt = System.currentTimeMillis()
      )

      list.items.add(0, newItem)
      itemAdapter.notifyItemInserted(0)
      recyclerView.scrollToPosition(0)

      // Guardar cambios
      saveCurrentList()

      Toast.makeText(this, "Item '$name' agregado", Toast.LENGTH_SHORT).show()
    }
  }

  private fun showDeleteItemDialog(position: Int) {
    currentList?.let { list ->
      val item = list.items[position]

      MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
        .setTitle("Eliminar Item")
        .setMessage("¿Estás seguro de que deseas eliminar '${item.name}'?")
        .setPositiveButton("Eliminar") { _, _ ->
          deleteItem(position)
        }
        .setNegativeButton("Cancelar", null)
        .show()
    }
  }

  private fun deleteItem(position: Int) {
    currentList?.let { list ->
      val itemName = list.items[position].name
      list.items.removeAt(position)
      itemAdapter.notifyItemRemoved(position)

      // Guardar cambios
      saveCurrentList()

      Toast.makeText(this, "Item '$itemName' eliminado", Toast.LENGTH_SHORT).show()
    }
  }

  private fun saveCurrentList() {
    val allLists = dataManager.loadLists().toMutableList()
    val index = allLists.indexOfFirst { it.id == listId }

    if (index != -1 && currentList != null) {
      allLists[index] = currentList!!
      dataManager.saveLists(allLists)
    }
  }
}
