package ourmeal.app.model

/** What the price line under one pending product says. */
sealed interface ItemPriceState {
  /** Asking the service for the first time. */
  data object Loading : ItemPriceState

  /**
   * Prices of the chosen supermarkets; [PriceComparison.best] is null when none sells it.
   * [refreshing] keeps the saved prices on screen while newer ones arrive.
   */
  data class Compared(
    val target: PriceTarget,
    val comparison: PriceComparison,
    val fetchedAt: Long,
    val refreshing: Boolean = false
  ) : ItemPriceState

  /** Several products match: the user picks one. */
  data class NeedsChoice(val query: String, val options: List<PriceCandidate>) : ItemPriceState

  /** The service did not understand the name. */
  data class NotFound(val query: String, val options: List<PriceCandidate>) : ItemPriceState

  /** The user preferred not to compare this product. */
  data object Skipped : ItemPriceState

  /** No connection and no saved prices. */
  data object Offline : ItemPriceState

  /** The service failed or answered something unusable, and no saved prices exist. */
  data object Failed : ItemPriceState
}
