package ourmeal.app

import android.content.Context
import app.ourmeat.R
import ourmeal.app.controller.PriceLineView
import ourmeal.app.model.ItemPriceState
import ourmeal.app.model.PRICE_LOCALE
import ourmeal.app.model.PriceOffer
import ourmeal.app.model.PriceSource
import ourmeal.app.model.UnitKind
import ourmeal.app.model.formatEuros
import ourmeal.app.model.formatPack
import ourmeal.app.model.formatUnitPrice
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val priceDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M", PRICE_LOCALE)

internal fun formatPriceDate(at: Long): String = priceDate.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

/** "el kilo", spoken after a price. */
internal fun Context.perUnitWords(kind: UnitKind): String = getString(
  when (kind) {
    UnitKind.MASS -> R.string.prices_per_kilo
    UnitKind.VOLUME -> R.string.prices_per_litre
    UnitKind.COUNT -> R.string.prices_per_unit
  }
)

/** "kilo", the unit prices are compared by. */
internal fun Context.unitWord(kind: UnitKind): String = getString(
  when (kind) {
    UnitKind.MASS -> R.string.prices_unit_kilo
    UnitKind.VOLUME -> R.string.prices_unit_litre
    UnitKind.COUNT -> R.string.prices_unit_unit
  }
)

internal fun Context.sourceLabel(source: PriceSource): String = getString(
  when (source) {
    PriceSource.ONLINE -> R.string.prices_detail_source_online
    PriceSource.OPEN_PRICES -> R.string.prices_detail_source_open_prices
    PriceSource.USER -> R.string.prices_detail_source_user
  }
)

/** "0,58 € el envase de 500 g · en oferta · precio online · precio del 12/11". */
internal fun Context.offerDetails(offer: PriceOffer): String = buildList {
  val pack = offer.packTotal?.let { formatPack(it, offer.unitKind) }
  add(
    if (pack != null) getString(R.string.prices_detail_pack, formatEuros(offer.price), pack)
    else getString(R.string.prices_detail_pack_unknown, formatEuros(offer.price))
  )
  if (offer.onOffer) add(getString(R.string.prices_detail_offer))
  add(sourceLabel(offer.source))
  add(getString(R.string.prices_detail_date, formatPriceDate(offer.observedAt)))
}.joinToString(" · ")

internal fun Context.priceLine(
  itemName: String,
  state: ItemPriceState,
  chainName: (Int) -> String?,
  now: Long
): PriceLineView = when (state) {
  ItemPriceState.Loading -> muted(getString(R.string.prices_line_loading), actionable = false)
  is ItemPriceState.Compared -> {
    val best = state.comparison.best
    val chain = best?.let { chainName(it.chainId) }
    if (best == null || chain == null) {
      muted(getString(R.string.prices_line_none), actionable = true, itemName = itemName)
    } else {
      val unitPrice = formatUnitPrice(best.unitPrice, best.unitKind)
      val compared = state.comparison.offers.size > 1
      val main = getString(if (compared) R.string.prices_line_best else R.string.prices_line_only, chain, unitPrice)
      val text = if (best.isFresh(now)) main else getString(R.string.prices_line_old, main, formatPriceDate(best.observedAt))
      val description = getString(
        if (compared) R.string.prices_line_best_description else R.string.prices_line_only_description,
        itemName, chain, formatEuros(best.unitPrice), perUnitWords(best.unitKind)
      )
      PriceLineView(text, R.color.sage, description, actionable = true)
    }
  }
  is ItemPriceState.NeedsChoice ->
    PriceLineView(
      getString(R.string.prices_line_choose), R.color.brand,
      actionDescription(itemName, R.string.prices_line_choose), actionable = true
    )
  is ItemPriceState.NotFound -> muted(getString(R.string.prices_line_not_found), actionable = true, itemName = itemName)
  ItemPriceState.Skipped -> muted(getString(R.string.prices_line_skipped), actionable = true, itemName = itemName)
  ItemPriceState.Offline -> muted(getString(R.string.prices_line_offline), actionable = true, itemName = itemName)
  ItemPriceState.Failed -> muted(getString(R.string.prices_line_failed), actionable = true, itemName = itemName)
}

private fun Context.muted(text: String, actionable: Boolean, itemName: String? = null): PriceLineView =
  PriceLineView(
    text, R.color.muted,
    itemName?.let { getString(R.string.prices_line_action_description, it, text) } ?: text,
    actionable
  )

private fun Context.actionDescription(itemName: String, text: Int): String =
  getString(R.string.prices_line_action_description, itemName, getString(text))
