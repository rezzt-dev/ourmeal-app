package ourmeal.app.model

import java.text.Normalizer
import java.util.Locale

private val extraWhitespace = Regex("\\s+")

/** Trims a user-written name and collapses repeated spaces into one. */
fun normalizeName(raw: String): String = raw.trim().replace(extraWhitespace, " ")

/** Comparison key: same text for case, accents and spacing variants, keeping ñ. */
fun matchKey(raw: String): String = buildString {
  Normalizer.normalize(normalizeName(raw), Normalizer.Form.NFC)
    .lowercase(Locale.ROOT)
    .forEach { char ->
      if (char == 'ñ') append(char)
      else Normalizer.normalize(char.toString(), Normalizer.Form.NFD).forEach { part ->
        if (Character.getType(part) != Character.NON_SPACING_MARK.toInt()) append(part)
      }
    }
}
