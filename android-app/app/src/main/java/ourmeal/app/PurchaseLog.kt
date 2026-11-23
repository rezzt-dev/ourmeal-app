package ourmeal.app

import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ourmeal.app.controller.PurchaseHistoryManager
import ourmeal.app.model.PurchaseHistory
import ourmeal.app.model.PurchasedItem

/**
 * Writes what the user marks in one list to the purchase history. Writes run off the main
 * thread in call order and finish even if the screen goes away meanwhile. A history that cannot
 * be read is left untouched and the list keeps working without it.
 */
internal class PurchaseLog(
  private val scope: LifecycleCoroutineScope,
  private val manager: PurchaseHistoryManager
) {
  private val mutex = Mutex()

  /** A product marked as bought joins the trip of its list; one put back leaves it. */
  fun marked(listId: Long, listName: String, item: PurchasedItem, bought: Boolean) = write { history ->
    if (bought) history.mark(listId, listName, item) else history.unmark(listId, item.itemId, item.at)
  }

  /** A price written in the list is what the product cost, if it was bought on the trip going on. */
  fun priced(listId: Long, itemId: Long, cents: Long?) = write { it.withPrice(listId, itemId, cents) }

  private fun write(change: (PurchaseHistory) -> PurchaseHistory) {
    scope.launch {
      withContext(NonCancellable) {
        mutex.withLock { withContext(Dispatchers.IO) { manager.update(change) } }
      }
    }
  }
}
