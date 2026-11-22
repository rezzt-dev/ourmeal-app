package ourmeal.prices.shared

import ourmeal.app.model.matchKey

data class Quantity(val kind: String, val total: Double) {
  fun pricePerUnit(price: Double): Double = price / if (kind == "count") total else total / 1000.0
}

/** One normalization and quantity parser for ingestion, catalogue matching and Android. */
object ProductText {
  private val synonyms = mapOf(
    "papel water" to "papel higienico", "papel wc" to "papel higienico", "cocacola" to "coca cola",
    "yogurt" to "yogur", "yogures" to "yogur", "semi" to "semidesnatada",
    "semidesnatado" to "semidesnatada", "desnatado" to "desnatada", "entero" to "entera",
    "litros" to "l", "litro" to "l", "kilogramos" to "kg", "kilogramo" to "kg",
    "gramos" to "g", "gramo" to "g", "mililitros" to "ml", "centilitros" to "cl",
    "unidades" to "ud", "unidad" to "ud", "uds" to "ud", "rollos" to "rollo"
  )
  private val quantity = Regex("(?<![\\d.])((?:\\d+\\s*[x×]\\s*)*)(\\d+(?:\\.\\d+)?)\\s*(kg|g|ml|cl|l|ud|rollo|huevos|bolsas|capsulas|lavados)(?![a-zñ])")
  private val stops = setOf("de", "del", "el", "la", "los", "las", "en", "para", "un", "una")
  private val exclusive = listOf(
    setOf("entera", "semidesnatada", "desnatada"), setOf("con gas", "sin gas"),
    setOf("zero", "light", "original"), setOf("integral", "blanco"), setOf("con sal", "sin sal")
  )
  private val required = setOf("sin gluten", "sin lactosa", "sin azucar", "sin sal", "ecologico", "bio")

  fun normalize(raw: String): String {
    var value = matchKey(raw).replace(Regex("(\\d),(\\d)"), "$1.$2")
      .replace(Regex("[^a-zñ0-9.× ]"), " ")
      .replace(Regex("(\\d)([a-wyzñ])"), "$1 $2")
      .replace(Regex("([a-zñ])(\\d)"), "$1 $2")
    synonyms.entries.sortedByDescending { it.key.length }.forEach { (key, replacement) ->
      value = value.replace(Regex("(?<![a-zñ])${Regex.escape(key)}(?![a-zñ])"), replacement)
    }
    return value.trim().replace(Regex("\\s+"), " ")
  }

  fun quantity(raw: String): Quantity? {
    val value = normalize(raw)
    val matches = quantity.findAll(value).toList()
    if (matches.isEmpty()) {
      return Regex("\\b(media )?docena\\b").find(value)?.let { Quantity("count", if (it.groupValues[1].isEmpty()) 12.0 else 6.0) }
    }
    if (matches.size != 1) return null
    val match = matches.single()
    val (kind, factor) = when (match.groupValues[3]) {
      "kg" -> "mass" to 1000.0; "g" -> "mass" to 1.0
      "l" -> "volume" to 1000.0; "ml" -> "volume" to 1.0; "cl" -> "volume" to 10.0
      else -> "count" to 1.0
    }
    val count = Regex("\\d+").findAll(match.groupValues[1]).fold(1.0) { total, m -> total * m.value.toDouble() }
    val total = match.groupValues[2].toDouble() * factor * count
    if (!total.isFinite() || total <= 0 || total > 1_000_000 || (kind == "count" && total % 1 != 0.0)) return null
    return Quantity(kind, total)
  }

  fun core(raw: String, brand: String? = null): String {
    var value = normalize(raw).replace(quantity) { match ->
      match.groupValues[3].takeIf { it in setOf("rollo", "huevos", "bolsas", "capsulas", "lavados") } ?: " "
    }.replace(Regex("\\b(media )?docena\\b"), " ")
    if (!brand.isNullOrBlank()) value = " $value ".replace(" ${normalize(brand)} ", " ")
    return value.trim().split(Regex("\\s+")).filter { it.isNotBlank() && it !in stops && it !in setOf("brik", "brick") }.joinToString(" ")
  }

  /** Matching strips accents, but labels keep the spelling and case present in the source. */
  fun displayName(raw: String, brand: String? = null): String {
    val words = Regex("[\\p{L}\\p{N}]+").findAll(raw).map { it.value }.toList()
    return core(raw, brand).split(' ').filter { it.isNotBlank() }.joinToString(" ") { token ->
      words.firstOrNull { normalize(it) == token } ?: token
    }
  }

  fun groupKey(name: String, brand: String?, kind: String): String = "$kind:${core(name, brand).split(' ').sorted().joinToString(" ")}"

  /** Restrictions in a query must be present; mutually exclusive variants cannot be substituted. */
  fun compatible(query: String, candidate: String): Boolean {
    val q = " ${normalize(query)} "; val c = " ${normalize(candidate)} "
    if (required.any { " $it " in q && " $it " !in c }) return false
    return exclusive.none { group ->
      val requested = group.filter { " $it " in q }.toSet()
      val found = group.filter { " $it " in c }.toSet()
      requested.isNotEmpty() && requested != found
    }
  }
}
