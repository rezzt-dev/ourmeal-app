package ourmeal.app

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import app.ourmeat.R
import app.ourmeat.databinding.DialogPriceDetailBinding
import app.ourmeat.databinding.ItemPriceOfferBinding
import ourmeal.app.model.ItemPriceState
import ourmeal.app.model.PriceCandidate
import ourmeal.app.model.PriceSettings
import ourmeal.app.model.formatEuros
import ourmeal.app.model.formatUnitPrice
import kotlin.math.roundToInt

/**
 * Every supermarket chosen, cheapest per unit first, with the pack price, date and origin of
 * each price, and a warning when a cheaper-looking pack is dearer per unit.
 */
internal fun AppCompatActivity.showPriceDetail(
  state: ItemPriceState.Compared,
  settings: PriceSettings,
  onChangeProduct: () -> Unit
) {
  val builder = ourmealDialog()
  val view = DialogPriceDetailBinding.inflate(LayoutInflater.from(builder.context))
  val comparison = state.comparison
  val best = comparison.best
  val kind = best?.unitKind ?: comparison.offers.firstOrNull()?.unitKind
  val city = settings.city?.name.orEmpty()

  view.tvPriceSubtitle.text = if (kind != null) {
    getString(R.string.prices_detail_subtitle, unitWord(kind), city)
  } else {
    getString(R.string.prices_detail_none)
  }

  val saving = comparison.savingOverNext
  val rival = best?.let { winner ->
    comparison.offers.firstOrNull { it.chainId != winner.chainId && it.unitPrice > winner.unitPrice }
  }
  val percent = saving?.let { (it * 100).roundToInt() }
  if (best != null && rival != null && percent != null && percent >= 1) {
    view.tvPriceSaving.text = getString(
      R.string.prices_detail_saving, percent, unitWord(best.unitKind), settings.chainName(rival.chainId).orEmpty()
    )
    view.tvPriceSaving.isVisible = true
  }
  comparison.cheaperButOld?.let { old ->
    view.tvPriceOld.text = getString(
      R.string.prices_detail_old_cheaper,
      settings.chainName(old.chainId).orEmpty(),
      formatUnitPrice(old.unitPrice, old.unitKind),
      formatPriceDate(old.observedAt)
    )
    view.tvPriceOld.isVisible = true
  }
  comparison.cheaperPackElsewhere?.let { trap ->
    view.tvPriceTrap.text = getString(
      R.string.prices_detail_trap,
      settings.chainName(trap.chainId).orEmpty(),
      formatEuros(trap.price),
      formatUnitPrice(trap.unitPrice, trap.unitKind)
    )
    view.tvPriceTrap.isVisible = true
  }

  val inflater = LayoutInflater.from(builder.context)
  comparison.offers.forEach { offer ->
    val row = ItemPriceOfferBinding.inflate(inflater, view.offersContainer, true)
    val isBest = offer == best
    val chain = settings.chainName(offer.chainId).orEmpty()
    val unitPrice = formatUnitPrice(offer.unitPrice, offer.unitKind)
    val details = offerDetails(offer)
    row.tvOfferChain.text = chain
    row.tvOfferUnitPrice.text = unitPrice
    row.tvOfferUnitPrice.setTextColor(ContextCompat.getColor(this, if (isBest) R.color.sage else R.color.ink))
    row.tvOfferBest.isVisible = isBest
    row.tvOfferProduct.text = offer.productName
    row.tvOfferProduct.isVisible = offer.productName.isNotEmpty()
    row.tvOfferMeta.text = details
    if (isBest) row.root.setBackgroundResource(R.drawable.bg_price_best)
    row.root.contentDescription = listOfNotNull(
      chain,
      getString(R.string.prices_detail_best).takeIf { isBest },
      "${formatEuros(offer.unitPrice)} ${perUnitWords(offer.unitKind)}",
      offer.productName.takeIf { it.isNotEmpty() },
      details
    ).joinToString(". ")
  }
  // Chains without this product are listed too: "not available" rather than silently missing.
  val priced = comparison.offers.mapTo(mutableSetOf()) { it.chainId }
  settings.chains.filterNot { it.id in priced }.forEach { chain ->
    val row = ItemPriceOfferBinding.inflate(inflater, view.offersContainer, true)
    row.tvOfferChain.text = chain.name
    row.tvOfferChain.setTextColor(ContextCompat.getColor(this, R.color.muted))
    row.tvOfferUnitPrice.text = getString(R.string.prices_detail_missing)
    row.tvOfferUnitPrice.setTextColor(ContextCompat.getColor(this, R.color.muted))
    row.tvOfferMeta.isVisible = false
    row.root.contentDescription = "${chain.name}. ${getString(R.string.prices_detail_missing)}"
  }

  builder
    .setTitle(state.target.name)
    .setView(view.root)
    .setNeutralButton(R.string.prices_detail_change) { _, _ -> onChangeProduct() }
    .setPositiveButton(R.string.prices_detail_close, null)
    .show()
}

/** The service is not sure what the product is: the user picks, or chooses not to compare it. */
internal fun AppCompatActivity.showPriceChoice(
  itemName: String,
  options: List<PriceCandidate>,
  onChoose: (PriceCandidate) -> Unit,
  onSkip: () -> Unit
) {
  val labels = options.map { option ->
    option.brand?.takeIf { brand -> !option.target.name.contains(brand, ignoreCase = true) }
      ?.let { getString(R.string.prices_choose_option_brand, option.target.name, it) }
      ?: option.target.name
  }.toTypedArray<CharSequence>()
  ourmealDialog()
    .setTitle(getString(R.string.prices_choose_title, itemName))
    .setItems(labels) { _, which -> options.getOrNull(which)?.let(onChoose) }
    .setNeutralButton(R.string.prices_choose_none) { _, _ -> onSkip() }
    .setNegativeButton(R.string.prices_choose_later, null)
    .show()
}

/** Nothing matched: suggestions when there are some, otherwise how to write it better. */
internal fun AppCompatActivity.showPriceNotFound(
  itemName: String,
  options: List<PriceCandidate>,
  onChoose: (PriceCandidate) -> Unit,
  onSkip: () -> Unit
) {
  if (options.isNotEmpty()) return showPriceChoice(itemName, options, onChoose, onSkip)
  ourmealDialog()
    .setTitle(getString(R.string.prices_not_found_title, itemName))
    .setMessage(R.string.prices_not_found_body)
    .setNeutralButton(R.string.prices_not_found_skip) { _, _ -> onSkip() }
    .setPositiveButton(R.string.prices_not_found_ok, null)
    .show()
}

internal fun AppCompatActivity.showPriceSkipped(itemName: String, onRetry: () -> Unit) {
  ourmealDialog()
    .setTitle(getString(R.string.prices_skipped_title, itemName))
    .setMessage(R.string.prices_skipped_body)
    .setNegativeButton(R.string.cancel, null)
    .setPositiveButton(R.string.prices_skipped_retry) { _, _ -> onRetry() }
    .show()
}
