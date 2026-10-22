package ourmeal.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.ourmeat.R
import app.ourmeat.databinding.ActivityListDetailBinding
import app.ourmeat.databinding.DialogAddItemBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ourmeal.app.controller.DataManager
import ourmeal.app.controller.ItemAdapter
import ourmeal.app.model.ListItem
import ourmeal.app.model.UserList
import ourmeal.app.model.matchKey
import ourmeal.app.model.normalizeName
import java.io.IOException

class ListDetailActivity : AppCompatActivity() {
  private lateinit var binding: ActivityListDetailBinding
  private lateinit var dataManager: DataManager
  private lateinit var adapter: ItemAdapter
  private var currentList: UserList? = null
  private var listId = -1L
  private var loadJob: Job? = null
  private lateinit var creationForm: CreationFormState
  private var isSaving = false
  private var pendingUndo: (() -> Unit)? = null
  /** A result from the menu is announced once, not again after recreation. */
  private var showMenuResult = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    creationForm = CreationFormState(this)
    showMenuResult = savedInstanceState == null && intent.hasExtra(EXTRA_ADDED_COUNT)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )
    binding = ActivityListDetailBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.root.applyScreenInsets()
    dataManager = DataManager(this)
    listId = intent.getLongExtra(EXTRA_LIST_ID, -1L)

    adapter = ItemAdapter(onCheck = ::setCompleted, onDelete = ::confirmDelete)
    binding.recyclerViewItems.layoutManager = LinearLayoutManager(this)
    binding.recyclerViewItems.adapter = adapter
    binding.btnBack.setOnClickListener { finish() }
    binding.btnAddItem.setOnClickListener { showAddDialog() }
    binding.btnShare.setOnClickListener { shareList() }
    loadList()
  }

  private fun loadList() {
    loadJob?.cancel()
    currentList = null
    renderLoading()
    loadJob = lifecycleScope.launch {
      var failed = false
      val list = try {
        withContext(Dispatchers.IO) { dataManager.getListById(listId) }
      } catch (_: IOException) {
        failed = true
        null
      }
      binding.loadingPanel.root.isVisible = false
      if (failed) {
        showErrorState()
        return@launch
      }
      if (list == null) {
        showMissingState()
        return@launch
      }
      bindList(list)
      announceMenuResult()
      creationForm.restore(::showAddDialog)
    }
  }

  private fun announceMenuResult() {
    if (!showMenuResult) return
    showMenuResult = false
    val added = intent.getIntExtra(EXTRA_ADDED_COUNT, 0).coerceAtLeast(0)
    val skipped = intent.getIntExtra(EXTRA_SKIPPED_COUNT, 0).coerceAtLeast(0)
    val memoryFailed = intent.getBooleanExtra(EXTRA_MEMORY_FAILED, false)
    listOf(EXTRA_ADDED_COUNT, EXTRA_SKIPPED_COUNT, EXTRA_MEMORY_FAILED).forEach(intent::removeExtra)
    val message = buildList {
      if (added == 0) {
        add(getString(R.string.menu_shopping_all_present))
      } else {
        add(resources.getQuantityString(R.plurals.menu_shopping_added, added, added))
        if (skipped > 0) {
          add(resources.getQuantityString(R.plurals.menu_shopping_result_skipped, skipped, skipped))
        }
      }
      if (memoryFailed) add(getString(R.string.menu_shopping_memory_failed))
    }.joinToString(" ")
    binding.rootDetail.showSnackbar(
      message = message,
      anchor = binding.actionBar,
      duration = Snackbar.LENGTH_LONG
    )
  }

  private fun renderLoading() {
    binding.recyclerViewItems.isVisible = false
    binding.statePanel.root.isVisible = false
    binding.loadingPanel.root.isVisible = true
    binding.loadingPanel.bindLoading(getString(R.string.detail_loading))
    binding.progressCard.isVisible = false
    binding.actionBar.isVisible = false
    binding.tvListTitle.isVisible = false
    binding.tvListSubtitle.isVisible = false
    binding.tvItemCount.text = ""
  }

  private fun bindList(list: UserList) {
    currentList = list
    binding.tvListTitle.isVisible = true
    binding.tvListSubtitle.isVisible = true
    binding.tvListTitle.text = list.name
    binding.tvItemCount.text =
      resources.getQuantityString(R.plurals.item_count, list.items.size, list.items.size)
    binding.progressCard.isVisible = true
    binding.actionBar.isVisible = true
    adapter.submitList(list.items.toList())
    updateSummary()
  }

  private fun updateSummary() {
    val list = currentList ?: return
    val total = list.getTotalCount()
    val done = list.getCompletedCount()
    val percentage = list.getCompletionPercentage()
    binding.tvItemCount.text =
      resources.getQuantityString(R.plurals.item_count, total, total)
    binding.tvProgress.text = when {
      total == 0 -> getString(R.string.progress_empty)
      done == total -> getString(R.string.progress_done)
      else -> getString(R.string.progress_partial, done, total)
    }
    binding.progressCompletion.setProgressWithMotion(percentage)
    binding.progressCompletion.contentDescription =
      "${getString(R.string.progress_completion)}: $percentage%"
    binding.btnShare.isEnabled = total > 0 && !isSaving
    renderItemsState(total == 0)
  }

  private fun renderItemsState(empty: Boolean) {
    if (empty) {
      binding.recyclerViewItems.isVisible = false
      binding.statePanel.root.reveal()
    } else {
      binding.statePanel.root.isVisible = false
      binding.recyclerViewItems.reveal()
    }
    if (empty) {
      binding.statePanel.bindState(
        title = getString(R.string.items_empty_title),
        body = getString(R.string.items_empty_body),
        icon = R.drawable.ic_basket
      )
    }
  }

  private fun showErrorState() {
    currentList = null
    adapter.submitList(emptyList())
    binding.progressCard.isVisible = false
    binding.actionBar.isVisible = false
    binding.tvListTitle.isVisible = false
    binding.tvListSubtitle.isVisible = false
    binding.tvItemCount.text = ""
    binding.recyclerViewItems.isVisible = false
    binding.statePanel.root.reveal()
    binding.statePanel.bindState(
      title = getString(R.string.detail_error_title),
      body = getString(R.string.detail_error_body),
      icon = R.drawable.ic_alert,
      actionLabel = getString(R.string.retry),
      onAction = ::loadList
    )
  }

  private fun showMissingState() {
    currentList = null
    adapter.submitList(emptyList())
    binding.progressCard.isVisible = false
    binding.actionBar.isVisible = false
    binding.tvListTitle.isVisible = false
    binding.tvListSubtitle.isVisible = false
    binding.tvItemCount.text = ""
    binding.recyclerViewItems.isVisible = false
    binding.statePanel.root.reveal()
    binding.statePanel.bindState(
      title = getString(R.string.detail_missing_title),
      body = getString(R.string.detail_missing_body),
      icon = R.drawable.ic_alert,
      actionLabel = getString(R.string.back_to_lists_action),
      onAction = ::finish
    )
  }

  private fun showAddDialog() {
    if (isSaving || creationForm.isShowing) return
    val list = currentList ?: return
    val builder = ourmealDialog()
    val dialogBinding = DialogAddItemBinding.inflate(android.view.LayoutInflater.from(builder.context))
    val dialog = builder
      .setTitle(R.string.add_item_title)
      .setView(dialogBinding.root)
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.add, null)
      .create()

    creationForm.attach(dialog, dialogBinding.etItemName)
    dialog.prepareForm(dialogBinding.itemNameField, dialogBinding.etItemName) { confirm ->
      addItem(list, dialog, confirm, dialogBinding)
    }
    dialog.show()
  }

  private fun addItem(
    list: UserList,
    dialog: AlertDialog,
    confirm: android.widget.Button,
    dialogBinding: DialogAddItemBinding
  ) {
    if (isSaving) return
    val name = normalizeName(dialogBinding.etItemName.text?.toString().orEmpty())
    if (name.isEmpty()) {
      dialogBinding.itemNameField.error = getString(R.string.item_name_required)
      return
    }
    val key = matchKey(name)
    val duplicate = list.items.indexOfFirst { matchKey(it.name) == key }
    if (duplicate >= 0) {
      dialogBinding.itemNameField.error = getString(R.string.item_exists)
      binding.recyclerViewItems.scrollToPosition(duplicate)
      return
    }
    dialogBinding.itemNameField.error = null
    dialog.setFormSaving(dialogBinding.itemNameField, true)
    confirm.setText(R.string.adding)

    val item = ListItem(nextItemId(list), name, false, System.currentTimeMillis())
    list.items.add(0, item)
    setSaving(true)
    val snapshot = list.snapshot()
    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) { dataManager.updateList(snapshot) }
      if (isFinishing || isDestroyed) return@launch
      setSaving(false)
      dialog.setFormSaving(dialogBinding.itemNameField, false)
      confirm.setText(R.string.add)
      if (!saved) {
        list.items.remove(item)
        confirm.isEnabled = true
        if (dialog.isShowing) {
          dialogBinding.itemNameField.error = getString(R.string.item_save_error)
        } else {
          binding.rootDetail.showSnackbar(
            message = getString(R.string.item_save_error),
            anchor = binding.actionBar
          )
        }
        return@launch
      }
      dialog.dismiss()
      adapter.submitList(list.items.toList())
      binding.recyclerViewItems.scrollToPosition(0)
      updateSummary()
      binding.rootDetail.showSnackbar(
        message = getString(R.string.item_added),
        anchor = binding.actionBar
      )
    }
  }

  private fun nextItemId(list: UserList): Long =
    generateSequence(System.currentTimeMillis()) { it + 1 }
      .first { candidate -> list.items.none { it.id == candidate } }

  private fun setCompleted(adapterItem: ListItem, completed: Boolean) {
    if (isSaving) return
    val list = currentList ?: return
    val item = list.items.firstOrNull { it.id == adapterItem.id } ?: return
    val previous = item.isCompleted
    val previousUpdatedAt = item.updatedAt
    if (previous == completed) return
    setSaving(true)
    item.isCompleted = completed
    item.updatedAt = System.currentTimeMillis()
    adapter.submitList(list.items.toList())
    updateSummary()
    val snapshot = list.snapshot()

    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) { dataManager.updateList(snapshot) }
      if (isFinishing || isDestroyed) return@launch
      if (!saved) {
        item.isCompleted = previous
        item.updatedAt = previousUpdatedAt
        adapter.submitList(list.items.toList())
        updateSummary()
        binding.rootDetail.showSnackbar(
          message = getString(R.string.item_toggle_error),
          anchor = binding.actionBar
        )
      }
      setSaving(false)
    }
  }

  private fun confirmDelete(adapterItem: ListItem) {
    if (isSaving) return
    ourmealDialog(destructive = true)
      .setTitle(R.string.delete_item_title)
      .setMessage(getString(R.string.delete_item_message, adapterItem.name))
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.delete) { _, _ -> deleteItem(adapterItem) }
      .show()
  }

  private fun deleteItem(adapterItem: ListItem) {
    if (isSaving) return
    val list = currentList ?: return
    val item = list.items.firstOrNull { it.id == adapterItem.id } ?: return
    val index = list.items.indexOfFirst { it.id == item.id }
    if (index < 0) return
    setSaving(true)
    list.items.removeAt(index)
    adapter.submitList(list.items.toList())
    updateSummary()
    val snapshot = list.snapshot()

    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) { dataManager.updateList(snapshot) }
      if (isFinishing || isDestroyed) return@launch
      setSaving(false)
      if (!saved) {
        list.items.add(index.coerceAtMost(list.items.size), item)
        adapter.submitList(list.items.toList())
        updateSummary()
        binding.rootDetail.showSnackbar(
          message = getString(R.string.item_delete_error),
          anchor = binding.actionBar
        )
        return@launch
      }
      binding.rootDetail.showSnackbar(
        message = getString(R.string.item_deleted),
        actionLabel = getString(R.string.undo),
        anchor = binding.actionBar,
        onAction = { restoreItem(item, index) }
      )
    }
  }

  private fun restoreItem(item: ListItem, index: Int) {
    if (isSaving) {
      pendingUndo = { restoreItem(item, index) }
      return
    }
    val list = currentList ?: return
    if (list.items.any { it.id == item.id }) return
    setSaving(true)
    list.items.add(index.coerceAtMost(list.items.size), item)
    adapter.submitList(list.items.toList())
    updateSummary()
    val snapshot = list.snapshot()
    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) { dataManager.updateList(snapshot) }
      if (isFinishing || isDestroyed) return@launch
      setSaving(false)
      if (!saved) {
        list.items.remove(item)
        adapter.submitList(list.items.toList())
        updateSummary()
        binding.rootDetail.showSnackbar(
          message = getString(R.string.item_save_error),
          anchor = binding.actionBar
        )
      }
    }
  }

  private fun shareList() {
    val list = currentList ?: return
    if (list.items.isEmpty()) return
    val text = buildString {
      appendLine(list.name)
      appendLine(getString(R.string.share_header))
      appendLine(
        resources.getQuantityString(
          R.plurals.share_summary,
          list.getTotalCount(),
          list.getCompletedCount(),
          list.getTotalCount()
        )
      )
      appendLine()
      list.items.forEach { item ->
        appendLine("${if (item.isCompleted) "[x]" else "[ ]"} ${item.name}")
      }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, list.name)
      putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
      startActivity(Intent.createChooser(intent, getString(R.string.share_chooser)))
    } catch (_: ActivityNotFoundException) {
      binding.rootDetail.showSnackbar(
        message = getString(R.string.share_error),
        anchor = binding.actionBar
      )
    }
  }

  private fun setSaving(saving: Boolean) {
    isSaving = saving
    adapter.setInteractionsEnabled(!saving)
    binding.btnAddItem.isEnabled = !saving
    binding.btnShare.isEnabled = !saving && (currentList?.items?.isNotEmpty() == true)
    if (!saving) {
      pendingUndo?.let { undo ->
        pendingUndo = null
        binding.root.post(undo)
      }
    }
  }

  private fun UserList.snapshot(): UserList = copy(items = items.map { it.copy() }.toMutableList())

  companion object {
    const val EXTRA_LIST_ID = "LIST_ID"
    const val EXTRA_ADDED_COUNT = "ADDED_COUNT"
    const val EXTRA_SKIPPED_COUNT = "SKIPPED_COUNT"
    const val EXTRA_MEMORY_FAILED = "MEMORY_FAILED"
  }
}
