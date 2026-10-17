package ourmeal.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.ourmeat.R
import app.ourmeat.databinding.ActivityListBinding
import app.ourmeat.databinding.DialogCreateListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ourmeal.app.controller.DataManager
import ourmeal.app.controller.ListAdapter
import ourmeal.app.model.UserList
import ourmeal.app.model.normalizeName
import java.io.IOException

class ListActivity : AppCompatActivity() {
  private lateinit var binding: ActivityListBinding
  private lateinit var dataManager: DataManager
  private lateinit var adapter: ListAdapter
  private var lists: List<UserList> = emptyList()
  private var loadJob: Job? = null
  private var firstResume = true
  private var isSaving = false
  private var pendingUndo: (() -> Unit)? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    )
    binding = ActivityListBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.root.applyScreenInsets()
    dataManager = DataManager(this)

    adapter = ListAdapter(onOpen = ::openList, onDelete = ::confirmDelete)
    binding.recyclerViewLists.layoutManager = LinearLayoutManager(this)
    binding.recyclerViewLists.adapter = adapter
    binding.btnBackToMain.setOnClickListener { finish() }
    binding.btnAddList.setOnClickListener { showCreateDialog() }
    loadLists()
  }

  override fun onResume() {
    super.onResume()
    if (firstResume) {
      firstResume = false
    } else {
      loadLists(showLoading = false)
    }
  }

  private fun loadLists(showLoading: Boolean = true) {
    loadJob?.cancel()
    if (showLoading) {
      binding.recyclerViewLists.isVisible = false
      binding.statePanel.root.isVisible = false
      binding.loadingPanel.root.isVisible = true
      binding.loadingPanel.bindLoading(getString(R.string.lists_loading))
      binding.tvListCount.text = ""
      binding.btnAddList.isEnabled = false
    }

    loadJob = lifecycleScope.launch {
      val loaded = try {
        withContext(Dispatchers.IO) { dataManager.loadLists() }
      } catch (_: IOException) {
        null
      }
      binding.loadingPanel.root.isVisible = false
      if (loaded == null) {
        showErrorState()
        return@launch
      }
      lists = loaded.sortedByDescending { it.createdAt }
      adapter.submitList(lists)
      binding.tvListCount.text =
        resources.getQuantityString(R.plurals.list_count, lists.size, lists.size)
      binding.btnAddList.isEnabled = true
      if (lists.isEmpty()) {
        showEmptyState()
      } else {
        binding.recyclerViewLists.reveal()
        binding.statePanel.root.isVisible = false
      }
    }
  }

  private fun showEmptyState() {
    binding.recyclerViewLists.isVisible = false
    binding.statePanel.root.reveal()
    binding.statePanel.bindState(
      title = getString(R.string.lists_empty_title),
      body = getString(R.string.lists_empty_body),
      icon = R.drawable.ic_basket
    )
  }

  private fun showErrorState() {
    adapter.submitList(emptyList())
    lists = emptyList()
    binding.recyclerViewLists.isVisible = false
    binding.tvListCount.text = ""
    binding.btnAddList.isEnabled = false
    binding.statePanel.root.reveal()
    binding.statePanel.bindState(
      title = getString(R.string.lists_error_title),
      body = getString(R.string.lists_error_body),
      icon = R.drawable.ic_alert,
      actionLabel = getString(R.string.retry),
      onAction = { loadLists() }
    )
  }

  private fun showCreateDialog() {
    if (isSaving) return
    val dialogBinding = DialogCreateListBinding.inflate(layoutInflater)
    val dialog = ourmealDialog()
      .setTitle(R.string.add_list)
      .setMessage(R.string.list_name_dialog_message)
      .setView(dialogBinding.root)
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.create, null)
      .create()

    dialog.setOnShowListener {
      val confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
      confirm.isEnabled = false
      dialogBinding.etListName.doAfterTextChanged { text ->
        confirm.isEnabled = text?.isNotBlank() == true
        dialogBinding.listNameField.error = null
      }
      dialogBinding.etListName.setOnEditorActionListener { _, _, _ ->
        if (confirm.isEnabled) {
          confirm.performClick()
          true
        } else {
          false
        }
      }
      dialogBinding.etListName.requestFocus()
      dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
      confirm.setOnClickListener { createList(dialog, confirm, dialogBinding) }
    }
    dialog.show()
  }

  private fun createList(
    dialog: AlertDialog,
    confirm: android.widget.Button,
    dialogBinding: DialogCreateListBinding
  ) {
    val name = normalizeName(dialogBinding.etListName.text?.toString().orEmpty())
    if (name.isEmpty()) {
      dialogBinding.listNameField.error = getString(R.string.list_name_required)
      return
    }
    dialogBinding.listNameField.error = null
    confirm.isEnabled = false
    confirm.setText(R.string.creating)
    setSaving(true)

    lifecycleScope.launch {
      val list = UserList(nextListId(), name, mutableListOf(), System.currentTimeMillis())
      val saved = withContext(Dispatchers.IO) { dataManager.saveLists(listOf(list) + lists) }
      if (isFinishing || isDestroyed) return@launch
      setSaving(false)
      confirm.setText(R.string.create)
      if (!saved) {
        confirm.isEnabled = true
        if (dialog.isShowing) {
          dialogBinding.listNameField.error = getString(R.string.list_create_error)
        } else {
          binding.rootLists.showSnackbar(
            message = getString(R.string.list_create_error),
            anchor = binding.btnAddList
          )
        }
        return@launch
      }
      dialog.dismiss()
      lists = (listOf(list) + lists).sortedByDescending { it.createdAt }
      adapter.submitList(lists)
      binding.tvListCount.text =
        resources.getQuantityString(R.plurals.list_count, lists.size, lists.size)
      binding.btnAddList.isEnabled = true
      binding.recyclerViewLists.reveal()
      binding.statePanel.root.isVisible = false
      binding.recyclerViewLists.scrollToPosition(0)
      binding.rootLists.showSnackbar(
        message = getString(R.string.list_created),
        anchor = binding.btnAddList
      )
    }
  }

  private fun nextListId(): Long =
    generateSequence(System.currentTimeMillis()) { it + 1 }
      .first { candidate -> lists.none { it.id == candidate } }

  private fun confirmDelete(list: UserList) {
    if (isSaving) return
    val message = if (list.items.isEmpty()) {
      getString(R.string.delete_list_message_empty, list.name)
    } else {
      getString(R.string.delete_list_message, list.name)
    }
    ourmealDialog()
      .setTitle(R.string.delete_list_title)
      .setMessage(message)
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.delete) { _, _ -> deleteList(list) }
      .show()
  }

  private fun deleteList(list: UserList) {
    if (isSaving) return
    setSaving(true)
    lifecycleScope.launch {
      val deleted = withContext(Dispatchers.IO) { dataManager.deleteList(list.id) }
      if (isFinishing || isDestroyed) return@launch
      setSaving(false)
      if (!deleted) {
        binding.rootLists.showSnackbar(
          message = getString(R.string.list_delete_error),
          anchor = binding.btnAddList
        )
        return@launch
      }
      lists = lists.filterNot { it.id == list.id }
      adapter.submitList(lists)
      binding.tvListCount.text =
        resources.getQuantityString(R.plurals.list_count, lists.size, lists.size)
      if (lists.isEmpty()) showEmptyState()
      binding.rootLists.showSnackbar(
        message = getString(R.string.list_deleted),
        actionLabel = getString(R.string.undo),
        anchor = binding.btnAddList,
        onAction = { restoreList(list) }
      )
    }
  }

  private fun restoreList(list: UserList) {
    if (isSaving) {
      pendingUndo = { restoreList(list) }
      return
    }
    if (lists.any { it.id == list.id }) return
    setSaving(true)
    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) { dataManager.saveLists(lists + list) }
      if (isFinishing || isDestroyed) return@launch
      setSaving(false)
      if (!saved) {
        binding.rootLists.showSnackbar(
          message = getString(R.string.list_create_error),
          anchor = binding.btnAddList
        )
        return@launch
      }
      lists = (lists + list).sortedByDescending { it.createdAt }
      adapter.submitList(lists)
      binding.tvListCount.text =
        resources.getQuantityString(R.plurals.list_count, lists.size, lists.size)
      binding.recyclerViewLists.reveal()
      binding.statePanel.root.isVisible = false
    }
  }

  private fun openList(list: UserList) {
    if (isSaving) return
    startActivity(
      Intent(this, ListDetailActivity::class.java)
        .putExtra(ListDetailActivity.EXTRA_LIST_ID, list.id)
    )
  }

  private fun setSaving(saving: Boolean) {
    isSaving = saving
    adapter.setInteractionsEnabled(!saving)
    binding.btnAddList.isEnabled = !saving
    if (!saving) {
      pendingUndo?.let { undo ->
        pendingUndo = null
        binding.root.post(undo)
      }
    }
  }
}
