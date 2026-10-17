package ourmeal.app.model

private val extraWhitespace = Regex("\\s+")

/** Trims a user-written name and collapses repeated spaces into one. */
fun normalizeName(raw: String): String = raw.trim().replace(extraWhitespace, " ")
