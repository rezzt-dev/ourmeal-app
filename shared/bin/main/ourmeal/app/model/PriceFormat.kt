package ourmeal.app.model

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Locale of the interface texts and of every price shown: euros written the Spanish way. */
val PRICE_LOCALE: Locale = Locale.forLanguageTag("es-ES")

/** "1,15 €". */
fun formatEuros(value: Double, locale: Locale = PRICE_LOCALE): String =
  NumberFormat.getCurrencyInstance(locale).apply {
    currency = Currency.getInstance("EUR")
    minimumFractionDigits = 2
    maximumFractionDigits = 2
  }.format(value)

/** Invisible glue: a price per unit never breaks across lines around its slash. */
private const val WORD_JOINER = '\u2060'

/** "1,15 €/kg", "0,98 €/L" or "0,25 €/ud", kept on one line. */
fun formatUnitPrice(value: Double, kind: UnitKind, locale: Locale = PRICE_LOCALE): String =
  "${formatEuros(value, locale)}$WORD_JOINER/$WORD_JOINER${unitSymbol(kind)}"

fun unitSymbol(kind: UnitKind): String = when (kind) {
  UnitKind.MASS -> "kg"
  UnitKind.VOLUME -> "L"
  UnitKind.COUNT -> "ud"
}

/** Pack size from grams, millilitres or units: "500 g", "1,5 kg", "330 ml", "2 L", "12 ud". */
fun formatPack(total: Double, kind: UnitKind, locale: Locale = PRICE_LOCALE): String? {
  if (!total.isFinite() || total <= 0.0) return null
  val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
  return when (kind) {
    UnitKind.MASS -> if (total >= 1000.0) "${number.format(total / 1000.0)} kg" else "${number.format(total)} g"
    UnitKind.VOLUME -> if (total >= 1000.0) "${number.format(total / 1000.0)} L" else "${number.format(total)} ml"
    UnitKind.COUNT -> "${number.format(total)} ud"
  }
}
