package ourmeal.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
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
import app.ourmeat.databinding.ActivityListDetailBinding
import app.ourmeat.databinding.DialogAddItemBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ourmeal.app.controller.DataManager
import ourmeal.app.controller.ItemAdapter
import ourmeal.app.model.ListItem
import ourmeal.app.model.UserList
import ourmeal.app.model.normalizeName
import java.io.IOException

class ListDetailActivity : AppCompatActivity() {
  private lateinit var binding: ActivityListDetailBinding
  private lateinit var dataManager: DataManager
  private lateinit var adapter: ItemAdapter
  private var currentList: UserList? = null
  private var listId = -1L
  private var loadJob: Job? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
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
    }
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
    binding.progressCompletion.progress = percentage
    binding.progressCompletion.contentDescription =
      "${getString(R.string.progress_completion)}: $percentage%"
    binding.btnShare.isEnabled = total > 0
    renderItemsState(total == 0)
  }

  private fun renderItemsState(empty: Boolean) {
    binding.recyclerViewItems.isVisible = !empty
    binding.statePanel.root.isVisible = empty
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
    binding.statePanel.root.isVisible = true
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
    binding.statePanel.root.isVisible = true
    binding.statePanel.bindState(
      title = getString(R.string.detail_missing_title),
      body = getString(R.string.detail_missing_body),
      icon = R.drawable.ic_alert,
      actionLabel = getString(R.string.back_to_lists_action),
      onAction = ::finish
    )
  }

  private fun showAddDialog() {
    val list = currentList ?: return
    val dialogBinding = DialogAddItemBinding.inflate(layoutInflater)
    val dialog = ourmealDialog()
      .setTitle(R.string.add_item_title)
      .setMessage(R.string.add_item_message)
      .setView(dialogBinding.root)
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.add, null)
      .create()

    dialog.setOnShowListener {
      val confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
      confirm.isEnabled = false
      dialogBinding.etItemName.doAfterTextChanged { text ->
        confirm.isEnabled = text?.isNotBlank() == true
        dialogBinding.itemNameField.error = null
      }
      dialogBinding.etItemName.setOnEditorActionListener { _, _, _ ->
        if (confirm.isEnabled) {
          confirm.performClick()
          true
        } else {
          false
        }
      }
      dialogBinding.etItemName.requestFocus()
      dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
      confirm.setOnClickListener { addItem(list, dialog, confirm, dialogBinding) }
    }
    dialog.show()
  }

  private fun addItem(
    list: UserList,
    dialog: AlertDialog,
    confirm: android.widget.Button,
    dialogBinding: DialogAddItemBinding
  ) {
    val name = normalizeName(dialogBinding.etItemName.text?.toString().orEmpty())
    if (name.isEmpty()) {
      dialogBinding.itemNameField.error = getString(R.string.item_name_required)
      return
    }
    val duplicate = list.items.indexOfFirst { it.name.equals(name, ignoreCase = true) }
    if (duplicate >= 0) {
      dialogBinding.itemNameField.error = getString(R.string.item_exists)
      binding.recyclerViewItems.scrollToPosition(duplicate)
      return
    }
    dialogBinding.itemNameField.error = null
    confirm.isEnabled = false
    confirm.setText(R.string.adding)

    val item = ListItem(nextItemId(list), name, false, System.currentTimeMillis())
    list.items.add(0, item)
    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) { dataManager.updateList(list) }
      if (isFinishing || isDestroyed) return@launch
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
    val list = currentList ?: return
    val item = list.items.firstOrNull { it.id == adapterItem.id } ?: return
    val previous = item.isCompleted
    if (previous == completed) return
    item.isCompleted = completed
    item.updatedAt = System.currentTimeMillis()
    adapter.submitList(list.items.toList())
    updateSummary()

    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) { dataManager.updateList(list) }
      if (isFinishing || isDestroyed) return@launch
      if (!saved && item.isCompleted == completed) {
        item.isCompleted = previous
        adapter.submitList(list.items.toList())
        updateSummary()
        binding.rootDetail.showSnackbar(
          message = getString(R.string.item_toggle_error),
          anchor = binding.actionBar
        )
      }
    }
  }

  private fun confirmDelete(adapterItem: ListItem) {
    ourmealDialog()
      .setTitle(R.string.delete_item_title)
      .setMessage(getString(R.string.delete_item_message, adapterItem.name))
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.delete) { _, _ -> deleteItem(adapterItem) }
      .show()
  }

  private fun deleteItem(adapterItem: ListItem) {
    val list = currentList ?: return
    val item = list.items.firstOrNull { it.id == adapterItem.id } ?: return
    val index = list.items.indexOfFirst { it.id == item.id }
    if (index < 0) return
    list.items.removeAt(index)
    adapter.submitList(list.items.toList())
    updateSummary()

    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) { dataManager.updateList(list) }
      if (isFinishing || isDestroyed) return@launch
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
    val list = currentList ?: return
    list.items.add(index.coerceAtMost(list.items.size), item)
    adapter.submitList(list.items.toList())
    updateSummary()
    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) { dataManager.updateList(list) }
      if (isFinishing || isDestroyed) return@launch
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

  companion object {
    const val EXTRA_LIST_ID = "LIST_ID"
  }
}
