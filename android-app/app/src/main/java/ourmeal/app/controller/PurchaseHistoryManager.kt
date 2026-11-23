package ourmeal.app.controller

import android.content.Context
import ourmeal.app.model.PurchaseHistory

/** `purchase_history.json`: every trip to the shop, kept after its list is deleted. */
class PurchaseHistoryManager(context: Context) {
  private val document = JsonDocument(
    context, "purchase_history.json", PurchaseHistory::class.java, ::PurchaseHistory, PurchaseHistory::sanitized
  )

  /** Empty history when the file does not exist; IOException when it cannot be read. */
  fun load(): PurchaseHistory = document.load()

  /** Read-modify-write under the file lock. Null when reading or writing fails. */
  fun update(change: (PurchaseHistory) -> PurchaseHistory): PurchaseHistory? = document.update(change)
}
