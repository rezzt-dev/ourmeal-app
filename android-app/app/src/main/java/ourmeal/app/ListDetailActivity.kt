package ourmeal.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.ourmeat.R
import app.ourmeat.databinding.ActivityListDetailBinding
import app.ourmeat.databinding.DialogAddItemBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ourmeal.app.controller.DataManager
import ourmeal.app.controller.ItemAdapter
import ourmeal.app.controller.ShoppingRouteManager
import ourmeal.app.model.ListItem
import ourmeal.app.model.RouteChange
import ourmeal.app.model.RouteStop
import ourmeal.app.model.ShoppingRoute
import ourmeal.app.model.UserList
import ourmeal.app.model.inRouteOrder
import ourmeal.app.model.matchKey
import ourmeal.app.model.routePlacement
import ourmeal.app.model.normalizeName
import java.io.IOException

class ListDetailActivity : AppCompatActivity() {
  private lateinit var binding: ActivityListDetailBinding
  private lateinit var dataManager: DataManager
  private lateinit var routeManager: ShoppingRouteManager
  private lateinit var adapter: ItemAdapter
  private var currentList: UserList? = null
  private var listId = -1L
  private var loadJob: Job? = null
  private lateinit var creationForm: CreationFormState
  private var isSaving = false
  private var pendingUndo: (() -> Unit)? = null
  /** A result from the menu is announced once, not again after recreation. */
  private var showMenuResult = false
  /** Null while the route file cannot be read; the list then keeps its stored order. */
  private var route: ShoppingRoute? = null
  private val routeMutex = Mutex()
  private var isBindingRoute = false

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
    routeManager = ShoppingRouteManager(this)
    listId = intent.getLongExtra(EXTRA_LIST_ID, -1L)

