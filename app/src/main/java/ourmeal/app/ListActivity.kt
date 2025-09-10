package ourmeal.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.widget.LinearLayout
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import app.ourmeat.R
import ourmeal.app.controller.DataManager
import ourmeal.app.controller.ListAdapter
import ourmeal.app.model.RecyclerItemClickListener
import ourmeal.app.model.UserList

class ListActivity : AppCompatActivity() {
  private lateinit var recyclerView: RecyclerView
  private lateinit var listAdapter: ListAdapter
  private lateinit var dataManager: DataManager
  private var userLists = mutableListOf<UserList>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_list)

    val btnBackToMain = findViewById<ImageButton>(R.id.btnBackToMain)
    btnBackToMain.setOnClickListener {
      finish() // Esto cierra ListActivity y vuelve a MainActivity
    }


    // Inicializar componentes
    initComponents()
    setupRecyclerView()
    loadData()
    setupFab()
  }

  private fun initComponents() {
    recyclerView = findViewById(R.id.recyclerViewLists)
    dataManager = DataManager(this)
  }

  private fun showDeleteListDialog(position: Int) {
    val list = userLists[position]

    val dialog = MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
      .setTitle("Eliminar Lista")
      .setMessage("¿Deseas eliminar '${list.name}'?")
      .setPositiveButton("Eliminar") { _, _ ->
        deleteList(position)
      }
      .setNegativeButton("Cancelar", null)
      .show()

    // Cambiar color de botones
    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#fa9154"))
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.LTGRAY)
  }

  private fun deleteList(position: Int) {
    val list = userLists[position]

    // Eliminar del adapter
    listAdapter.removeList(position)

    // Eliminar del almacenamiento
    dataManager.deleteList(list.id)

    Toast.makeText(this, "Lista '${list.name}' eliminada", Toast.LENGTH_SHORT).show()
  }

  private fun setupRecyclerView() {
    listAdapter = ListAdapter(userLists) { position ->
      openListDetail(userLists[position])
    }

    recyclerView.apply {
      layoutManager = LinearLayoutManager(this@ListActivity)
      adapter = listAdapter
    }

    // Agregar long click a cada item
    recyclerView.addOnItemTouchListener(
      RecyclerItemClickListener(
        this,
        recyclerView,
        object : RecyclerItemClickListener.OnItemClickListener {
          override fun onItemClick(view: View, position: Int) {
            openListDetail(userLists[position])
          }

          override fun onItemLongClick(view: View, position: Int) {
            showDeleteListDialog(position)
          }
        }
      )
    )
  }

  private fun loadData() {
    userLists.clear()
    userLists.addAll(dataManager.loadLists())
    listAdapter.notifyDataSetChanged()
  }

  private fun setupFab() {
    val fab = findViewById<FloatingActionButton>(R.id.fabAddList)
    fab.setOnClickListener {
      showCreateListDialog()
    }
  }

  private fun showCreateListDialog() {
    val dialogView = layoutInflater.inflate(R.layout.dialog_create_list, null)
    val etListName = dialogView.findViewById<TextInputEditText>(R.id.etListName)

    MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
      .setTitle("Nueva Lista")
      .setView(dialogView)
      .setPositiveButton("Crear") { _, _ ->
        val listName = etListName.text.toString().trim()
        if (listName.isNotEmpty()) {
          createNewList(listName)
        } else {
          Toast.makeText(this, "Por favor ingresa un nombre", Toast.LENGTH_SHORT).show()
        }
      }
      .setNegativeButton("Cancelar", null)
      .show()
  }

  private fun createNewList(name: String) {
    val newList = UserList(
      id = System.currentTimeMillis(),
      name = name,
      items = mutableListOf(),
      createdAt = System.currentTimeMillis()
    )

    userLists.add(0, newList)
    listAdapter.notifyItemInserted(0)
    recyclerView.scrollToPosition(0)

    // Guardar datos
    dataManager.saveLists(userLists)

    Toast.makeText(this, "Lista '$name' creada", Toast.LENGTH_SHORT).show()
  }

  private fun openListDetail(list: UserList) {
    val intent = Intent(this, ListDetailActivity::class.java)
    intent.putExtra("LIST_ID", list.id)
    startActivity(intent)
  }

  override fun onResume() {
    super.onResume()
    loadData() // Recargar datos cuando volvemos a la actividad principal
  }

  override fun onBackPressed() {
    super.onBackPressed()
    finish() // vuelve a MainActivity
  }
}