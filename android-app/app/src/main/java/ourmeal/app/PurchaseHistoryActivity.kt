package ourmeal.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.ourmeat.R
import app.ourmeat.databinding.ActivityPurchaseHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ourmeal.app.controller.DataManager
import ourmeal.app.controller.MergeOutcome
import ourmeal.app.controller.PurchaseHistoryManager
import ourmeal.app.controller.PurchaseText
import ourmeal.app.controller.PurchaseTripAdapter
import ourmeal.app.controller.TripRow
import ourmeal.app.model.PurchaseHistory
import ourmeal.app.model.PurchaseTrip
import ourmeal.app.model.repeatListName
import java.io.IOException
import java.time.LocalDate

/**
 * "Mis compras": every trip to the shop, newest first, with its products and what was spent.
 * A trip can be made again as a new list with all of its products pending.
 */
class PurchaseHistoryActivity : AppCompatActivity() {
  private lateinit var binding: ActivityPurchaseHistoryBinding
  private lateinit var historyManager: PurchaseHistoryManager
  private lateinit var dataManager: DataManager
  private lateinit var adapter: PurchaseTripAdapter
  private lateinit var text: PurchaseText
  private var history: PurchaseHistory? = null
  /** Ids of the lists that still exist; null when the lists cannot be read. */
  private var listIds: Set<Long>? = null
  private val expanded = mutableSetOf<Long>()
  private var loadJob: Job? = null
  private var firstResume = true
  private var isSaving = false
  private var pendingUndo: (() -> Unit)? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )
    binding = ActivityPurchaseHistoryBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.root.applyScreenInsets()
    historyManager = PurchaseHistoryManager(this)
    dataManager = DataManager(this)
    text = PurchaseText(this)
    savedInstanceState?.getLongArray(STATE_EXPANDED)?.let { expanded.addAll(it.toList()) }

    adapter = PurchaseTripAdapter(onRepeat = ::repeatTrip, onDelete = ::confirmDelete, onToggle = ::toggleTrip)
    binding.recyclerViewPurchases.layoutManager = LinearLayoutManager(this)
    binding.recyclerViewPurchases.adapter = adapter
    binding.btnBackToLists.setOnClickListener { finish() }
    loadHistory()
  }

  /** Coming back from a list, a trip may have grown or finished meanwhile. */
  override fun onResume() {
    super.onResume()
    if (firstResume) firstResume = false else if (!isSaving) loadHistory(showLoading = false)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putLongArray(STATE_EXPANDED, expanded.toLongArray())
    super.onSaveInstanceState(outState)
  }

  private fun loadHistory(showLoading: Boolean = true) {
    loadJob?.cancel()
    if (showLoading) {
      binding.recyclerViewPurchases.isVisible = false
      binding.statePanel.root.isVisible = false
      binding.loadingPanel.root.isVisible = true
      binding.loadingPanel.bindLoading(getString(R.string.purchases_loading))
      binding.tvPurchaseCount.text = ""
    }
    loadJob = lifecycleScope.launch {
      val loaded = withContext(Dispatchers.IO) { readHistory() }
      binding.loadingPanel.root.isVisible = false
      if (loaded == null) {
        showErrorState()
        return@launch
      }
      history = loaded.first
      listIds = loaded.second
      render()
    }
  }

  /**
   * Trips that ended since the last visit are closed before showing them. If closing them cannot
   * be written, they are still shown closed; the file is settled again on the next write.
   */
  private fun readHistory(): Pair<PurchaseHistory, Set<Long>?>? {
    val now = System.currentTimeMillis()
    val stored = try { historyManager.load() } catch (_: IOException) { return null }
    val settled = stored.settle(now)
    if (settled != stored) historyManager.update { it.settle(now) }
    val ids = try { dataManager.loadLists().mapTo(HashSet()) { it.id } } catch (_: IOException) { null }
    return settled to ids
  }

  private fun render() {
    val history = history ?: return
    val trips = history.timeline()
    expanded.retainAll(trips.mapTo(HashSet()) { it.id })
    adapter.submitList(trips.map { trip ->
      TripRow(
        trip = trip,
        inProgress = history.isOpen(trip.id),
        listDeleted = listIds?.let { trip.listId !in it } ?: false,
        expanded = trip.id in expanded
      )
    })
    binding.tvPurchaseCount.text =
      if (trips.isEmpty()) "" else resources.getQuantityString(R.plurals.purchases_count, trips.size, trips.size)
    if (trips.isEmpty()) {
      showEmptyState()
    } else {
      binding.statePanel.root.isVisible = false
      binding.recyclerViewPurchases.reveal()
    }
  }

  private fun showEmptyState() {
    binding.recyclerViewPurchases.isVisible = false
    binding.statePanel.root.reveal()
    binding.statePanel.bindState(
      title = getString(R.string.purchases_empty_title),
      body = getString(R.string.purchases_empty_body),
      icon = R.drawable.ic_basket,
      actionLabel = getString(R.string.back_to_lists_action),
      onAction = ::finish
    )
  }

  private fun showErrorState() {
    history = null
    adapter.submitList(emptyList())
    binding.recyclerViewPurchases.isVisible = false
    binding.tvPurchaseCount.text = ""
    binding.statePanel.root.reveal()
    binding.statePanel.bindState(
      title = getString(R.string.purchases_error_title),
      body = getString(R.string.purchases_error_body),
      icon = R.drawable.ic_alert,
      actionLabel = getString(R.string.retry),
      onAction = { loadHistory() }
    )
  }

  private fun toggleTrip(trip: PurchaseTrip) {
    if (!expanded.add(trip.id)) expanded.remove(trip.id)
    render()
  }

  /** One tap: a new list with every product of the trip, pending, opened with the option to undo. */
  private fun repeatTrip(trip: PurchaseTrip) {
    if (isSaving) return
    setSaving(true)
    val names = trip.items.map { it.name }
    val dateLabel = text.dateLabel(LocalDate.now())
    val listName = text.listName(trip)
    lifecycleScope.launch {
      val outcome = withContext(Dispatchers.IO) {
        val taken = try { dataManager.loadLists().map { it.name } } catch (_: IOException) { null }
        if (taken == null) MergeOutcome.Failed
        else dataManager.createListWithItems(repeatListName(listName, taken, dateLabel), names)
      }
      if (isFinishing || isDestroyed) return@launch
      setSaving(false)
      if (outcome !is MergeOutcome.Saved) {
        binding.rootPurchases.showSnackbar(message = getString(R.string.purchase_repeat_error))
        return@launch
      }
      startActivity(
        Intent(this@PurchaseHistoryActivity, ListDetailActivity::class.java)
          .putExtra(ListDetailActivity.EXTRA_LIST_ID, outcome.listId)
          .putExtra(ListDetailActivity.EXTRA_REPEATED, true)
          .putExtra(ListDetailActivity.EXTRA_FROM_PURCHASES, true)
      )
    }
  }

  private fun confirmDelete(trip: PurchaseTrip) {
    if (isSaving) return
    ourmealDialog(destructive = true)
      .setTitle(R.string.purchase_delete_title)
      .setMessage(getString(R.string.purchase_delete_message, text.listName(trip), text.day(trip.startedAt)))
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.purchase_delete_confirm) { _, _ -> deleteTrip(trip) }
      .show()
  }

  private fun deleteTrip(trip: PurchaseTrip) {
    writeHistory({ it.without(trip.id) }) { saved ->
      if (!saved) {
        binding.rootPurchases.showSnackbar(message = getString(R.string.purchase_delete_error))
        return@writeHistory
      }
      binding.rootPurchases.showSnackbar(
        message = getString(R.string.purchase_deleted),
        actionLabel = getString(R.string.undo),
        onAction = { restoreTrip(trip) }
      )
    }
  }

  private fun restoreTrip(trip: PurchaseTrip) {
    if (isSaving) {
      pendingUndo = { restoreTrip(trip) }
      return
    }
    writeHistory({ it.withRestored(trip) }) { saved ->
      if (!saved) binding.rootPurchases.showSnackbar(message = getString(R.string.item_save_error))
    }
  }

  /** Changes the stored history and shows the result; [onResult] receives false when nothing was written. */
  private fun writeHistory(change: (PurchaseHistory) -> PurchaseHistory, onResult: (Boolean) -> Unit) {
    if (isSaving) return
    setSaving(true)
    lifecycleScope.launch {
      val now = System.currentTimeMillis()
      val stored = withContext(Dispatchers.IO) { historyManager.update { change(it.settle(now)) } }
      if (isFinishing || isDestroyed) return@launch
      setSaving(false)
      if (stored != null) {
        history = stored
        render()
      }
      onResult(stored != null)
    }
  }

  private fun setSaving(saving: Boolean) {
    isSaving = saving
    adapter.setInteractionsEnabled(!saving)
    if (!saving) {
      pendingUndo?.let { undo ->
        pendingUndo = null
        binding.root.post(undo)
      }
    }
  }

  private companion object {
    const val STATE_EXPANDED = "expanded_trips"
  }
}
