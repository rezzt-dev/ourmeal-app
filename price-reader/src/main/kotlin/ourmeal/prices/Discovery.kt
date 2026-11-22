package ourmeal.prices

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

/** Safe diagnostic code; never includes city queries, addresses, credentials or response bodies. */
class DiscoveryFailure internal constructor(val code: String) : IOException(code)

/** One shared discovery client per service. Public instances receive cached, serial requests. */
class Discovery(
  private val state: Path,
  private val nominatim: String = "https://nominatim.openstreetmap.org/search",
  private val overpass: String = "https://overpass-api.de/api/interpreter",
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(Duration.ofSeconds(10))
    .readTimeout(Duration.ofSeconds(50))
    .callTimeout(Duration.ofSeconds(60))
    .build()
) {
  private var nextRequestNanos = 0L

  /** A submitted city search; this must not be wired to autocomplete on a public Nominatim instance. */
  @Synchronized
  fun cities(query: String): List<City> {
    require(query.trim().length in 2..120) { "Indica una ciudad de entre 2 y 120 caracteres." }
    val url = nominatim.toHttpUrl().newBuilder()
      .addQueryParameter("q", query.trim())
      .addQueryParameter("format", "jsonv2")
      .addQueryParameter("addressdetails", "1")
      .addQueryParameter("countrycodes", "es")
      .addQueryParameter("featureType", "settlement")
      .addQueryParameter("limit", "10")
      .build()
    val body = cached("cities:${normalized(query)}", Duration.ofDays(7), { raw ->
      if (!JsonParser.parseString(raw).isJsonArray) throw DiscoveryFailure("CITIES_INVALID_RESPONSE")
    }) {
      fetch(Request.Builder().url(url).build(), "CITIES")
    }
    return parseCities(JsonParser.parseString(body).asJsonArray)
  }

  @Synchronized
  fun stores(city: City): CityCatalog {
    require(city.relationId > 0) { "La ciudad necesita un límite administrativo de OSM." }
    val query = "[out:json][timeout:40];rel(${city.relationId});map_to_area->.city;" +
      "nwr[shop=supermarket](area.city);out tags;"
    val body = cached("stores:${city.relationId}", Duration.ofDays(1), { raw ->
      val parsed = JsonParser.parseString(raw)
      if (!parsed.isJsonObject || parsed.asJsonObject.has("remark") || parsed.asJsonObject.get("elements")?.isJsonArray != true) {
        throw DiscoveryFailure("STORES_INCOMPLETE_RESPONSE")
      }
    }) {
      fetch(Request.Builder().url(overpass).post(FormBody.Builder().add("data", query).build()).build(), "STORES")
    }
    val json = JsonParser.parseString(body).asJsonObject
    if (json.has("remark")) throw IOException("La consulta de tiendas no se completó; no se usarán resultados parciales.")
    return CityCatalog(city, parseChains(json.getAsJsonArray("elements") ?: throw IOException("Respuesta de tiendas incompleta.")))
  }

  private fun fetch(request: Request, operation: String): String {
    val remaining = nextRequestNanos - System.nanoTime()
    if (remaining > 0) Thread.sleep((remaining / 1_000_000) + 1)
    nextRequestNanos = System.nanoTime() + 1_100_000_000
    val identified = request.newBuilder()
      .header("User-Agent", "Ourmeal-PriceReader/1.0 (https://github.com/rezzt-dev/ourmeal-app)")
      .header("Accept", "application/json")
      .build()
    try {
      return client.newCall(identified).execute().use { response ->
        if (!response.isSuccessful) throw DiscoveryFailure("${operation}_HTTP_${response.code}")
        response.body?.string() ?: throw DiscoveryFailure("${operation}_EMPTY_RESPONSE")
      }
    } catch (error: DiscoveryFailure) {
      throw error
    } catch (_: IOException) {
      throw DiscoveryFailure("${operation}_NETWORK_FAILURE")
    }
  }

  /** Discovery cache only. Cookies and browser sessions live in separate private profiles. */
  private fun cached(key: String, ttl: Duration, validate: (String) -> Unit, fetch: () -> String): String {
    val directory = state.resolve("discovery")
    Files.createDirectories(directory)
    val file = directory.resolve("${stableKey(key)}.json")
    if (Files.exists(file) && System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() < ttl.toMillis()) {
      val cached = Files.readString(file)
      try {
        JsonParser.parseString(cached)
        validate(cached)
        return cached
      } catch (_: RuntimeException) {
        // This is a disposable cache, never a user-data file.
      } catch (_: IOException) {
        // A partial response is not a usable cached city catalogue.
      }
    }
    val fresh = fetch()
    JsonParser.parseString(fresh)
    validate(fresh)
    val temporary = Files.createTempFile(directory, "discovery-", ".json")
    try {
      Files.writeString(temporary, fresh)
      Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
      Files.deleteIfExists(temporary)
    }
    return fresh
  }
}

internal fun parseCities(elements: JsonArray): List<City> = elements.mapNotNull { element ->
  val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
  if (item.text("osm_type") != "relation") return@mapNotNull null
  if (item.text("addresstype") !in setOf("city", "town", "village", "municipality")) return@mapNotNull null
  val relation = item.text("osm_id")?.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
  val address = item.get("address")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
  val name = sequenceOf("city", "town", "village", "municipality").mapNotNull { address.text(it) }.firstOrNull()
    ?: item.text("name") ?: return@mapNotNull null
  val province = address.text("province") ?: address.text("state_district") ?: address.text("state")
  City(stableId("city:relation:$relation"), name, province, relation)
}.distinctBy { it.relationId }

internal fun parseChains(elements: JsonArray): List<Chain> {
  data class Entry(val key: String, val name: String, val tags: JsonObject, val id: String)
  val entries = elements.mapNotNull { element ->
    val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
    val tags = item.get("tags")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
    if (tags.text("shop") != "supermarket") return@mapNotNull null
    val name = tags.text("brand") ?: tags.text("operator") ?: tags.text("name") ?: return@mapNotNull null
    val key = tags.text("brand:wikidata") ?: normalized(name)
    val type = item.text("type")?.takeIf { it in setOf("node", "way", "relation") } ?: return@mapNotNull null
    val id = item.text("id")?.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
    Entry(key, name, tags, "$type:$id")
  }
  // A branch with no wikidata tag still belongs to the same named brand.
  val wikidataByName = entries.filter { it.key.startsWith("Q") }.associate { normalized(it.name) to it.key }
  return entries.groupBy { wikidataByName[normalized(it.name)] ?: it.key }.map { (key, branches) ->
    val id = stableId("chain:$key")
    val name = branches.first().name
    val websites = branches.flatMap { branch ->
      sequenceOf("brand:website", "website", "contact:website", "operator:website")
        .mapNotNull { branch.tags.text(it) }.flatMap { it.split(';').asSequence() }.toList()
    }.mapNotNull(::publicWebsite).distinct()
    val stores = branches.distinctBy { it.id }.map {
      Store(stableId("store:${it.id}"), id, name, it.tags.text("name") ?: name)
    }
    Chain(id, name, websites, stores)
  }.sortedBy { normalized(it.name) }
}

internal fun JsonObject.text(key: String): String? = get(key)
  ?.takeIf { it.isJsonPrimitive && (it.asJsonPrimitive.isString || it.asJsonPrimitive.isNumber) }
  ?.asString?.trim()?.takeIf { it.isNotEmpty() }
