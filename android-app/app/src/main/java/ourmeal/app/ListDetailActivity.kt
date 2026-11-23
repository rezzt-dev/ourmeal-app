package ourmeal.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.ourmeat.R
import app.ourmeat.databinding.ActivityListDetailBinding
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
import ourmeal.app.controller.MergeOutcome
import ourmeal.app.controller.ProductPriceManager
import ourmeal.app.controller.PurchaseHistoryManager
import ourmeal.app.controller.ShoppingRouteManager
import ourmeal.app.model.ListItem
import ourmeal.app.model.PriceEstimate
import ourmeal.app.model.PriceInput
import ourmeal.app.model.PurchasedItem
import ourmeal.app.model.RouteChange
import ourmeal.app.model.RouteStop
import ourmeal.app.model.ShoppingRoute
import ourmeal.app.model.UserList
import ourmeal.app.model.allPending
import ourmeal.app.model.copyName
import ourmeal.app.model.deepCopy
import ourmeal.app.model.estimate
import ourmeal.app.model.formatPrice
import ourmeal.app.model.hasOtherItemNamed
import ourmeal.app.model.inRouteOrder
import ourmeal.app.model.itemName
import ourmeal.app.model.matchKey
import ourmeal.app.model.routePlacement
import ourmeal.app.model.shownPrice
import ourmeal.app.model.withItemName
import ourmeal.app.model.withItemPrice
import ourmeal.app.model.withMarks
import ourmeal.app.model.withRestored
import ourmeal.app.model.withoutBought
import java.io.IOException