    adapter = ItemAdapter(onCheck = ::setCompleted, onDelete = ::confirmDelete)
    binding.recyclerViewItems.layoutManager = LinearLayoutManager(this)
    binding.recyclerViewItems.adapter = adapter
    binding.btnBack.setOnClickListener { finish() }
    binding.btnAddItem.setOnClickListener { showAddDialog() }
    binding.btnShare.setOnClickListener { shareList() }
    binding.routeOrder.switchRouteOrder.setOnCheckedChangeListener { _, checked -> setSortByRoute(checked) }
    binding.routeOrder.btnForgetRoute.setOnClickListener { confirmForgetRoute() }
    loadList()
  }

  private fun loadList() {
    loadJob?.cancel()
    currentList = null
    renderLoading()
    loadJob = lifecycleScope.launch {
      var failed = false
      val (list, learned) = try {
        withContext(Dispatchers.IO) {
          // A trip that ended since the last visit is learned now, before sorting the list.
          val settled = routeManager.update { it.settle(System.currentTimeMillis()) }
          dataManager.getListById(listId) to settled
        }
      } catch (_: IOException) {
        failed = true
        null to null
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
      route = learned?.route
      bindList(list)
      if (!announceMenuResult()) announceLearnedRoute(learned?.learned ?: 0)
      creationForm.restore(::showAddDialog)
    }
  }

  /** True when a result was announced. */
  private fun announceMenuResult(): Boolean {
    if (!showMenuResult) return false
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
    return true
  }

  private fun announceLearnedRoute(learned: Int) {
    if (learned <= 0) return
    binding.rootDetail.showSnackbar(
      message = resources.getQuantityString(R.plurals.route_learned, learned, learned),
      anchor = binding.actionBar
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
    binding.routeOrder.root.isVisible = false
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
    adapter.submitList(displayedItems(list))
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
    renderRoute(list)
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
    binding.routeOrder.root.isVisible = false
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
    binding.routeOrder.root.isVisible = false
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
    val duplicate = displayedItems(list).indexOfFirst { matchKey(it.name) == key }
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
      val shown = displayedItems(list)
      adapter.submitList(shown)
      binding.recyclerViewItems.scrollToPosition(shown.indexOfFirst { it.id == item.id }.coerceAtLeast(0))
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
    val shownAt = displayedItems(list).indexOfFirst { it.id == item.id }
    setSaving(true)
    item.isCompleted = completed
    item.updatedAt = System.currentTimeMillis()
    val markedAt = item.updatedAt
    submitKeepingScroll(list)
    updateSummary()
    val snapshot = list.snapshot()

    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) { dataManager.updateList(snapshot) }
      if (isFinishing || isDestroyed) return@launch
      if (!saved) {
        item.isCompleted = previous
        item.updatedAt = previousUpdatedAt
        submitKeepingScroll(list)
        updateSummary()
        binding.rootDetail.showSnackbar(
          message = getString(R.string.item_toggle_error),
          anchor = binding.actionBar
        )
      }
      setSaving(false)
      if (saved) {
        keepKeyboardPlace(shownAt, item.id)
        learnRoute(item.name, completed, markedAt)
      }
    }
  }

  /** A keyboard user stays at the same row when the product they marked moves down the list. */
  private fun keepKeyboardPlace(position: Int, movedId: Long) {
    if (binding.root.isInTouchMode || position < 0) return
    val shown = displayedItems(currentList ?: return)
    if (shown.getOrNull(position)?.id == movedId) return
    // Rows are enabled again on the next layout; only then can they take focus.
    binding.recyclerViewItems.doOnNextLayout {
      binding.recyclerViewItems.findViewHolderForAdapterPosition(position.coerceAtMost(shown.lastIndex))
        ?.itemView?.findViewById<View>(R.id.cbItemCompleted)?.requestFocus()
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
    adapter.submitList(displayedItems(list))
    updateSummary()
    val snapshot = list.snapshot()

    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) { dataManager.updateList(snapshot) }
      if (isFinishing || isDestroyed) return@launch
      setSaving(false)
      if (!saved) {
        list.items.add(index.coerceAtMost(list.items.size), item)
        adapter.submitList(displayedItems(list))
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
    adapter.submitList(displayedItems(list))
    updateSummary()
    val snapshot = list.snapshot()
    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) { dataManager.updateList(snapshot) }
      if (isFinishing || isDestroyed) return@launch
      setSaving(false)
      if (!saved) {
        list.items.remove(item)
        adapter.submitList(displayedItems(list))
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
      // Whoever receives the list gets it in the order shown, route included.
      displayedItems(list).forEach { item ->
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

  // RECORRIDO DE COMPRA

  /** Items as shown: in route order when the user follows the route, otherwise as stored. */
  private fun displayedItems(list: UserList): List<ListItem> {
    val route = route
    return if (route?.sortByRoute == true) list.items.inRouteOrder(route) else list.items.toList()
  }

  /**
   * The list keeps its scroll by position: otherwise it would follow a marked product down to
   * the bought ones and hide the next pending ones.
   */
  private fun submitKeepingScroll(list: UserList) {
    val layout = binding.recyclerViewItems.layoutManager ?: return adapter.submitList(displayedItems(list))
    val scroll = layout.onSaveInstanceState()
    adapter.submitList(displayedItems(list))
    layout.onRestoreInstanceState(scroll)
  }

  private fun refreshItems() {
    val list = currentList ?: return
    adapter.submitList(displayedItems(list))
    updateSummary()
  }

  private fun renderRoute(list: UserList) {
    val route = route
    binding.tvListSubtitle.text = routeStatus(list, route)
    binding.routeOrder.root.isVisible = route != null && list.items.isNotEmpty()
    if (route == null) return
    val switch = binding.routeOrder.switchRouteOrder
    if (switch.isChecked != route.sortByRoute) {
      isBindingRoute = true
      switch.isChecked = route.sortByRoute
      isBindingRoute = false
    }
    binding.routeOrder.btnForgetRoute.isVisible = route.hasStops
  }

  private fun routeStatus(list: UserList, route: ShoppingRoute?): String {
    if (route == null) return getString(R.string.route_status_unavailable)
    if (list.items.isEmpty()) return getString(R.string.detail_subtitle)
    if (!route.sortByRoute) return getString(R.string.route_status_list_order)
    val placement = list.items.routePlacement(route)
    return when {
      placement.pending == 0 -> getString(R.string.route_status_done)
      !route.hasStops -> getString(R.string.route_status_learning)
      placement.placed == 0 -> getString(R.string.route_status_unplaced)
      placement.placed == placement.pending -> getString(R.string.route_status_all)
      else -> resources.getQuantityString(
        R.plurals.route_status_partial, placement.pending, placement.placed, placement.pending
      )
    }
  }

  /** Every product marked or put back teaches the route, whichever order is on screen. */
  private fun learnRoute(name: String, completed: Boolean, at: Long) {
    if (route == null) return
    val key = matchKey(name)
    writeRoute({ if (completed) it.tick(key, at) else it.untick(key, at) }) { result ->
      val saved = result?.route ?: return@writeRoute
      val shown = route ?: return@writeRoute
      // The sort choice may have its own write pending; take only what this one learned.
      route = shown.copy(stops = saved.stops, trip = saved.trip)
      if (saved.stops != shown.stops) refreshItems()
    }
  }

  /** Applied at once and rolled back if it cannot be saved. */
  private fun setSortByRoute(enabled: Boolean) {
    if (isBindingRoute) return
    val current = route ?: return
    if (current.sortByRoute == enabled) return
    route = current.copy(keepListOrder = !enabled)
    refreshItems()
    binding.recyclerViewItems.scrollToPosition(0)
    writeRoute({ RouteChange(it.copy(keepListOrder = !enabled)) }) { result ->
      if (result != null) return@writeRoute
      route = route?.copy(keepListOrder = enabled)
      refreshItems()
      binding.rootDetail.showSnackbar(
        message = getString(R.string.route_save_error),
        anchor = binding.actionBar
      )
    }
  }

  private fun confirmForgetRoute() {
    ourmealDialog(destructive = true)
      .setTitle(R.string.route_forget_title)
      .setMessage(R.string.route_forget_message)
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.route_forget) { _, _ -> forgetRoute() }
      .show()
  }

  private fun forgetRoute() {
    val previous = route?.stops ?: return
    writeRoute({ RouteChange(it.forget()) }) { result ->
      if (result == null) {
        binding.rootDetail.showSnackbar(
          message = getString(R.string.route_forget_error),
          anchor = binding.actionBar
        )
        return@writeRoute
      }
      route = route?.copy(stops = emptyList(), trip = emptyList())
      refreshItems()
      binding.rootDetail.showSnackbar(
        message = getString(R.string.route_forgotten),
        actionLabel = getString(R.string.undo),
        anchor = binding.actionBar,
        onAction = { restoreRoute(previous) }
      )
    }
  }

  /** Undo brings back what was learned; products marked since then stay in the new trip. */
  private fun restoreRoute(stops: List<RouteStop>) {
    writeRoute({ RouteChange(it.copy(stops = stops)) }) { result ->
      if (result == null) {
        binding.rootDetail.showSnackbar(
          message = getString(R.string.route_save_error),
          anchor = binding.actionBar
        )
        return@writeRoute
      }
      route = route?.copy(stops = result.route.stops)
      refreshItems()
    }
  }

  /** Route writes keep their call order and finish even if the screen goes away meanwhile. */
  private fun writeRoute(change: (ShoppingRoute) -> RouteChange, onResult: (RouteChange?) -> Unit) {
    lifecycleScope.launch {
      val result = withContext(NonCancellable) {
        routeMutex.withLock { withContext(Dispatchers.IO) { routeManager.update(change) } }
      }
      if (isFinishing || isDestroyed) return@launch
      onResult(result)
    }
  }

  companion object {
    const val EXTRA_LIST_ID = "LIST_ID"
    const val EXTRA_ADDED_COUNT = "ADDED_COUNT"
    const val EXTRA_SKIPPED_COUNT = "SKIPPED_COUNT"
    const val EXTRA_MEMORY_FAILED = "MEMORY_FAILED"
  }
}
