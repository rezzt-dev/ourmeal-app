package ourmeal.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.ourmeat.R
import app.ourmeat.databinding.ActivityListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ourmeal.app.controller.DataManager
import ourmeal.app.controller.ListAdapter
import ourmeal.app.model.UserList
import ourmeal.app.model.itemName
import java.io.IOException

class ListActivity : AppCompatActivity() {
  private lateinit var binding: ActivityListBinding
  private lateinit var dataManager: DataManager
  private lateinit var adapter: ListAdapter
  private var lists: List<UserList> = emptyList()
  private var loadJob: Job? = null
  private var firstResume = true
  private lateinit var creationForm: CreationFormState
  private var isSaving = false
  private var pendingUndo: (() -> Unit)? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    creationForm = CreationFormState(this)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    )
    binding = ActivityListBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.root.applyScreenInsets()
    dataManager = DataManager(this)

    adapter = ListAdapter(onOpen = ::openList, onRename = ::showRenameDialog, onDelete = ::confirmDelete)
    binding.recyclerViewLists.layoutManager = LinearLayoutManager(this)
    binding.recyclerViewLists.adapter = adapter
    binding.btnBackToMain.setOnClickListener { finish() }
    binding.btnAddList.setOnClickListener { showCreateDialog() }
    binding.btnOpenPurchases.setOnClickListener {
      startActivity(Intent(this, PurchaseHistoryActivity::class.java))
    }
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
      showListCount(lists.size)
      binding.btnAddList.isEnabled = true
      if (lists.isEmpty()) {
        showEmptyState()
      } else {
        binding.recyclerViewLists.reveal()
        binding.statePanel.root.isVisible = false
      }
      creationForm.restore { kind, target ->
        when (kind) {
          FormKind.RENAME_LIST -> lists.firstOrNull { it.id == target }?.let(::showRenameDialog)
          else -> showCreateDialog()
        }
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
    if (isSaving || creationForm.isShowing) return
    openNameForm(
      state = creationForm,
      kind = FormKind.CREATE,
      nameKind = NameKind.LIST,
      spec = NameFormSpec(
        title = R.string.add_list,
        message = getString(R.string.list_name_dialog_message),
        confirm = R.string.create,
        busy = R.string.creating
      ),
      onSubmit = ::createList
    )
  }

  private fun createList(form: NameForm) {
    if (isSaving) return
    val name = form.name
    if (name.isEmpty()) {
      form.showError(getString(R.string.list_name_required))
      return
    }
    form.clearError()
    form.setBusy(true)
    setSaving(true)

    lifecycleScope.launch {
      val list = UserList(nextListId(), name, mutableListOf(), System.currentTimeMillis())
      val saved = withContext(Dispatchers.IO) { dataManager.saveLists(listOf(list) + lists) }
      if (isFinishing || isDestroyed) return@launch
      setSaving(false)
      form.setBusy(false)
      if (!saved) {
        showFormError(form, getString(R.string.list_create_error))
        return@launch
      }
      form.dialog.dismiss()
      lists = (listOf(list) + lists).sortedByDescending { it.createdAt }
      adapter.submitList(lists)
      showListCount(lists.size)
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

  /** An error the open form can show stays in it; otherwise it becomes a snackbar. */
  private fun showFormError(form: OpenForm, message: String) {
    if (form.dialog.isShowing) {
      form.showError(message)
    } else {
      binding.rootLists.showSnackbar(message = message, anchor = binding.btnAddList)
    }
  }

  // EDITAR NOMBRES

  private fun showRenameDialog(list: UserList) {
    if (isSaving || creationForm.isShowing) return
    openNameForm(
      state = creationForm,
      kind = FormKind.RENAME_LIST,
      nameKind = NameKind.LIST,
      target = list.id,
      spec = NameFormSpec(
        title = R.string.rename_list_title,
        message = getString(R.string.rename_list_message),
        confirm = R.string.save,
        busy = R.string.saving,
        initial = list.name
      )
    ) { form -> submitRename(list.id, form) }
  }

  private fun submitRename(listId: Long, form: NameForm) {
    if (isSaving) return
    val list = lists.firstOrNull { it.id == listId } ?: return form.dialog.dismiss()
    val name = itemName(form.name)
    if (name.isEmpty()) {
      form.showError(getString(R.string.list_name_required))
      return
    }
    if (name == list.name) {
      form.dialog.dismiss()
      return
    }
    form.clearError()
    form.setBusy(true)
    renameList(listId, from = list.name, to = name) { saved ->
      form.setBusy(false)
      if (!saved) {
        showFormError(form, getString(R.string.rename_error))
        return@renameList
      }
      form.dialog.dismiss()
      binding.rootLists.showSnackbar(
        message = getString(R.string.list_renamed),
        actionLabel = getString(R.string.undo),
        anchor = binding.btnAddList,
        onAction = { undoRename(listId, renamedTo = name, previous = list.name) }
      )
    }
  }

  /** Undo only applies while the list still has the name it was given. */
  private fun undoRename(listId: Long, renamedTo: String, previous: String) {
    if (isSaving) {
      pendingUndo = { undoRename(listId, renamedTo, previous) }
      return
    }
    if (lists.firstOrNull { it.id == listId }?.name != renamedTo) return
    renameList(listId, from = renamedTo, to = previous) { saved ->
      if (!saved) {
        binding.rootLists.showSnackbar(message = getString(R.string.rename_error), anchor = binding.btnAddList)
      }
    }
  }

  /** Renames on disk, so products changed meanwhile on another screen are kept. */
  private fun renameList(listId: Long, from: String, to: String, onResult: (Boolean) -> Unit) {
    setSaving(true)
    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) { dataManager.renameList(listId, to) }
      if (isFinishing || isDestroyed) return@launch
      setSaving(false)
      if (saved) {
        lists = lists.map { if (it.id == listId && it.name == from) it.copy(name = to) else it }
        adapter.submitList(lists)
      }
      onResult(saved)
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
    ourmealDialog(destructive = true)
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
      showListCount(lists.size)
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
      showListCount(lists.size)
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

  /** The empty state already says there are no lists, so the counter only shows real ones. */
  private fun showListCount(count: Int) {
    binding.tvListCount.text =
      if (count == 0) "" else resources.getQuantityString(R.plurals.list_count, count, count)
  }
}