class ListDetailActivity : AppCompatActivity() {
  private lateinit var binding: ActivityListDetailBinding
  private lateinit var dataManager: DataManager
  private lateinit var routeManager: ShoppingRouteManager
  private lateinit var priceManager: ProductPriceManager
  private lateinit var purchases: PurchaseLog
  private lateinit var adapter: ItemAdapter
  private var currentList: UserList? = null
  private var listId = -1L
  private var loadJob: Job? = null
  private lateinit var creationForm: CreationFormState
  private var isSaving = false
  private var pendingUndo: (() -> Unit)? = null
  /** A result from the menu is announced once, not again after recreation. */
  private var showMenuResult = false
  /** A list just duplicated, or made again from a past trip, offers to undo it, once. */
  private var showDuplicated = false
  private var showRepeated = false
  /** Remembered prices by product key; null while their file cannot be read. */
  private var rememberedPrices: Map<String, Long>? = null
  private val priceMutex = Mutex()
  /** Null while the route file cannot be read; the list then keeps its stored order. */
  private var route: ShoppingRoute? = null
  private val routeMutex = Mutex()
  private var isBindingRoute = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    creationForm = CreationFormState(this)
    showMenuResult = savedInstanceState == null && intent.hasExtra(EXTRA_ADDED_COUNT)
    showDuplicated = savedInstanceState == null && intent.getBooleanExtra(EXTRA_DUPLICATED, false)
    showRepeated = savedInstanceState == null && intent.getBooleanExtra(EXTRA_REPEATED, false)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )
    binding = ActivityListDetailBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.root.applyScreenInsets()
    dataManager = DataManager(this)
    routeManager = ShoppingRouteManager(this)
    priceManager = ProductPriceManager(this)
    purchases = PurchaseLog(lifecycleScope, PurchaseHistoryManager(this))
    listId = intent.getLongExtra(EXTRA_LIST_ID, -1L)

    adapter = ItemAdapter(
      onCheck = ::setCompleted,
      onRename = ::showRenameItemDialog,
      onPrice = ::showPriceDialog,
      onDelete = ::confirmDelete
    )
    binding.recyclerViewItems.layoutManager = LinearLayoutManager(this)
    binding.recyclerViewItems.adapter = adapter
    // A list made again from a past trip goes back to the history it was opened from.
    if (intent.getBooleanExtra(EXTRA_FROM_PURCHASES, false)) binding.btnBack.setText(R.string.purchases_open)
    binding.btnBack.setOnClickListener { finish() }
    binding.btnAddItem.setOnClickListener { showAddDialog() }
    binding.btnShare.setOnClickListener { showShareOptions(it) }
    binding.btnListActions.setOnClickListener { showListActions(it) }
    binding.routeOrder.switchRouteOrder.setOnCheckedChangeListener { _, checked -> setSortByRoute(checked) }
    binding.routeOrder.btnForgetRoute.setOnClickListener { confirmForgetRoute() }
    loadList()
  }

  private fun loadList() {
    loadJob?.cancel()
    currentList = null
    renderLoading()
    loadJob = lifecycleScope.launch {
      val loaded = try {
        withContext(Dispatchers.IO) {
          LoadedList(
            list = dataManager.getListById(listId),
            // A trip that ended since the last visit is learned now, before sorting the list.
            route = routeManager.update { it.settle(System.currentTimeMillis()) },
            // Without remembered prices the list still shows, and lets the user write, its own.
            prices = try { priceManager.load().byKey() } catch (_: IOException) { null }
          )
        }
      } catch (_: IOException) {
        null
      }
      binding.loadingPanel.root.isVisible = false
      if (loaded == null) {
        showErrorState()
        return@launch
      }
      val list = loaded.list
      if (list == null) {
        showMissingState()
        return@launch
      }
      route = loaded.route?.route
      rememberedPrices = loaded.prices
      adapter.setRememberedPrices(loaded.prices.orEmpty())
      bindList(list)
      if (!announceMenuResult() && !announceDuplicated()) announceLearnedRoute(loaded.route?.learned ?: 0)
      creationForm.restore { kind, target ->
        when (kind) {
          FormKind.CREATE -> showAddDialog()
          FormKind.RENAME_ITEM -> currentList?.items?.firstOrNull { it.id == target }?.let(::showRenameItemDialog)
          FormKind.RENAME_LIST -> showRenameListDialog()
          FormKind.DUPLICATE_LIST -> showDuplicateDialog()
          FormKind.PRICE_ITEM -> currentList?.items?.firstOrNull { it.id == target }?.let(::showPriceDialog)
        }
      }
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
    // An empty list is explained by its empty state; the counter and progress card would repeat it.
    binding.tvItemCount.text =
      if (total == 0) "" else resources.getQuantityString(R.plurals.item_count, total, total)
    binding.progressCard.isVisible = total > 0
    binding.tvProgress.text =
      if (done == total) getString(R.string.progress_done)
      else getString(R.string.progress_partial, done, total)
    binding.progressCompletion.setProgressWithMotion(percentage)
    binding.progressCompletion.contentDescription =
      "${getString(R.string.progress_completion)}: $percentage%"
    binding.btnShare.isEnabled = total > 0 && !isSaving
    renderEstimate(list)
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
    if (isSaving || creationForm.isShowing || currentList == null) return
    openNameForm(
      state = creationForm,
      kind = FormKind.CREATE,
      nameKind = NameKind.PRODUCT,
      spec = NameFormSpec(
        title = R.string.add_item_title,
        message = getString(R.string.add_item_message),
        confirm = R.string.add,
        busy = R.string.adding
      ),
      onSubmit = ::addItem
    )
  }

  private fun addItem(form: NameForm) {
    if (isSaving) return
    val list = currentList ?: return
    val name = form.name
    if (name.isEmpty()) {
      form.showError(getString(R.string.item_name_required))
      return
    }
    val key = matchKey(name)
    val duplicate = displayedItems(list).indexOfFirst { matchKey(it.name) == key }
    if (duplicate >= 0) {
      form.showError(getString(R.string.item_exists))
      binding.recyclerViewItems.scrollToPosition(duplicate)
      return
    }
    form.clearError()
    form.setBusy(true)

    val item = ListItem(nextItemId(list), name, false, System.currentTimeMillis())
    list.items.add(0, item)
    setSaving(true)
    val snapshot = list.snapshot()
    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) { dataManager.updateList(snapshot) }
      if (isFinishing || isDestroyed) return@launch
      setSaving(false)
      form.setBusy(false)
      if (!saved) {
        list.items.remove(item)
        showFormError(form, getString(R.string.item_save_error))
        return@launch
      }
      form.dialog.dismiss()
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

  /** An error the open form can show stays in it; otherwise it becomes a snackbar. */
  private fun showFormError(form: OpenForm, message: String) {
    if (form.dialog.isShowing) form.showError(message) else showMessage(message)
  }

  private fun showMessage(message: String) {
    binding.rootDetail.showSnackbar(message = message, anchor = binding.actionBar)
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
        val price = item.shownPrice(rememberedPrices.orEmpty())?.cents
        purchases.marked(list.id, list.name, PurchasedItem(item.id, item.name, price, markedAt), bought = completed)
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

  /** Prices are only shared when the user chooses so; a list without any shares at once. */
  private fun showShareOptions(anchor: View) {
    val list = currentList ?: return
    if (list.items.isEmpty() || isSaving) return
    if (!list.items.estimate(rememberedPrices.orEmpty()).hasPrices) return shareList(withPrices = false)
    PopupMenu(this, anchor).apply {
      menu.add(Menu.NONE, SHARE_PLAIN, Menu.NONE, R.string.share_list_plain)
      menu.add(Menu.NONE, SHARE_WITH_PRICES, Menu.NONE, R.string.share_list_with_prices)
      setOnMenuItemClickListener { item ->
        shareList(withPrices = item.itemId == SHARE_WITH_PRICES)
        true
      }
      show()
    }
  }

  private fun shareList(withPrices: Boolean) {
    val list = currentList ?: return
    if (list.items.isEmpty()) return
    val remembered = rememberedPrices.orEmpty()
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
      if (withPrices) appendLine(estimateText(list.items.estimate(remembered)))
      appendLine()
      // Whoever receives the list gets it in the order shown, route included.
      displayedItems(list).forEach { item ->
        val price = item.shownPrice(remembered)?.takeIf { withPrices }?.let { formatPrice(it.cents) }
        val name = if (price == null) item.name else getString(R.string.price_share_line, item.name, price)
        appendLine("${if (item.isCompleted) "[x]" else "[ ]"} $name")
      }
    }
    if (!startShareChooser(textShareIntent(list.name, text), getString(R.string.share_chooser))) {
      showMessage(getString(R.string.share_error))
    }
  }

  private fun setSaving(saving: Boolean) {
    isSaving = saving
    adapter.setInteractionsEnabled(!saving)
    binding.btnAddItem.isEnabled = !saving
    binding.btnShare.isEnabled = !saving && (currentList?.items?.isNotEmpty() == true)
    binding.btnListActions.isEnabled = !saving
    if (!saving) {
      pendingUndo?.let { undo ->
        pendingUndo = null
        binding.root.post(undo)
      }
    }
  }

  private fun UserList.snapshot(): UserList = deepCopy()

  /**
   * Shows [updated] at once and writes it; on failure the previous list comes back and
   * [onResult] receives false so the caller can say what was not saved.
   */
  private fun saveListChange(updated: UserList, onResult: (Boolean) -> Unit) {
    val previous = currentList ?: return
    setSaving(true)
    showList(updated)
    val snapshot = updated.snapshot()
    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) { dataManager.updateList(snapshot) }
      if (isFinishing || isDestroyed) return@launch
      if (!saved) showList(previous)
      setSaving(false)
      onResult(saved)
    }
  }

  private fun showList(list: UserList) {
    currentList = list
    binding.tvListTitle.text = list.name
    submitKeepingScroll(list)
    updateSummary()
  }

  // EDITAR NOMBRES Y REUTILIZAR LA LISTA

  /** Each action is disabled when it would do nothing; the list can always be renamed. */
  private fun showListActions(anchor: View) {
    val list = currentList ?: return
    if (isSaving) return
    val hasBought = list.getCompletedCount() > 0
    PopupMenu(this, anchor).apply {
      menu.add(Menu.NONE, ACTION_RENAME, Menu.NONE, R.string.rename_action)
      menu.add(Menu.NONE, ACTION_UNCHECK_ALL, Menu.NONE, R.string.list_uncheck_all).isEnabled = hasBought
      menu.add(Menu.NONE, ACTION_REMOVE_BOUGHT, Menu.NONE, R.string.list_remove_bought).isEnabled = hasBought
      menu.add(Menu.NONE, ACTION_DUPLICATE, Menu.NONE, R.string.list_duplicate).isEnabled = list.items.isNotEmpty()
      setOnMenuItemClickListener { item ->
        when (item.itemId) {
          ACTION_RENAME -> showRenameListDialog()
          ACTION_UNCHECK_ALL -> uncheckAll()
          ACTION_REMOVE_BOUGHT -> confirmRemoveBought()
          ACTION_DUPLICATE -> showDuplicateDialog()
        }
        true
      }
      show()
    }
  }

  private fun showRenameListDialog() {
    val list = currentList ?: return
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
      ),
      onSubmit = ::renameList
    )
  }

  private fun renameList(form: NameForm) {
    if (isSaving) return
    val list = currentList ?: return form.dialog.dismiss()
    val name = itemName(form.name)
    if (name.isEmpty()) {
      form.showError(getString(R.string.list_name_required))
      return
    }
    if (name == list.name) {
      form.dialog.dismiss()
      return
    }
    val previous = list.name
    form.clearError()
    form.setBusy(true)
    saveListChange(list.deepCopy().copy(name = name)) { saved ->
      form.setBusy(false)
      if (!saved) {
        showFormError(form, getString(R.string.rename_error))
        return@saveListChange
      }
      form.dialog.dismiss()
      offerUndo(getString(R.string.list_renamed)) {
        val current = currentList ?: return@offerUndo null
        if (current.name != name) null else current.deepCopy().copy(name = previous)
      }
    }
  }

  private fun showRenameItemDialog(adapterItem: ListItem) {
    val item = currentList?.items?.firstOrNull { it.id == adapterItem.id } ?: return
    if (isSaving || creationForm.isShowing) return
    openNameForm(
      state = creationForm,
      kind = FormKind.RENAME_ITEM,
      nameKind = NameKind.PRODUCT,
      target = item.id,
      spec = NameFormSpec(
        title = R.string.rename_item_title,
        message = getString(R.string.rename_item_message),
        confirm = R.string.save,
        busy = R.string.saving,
        initial = item.name
      )
    ) { form -> renameItem(item.id, form) }
  }

  /**
   * The learned route keys products by name, so a renamed product is placed again once it is
   * marked under its new name; its old place is dropped with time by the route's stop limit.
   */
  private fun renameItem(itemId: Long, form: NameForm) {
    if (isSaving) return
    val list = currentList ?: return form.dialog.dismiss()
    val item = list.items.firstOrNull { it.id == itemId } ?: return form.dialog.dismiss()
    val name = itemName(form.name)
    if (name.isEmpty()) {
      form.showError(getString(R.string.item_name_required))
      return
    }
    if (name == item.name) {
      form.dialog.dismiss()
      return
    }
    if (list.hasOtherItemNamed(name, exceptId = itemId)) {
      form.showError(getString(R.string.item_exists))
      return
    }
    val updated = list.withItemName(itemId, name, System.currentTimeMillis()) ?: return form.dialog.dismiss()
    val previous = item.name
    form.clearError()
    form.setBusy(true)
    saveListChange(updated) { saved ->
      form.setBusy(false)
      if (!saved) {
        showFormError(form, getString(R.string.rename_error))
        return@saveListChange
      }
      form.dialog.dismiss()
      offerUndo(getString(R.string.item_renamed)) {
        val current = currentList ?: return@offerUndo null
        val renamed = current.items.firstOrNull { it.id == itemId } ?: return@offerUndo null
        if (renamed.name != name || current.hasOtherItemNamed(previous, exceptId = itemId)) null
        else current.withItemName(itemId, previous, System.currentTimeMillis())
      }
    }
  }

  /** Not a walk through the shop: nothing is written to the learned route. */
  private fun uncheckAll() {
    val list = currentList ?: return
    if (isSaving) return
    val result = list.allPending(System.currentTimeMillis())
    if (result.previous.isEmpty()) return
    saveListChange(result.list) { saved ->
      if (!saved) {
        showMessage(getString(R.string.list_bulk_error))
        return@saveListChange
      }
      val count = result.previous.size
      offerUndo(resources.getQuantityString(R.plurals.list_unchecked, count, count)) {
        currentList?.withMarks(result.previous)
      }
    }
  }

  private fun confirmRemoveBought() {
    val list = currentList ?: return
    val bought = list.getCompletedCount()
    if (bought == 0 || isSaving) return
    ourmealDialog(destructive = true)
      .setTitle(R.string.list_remove_bought_title)
      .setMessage(resources.getQuantityString(R.plurals.list_remove_bought_message, bought, bought, list.name))
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.list_remove_bought_confirm) { _, _ -> removeBought() }
      .show()
  }

  private fun removeBought() {
    val list = currentList ?: return
    if (isSaving) return
    val result = list.withoutBought()
    if (result.removed.isEmpty()) return
    saveListChange(result.list) { saved ->
      if (!saved) {
        showMessage(getString(R.string.list_bulk_error))
        return@saveListChange
      }
      val count = result.removed.size
      offerUndo(resources.getQuantityString(R.plurals.list_bought_removed, count, count)) {
        currentList?.withRestored(result.removed)
      }
    }
  }

  /**
   * Offers to undo the last change. [restore] builds the list to write from the current one, or
   * returns null when the change no longer applies; an undo tapped during a write runs after it.
   */
  private fun offerUndo(message: String, restore: () -> UserList?) {
    binding.rootDetail.showSnackbar(
      message = message,
      actionLabel = getString(R.string.undo),
      anchor = binding.actionBar,
      onAction = { undoChange(restore) }
    )
  }

  private fun undoChange(restore: () -> UserList?) {
    if (isSaving) {
      pendingUndo = { undoChange(restore) }
      return
    }
    val restored = restore() ?: return
    saveListChange(restored) { saved ->
      if (!saved) showMessage(getString(R.string.item_save_error))
    }
  }

  private fun showDuplicateDialog() {
    val list = currentList ?: return
    if (isSaving || creationForm.isShowing) return
    openNameForm(
      state = creationForm,
      kind = FormKind.DUPLICATE_LIST,
      nameKind = NameKind.LIST,
      spec = NameFormSpec(
        title = R.string.duplicate_list_title,
        message = getString(R.string.duplicate_list_message),
        confirm = R.string.duplicate_list_confirm,
        busy = R.string.duplicating,
        initial = copyName(list.name, getString(R.string.list_copy_suffix))
      ),
      onSubmit = ::duplicateList
    )
  }

  /** The copy keeps the order of the products, all pending, and opens in place of this list. */
  private fun duplicateList(form: NameForm) {
    if (isSaving) return
    val list = currentList ?: return form.dialog.dismiss()
    val name = itemName(form.name)
    if (name.isEmpty()) {
      form.showError(getString(R.string.list_name_required))
      return
    }
    form.clearError()
    form.setBusy(true)
    setSaving(true)
    val names = list.items.map { it.name }
    lifecycleScope.launch {
      val outcome = withContext(Dispatchers.IO) { dataManager.createListWithItems(name, names) }
      if (isFinishing || isDestroyed) return@launch
      setSaving(false)
      form.setBusy(false)
      if (outcome !is MergeOutcome.Saved) {
        showFormError(form, getString(R.string.list_duplicate_error))
        return@launch
      }
      form.dialog.dismiss()
      startActivity(
        Intent(this@ListDetailActivity, ListDetailActivity::class.java)
          .putExtra(EXTRA_LIST_ID, outcome.listId)
          .putExtra(EXTRA_DUPLICATED, true)
      )
      finish()
    }
  }

  /**
   * True when a duplicate or a repeated trip was announced. Undo deletes the new list and goes
   * back to the screen it came from.
   */
  private fun announceDuplicated(): Boolean {
    if (!showDuplicated && !showRepeated) return false
    val message = if (showRepeated) R.string.purchase_repeated else R.string.list_duplicated
    showDuplicated = false
    showRepeated = false
    intent.removeExtra(EXTRA_DUPLICATED)
    intent.removeExtra(EXTRA_REPEATED)
    binding.rootDetail.showSnackbar(
      message = getString(message),
      actionLabel = getString(R.string.undo),
      anchor = binding.actionBar,
      onAction = ::undoDuplicate
    )
    return true
  }

  private fun undoDuplicate() {
    if (isSaving) {
      pendingUndo = ::undoDuplicate
      return
    }
    setSaving(true)
    lifecycleScope.launch {
      val deleted = withContext(Dispatchers.IO) { dataManager.deleteList(listId) }
      if (isFinishing || isDestroyed) return@launch
      setSaving(false)
      if (deleted) finish() else showMessage(getString(R.string.list_delete_error))
    }
  }

  // PRECIOS ANOTADOS

  private fun showPriceDialog(adapterItem: ListItem) {
    val item = currentList?.items?.firstOrNull { it.id == adapterItem.id } ?: return
    if (isSaving || creationForm.isShowing) return
    openPriceForm(
      state = creationForm,
      target = item.id,
      itemName = item.name,
      current = item.shownPrice(rememberedPrices.orEmpty())
    ) { form, input -> savePrice(item.id, form, input) }
  }

  /** An empty price removes the one written in the list and stops offering the remembered one. */
  private fun savePrice(itemId: Long, form: PriceForm, input: PriceInput) {
    if (isSaving) return
    val list = currentList ?: return form.dialog.dismiss()
    val item = list.items.firstOrNull { it.id == itemId } ?: return form.dialog.dismiss()
    val cents = when (input) {
      is PriceInput.Valid -> input.cents
      PriceInput.Empty -> null
      PriceInput.Zero -> return form.showError(getString(R.string.price_zero))
      PriceInput.TooLarge -> return form.showError(getString(R.string.price_too_large))
      PriceInput.Invalid -> return form.showError(getString(R.string.price_invalid))
    }
    if (cents == null && item.shownPrice(rememberedPrices.orEmpty()) == null) return form.dialog.dismiss()
    val updated = list.withItemPrice(itemId, cents, System.currentTimeMillis()) ?: return form.dialog.dismiss()
    form.clearError()
    form.setBusy(true)
    saveListChange(updated) { saved ->
      form.setBusy(false)
      if (!saved) {
        showFormError(form, getString(R.string.price_save_error))
        return@saveListChange
      }
      form.dialog.dismiss()
      purchases.priced(list.id, itemId, cents)
      rememberPrice(item.name, cents)
    }
  }

  /** Remembered for the next list, in call order; a memory that cannot be read is left untouched. */
  private fun rememberPrice(name: String, cents: Long?) {
    val now = System.currentTimeMillis()
    lifecycleScope.launch {
      val stored = withContext(NonCancellable) {
        priceMutex.withLock {
          withContext(Dispatchers.IO) {
            priceManager.update { if (cents == null) it.forget(name) else it.remember(name, cents, now) }
          }
        }
      }
      if (isFinishing || isDestroyed) return@launch
      if (stored != null) {
        rememberedPrices = stored.byKey()
        adapter.setRememberedPrices(stored.byKey())
        updateSummary()
      }
      showMessage(
        getString(
          when {
            stored == null -> R.string.price_memory_failed
            cents == null -> R.string.price_removed
            else -> R.string.price_saved
          }
        )
      )
    }
  }

  /** The total counts the price written in the list or else the remembered one. */
  private fun renderEstimate(list: UserList) {
    val estimate = list.items.estimate(rememberedPrices.orEmpty())
    binding.tvEstimate.isVisible = estimate.hasPrices
    if (!estimate.hasPrices) return
    val total = formatPrice(estimate.totalCents)
    binding.tvEstimate.text = estimateText(estimate)
    binding.tvEstimate.contentDescription = if (estimate.unpriced == 0) {
      getString(R.string.price_estimate_description_all, total)
    } else {
      resources.getQuantityString(R.plurals.price_estimate_description, estimate.unpriced, total, estimate.unpriced)
    }
  }

  private fun estimateText(estimate: PriceEstimate): String {
    val total = formatPrice(estimate.totalCents)
    return if (estimate.unpriced == 0) getString(R.string.price_estimate, total)
    else resources.getQuantityString(R.plurals.price_estimate_unpriced, estimate.unpriced, total, estimate.unpriced)
  }

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

  private class LoadedList(val list: UserList?, val route: RouteChange?, val prices: Map<String, Long>?)

  companion object {
    const val EXTRA_LIST_ID = "LIST_ID"
    const val EXTRA_ADDED_COUNT = "ADDED_COUNT"
    const val EXTRA_SKIPPED_COUNT = "SKIPPED_COUNT"
    const val EXTRA_MEMORY_FAILED = "MEMORY_FAILED"
    const val EXTRA_DUPLICATED = "DUPLICATED"
    const val EXTRA_REPEATED = "REPEATED"
    const val EXTRA_FROM_PURCHASES = "FROM_PURCHASES"
    private const val ACTION_RENAME = 1
    private const val ACTION_UNCHECK_ALL = 2
    private const val ACTION_REMOVE_BOUGHT = 3
    private const val ACTION_DUPLICATE = 4
    private const val SHARE_PLAIN = 1
    private const val SHARE_WITH_PRICES = 2
  }
}
