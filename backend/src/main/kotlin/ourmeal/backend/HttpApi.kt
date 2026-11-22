package ourmeal.backend

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveStream
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ourmeal.app.model.CompareItemDto
import ourmeal.app.model.CompareRequest
import ourmeal.app.model.ItemMode
import ourmeal.app.model.ResolveRequest
import kotlin.time.Duration.Companion.minutes

fun Application.priceApi(engine: PriceEngine, locations: Locations, ingestion: Ingestion, database: Database) {
  install(ContentNegotiation) { gson() }
  install(StatusPages) {
    exception<IllegalArgumentException> { call, error ->
      val code = error.message?.takeIf { Regex("[A-Z_]{2,60}").matches(it) } ?: "INVALID_REQUEST"
      call.respond(HttpStatusCode.BadRequest, mapOf("code" to code, "message" to "Revisa la ciudad, los supermercados y el producto."))
    }
    exception<Exception> { call, error ->
      if (error is kotlinx.coroutines.CancellationException) throw error
      call.respond(HttpStatusCode.ServiceUnavailable, mapOf("code" to "SERVICE_UNAVAILABLE", "message" to "El servicio no está disponible. Inténtalo más tarde."))
    }
  }
  install(RateLimit) {
    register(RateLimitName("prices")) {
      rateLimiter(limit = 60, refillPeriod = 1.minutes)
      requestKey { call -> hash(call.request.headers["X-Api-Key"].orEmpty()) }
    }
  }
  routing {
    get("/health") {
      val healthy = withContext(Dispatchers.IO) { runCatching { database.isReady() }.getOrDefault(false) }
      call.respond(if (healthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
        mapOf("database" to if (healthy) "ok" else "unavailable", "ingestion" to ingestion.health()))
    }
    rateLimit(RateLimitName("prices")) {
      route("/v1") {
        intercept(ApplicationCallPipeline.Plugins) {
          val key = call.request.headers["X-Api-Key"].orEmpty()
          if (!Regex("[a-zA-Z0-9_-]{8,128}|[a-fA-F0-9-]{36}").matches(key)) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("code" to "INSTALLATION_KEY_REQUIRED", "message" to "Falta la clave de instalación."))
            finish()
          }
        }
        get("/cities") {
          val query = call.request.queryParameters["query"]?.trim().orEmpty()
          require(query.length in 2..120) { "INVALID_CITY_QUERY" }
          val cities = withContext(Dispatchers.IO) { locations.cities(query) }
          call.respond(cities.map { mapOf("id" to it.id, "name" to it.name, "province" to it.province) })
        }
        get("/stores") {
          val name = call.request.queryParameters["city"]?.trim().orEmpty()
          val province = call.request.queryParameters["province"]?.trim()
          require(name.length in 2..120 && (province == null || province.length <= 120)) { "INVALID_CITY_QUERY" }
          val city = withContext(Dispatchers.IO) { locations.stores(name, province) }
          call.respond(city.toStoresResponse())
        }
        post("/resolve") {
          val body = call.jsonBody()
          val query = body.string("query")
          val mode = body.mode()
          val request = ResolveRequest(query, mode, body.integer("cityId"), body.ids("chainIds"))
          val result = withContext(Dispatchers.IO) { engine.resolve(request) }
          call.respond(result)
        }
        post("/compare") {
          val body = call.jsonBody()
          val items = body.get("items")?.takeIf { it.isJsonArray }?.asJsonArray ?: throw IllegalArgumentException("INVALID_ITEMS")
          require(items.size() in 1..40) { "INVALID_ITEMS" }
          val request = CompareRequest(body.ids("chainIds"), body.ids("storeIds"), items.map { element ->
            require(element.isJsonObject) { "INVALID_ITEM" }
            val item = element.asJsonObject
            CompareItemDto(item.string("id"), item.mode(), item.integer("groupId"), item.integer("canonicalId"), item.integer("quantity") ?: 1)
          }, body.integer("cityId"))
          call.respond(withContext(Dispatchers.IO) { engine.compare(request) })
        }
        post("/aliases") {
          val body = call.jsonBody()
          val query = body.string("query")
          require(query.length in 2..120) { "INVALID_QUERY" }
          val group = body.integer("groupId")
          val canonical = body.integer("canonicalId")
          require(group != null || canonical != null) { "INVALID_TARGET" }
          withContext(Dispatchers.IO) {
            engine.catalog.alias(query, canonical?.let { "c:$it" } ?: "g:$group", call.request.headers["X-Api-Key"]!!)
          }
          call.respond(HttpStatusCode.NoContent)
        }
      }
    }
  }
}

private suspend fun ApplicationCall.jsonBody(): JsonObject = withContext(Dispatchers.IO) {
  val bytes = receiveStream().use { it.readNBytes(65_537) }
  require(bytes.size <= 65_536) { "REQUEST_TOO_LARGE" }
  val text = bytes.toString(Charsets.UTF_8)
  // Bound nesting before Gson allocates a tree, including malicious bodies made of tiny arrays.
  var quoted = false
  var escaped = false
  var depth = 0
  text.forEach { character ->
    if (quoted) {
      if (escaped) escaped = false
      else if (character == '\\') escaped = true
      else if (character == '"') quoted = false
    } else when (character) {
      '"' -> quoted = true
      '{', '[' -> { depth++; require(depth <= 32) { "JSON_TOO_DEEP" } }
      '}', ']' -> { depth--; require(depth >= 0) { "INVALID_JSON" } }
    }
  }
  val parsed = try { JsonParser.parseString(text) } catch (_: RuntimeException) { throw IllegalArgumentException("INVALID_JSON") }
  require(parsed.isJsonObject) { "INVALID_JSON" }
  parsed.asJsonObject
}

private fun JsonObject.string(name: String): String {
  val value = get(name)
  require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) { "INVALID_FIELD" }
  return value.asString.trim()
}

private fun JsonObject.integer(name: String): Int? {
  val value = get(name)?.takeUnless { it.isJsonNull } ?: return null
  require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber && Regex("[1-9]\\d{0,9}").matches(value.asString)) { "INVALID_ID" }
  return value.asString.toIntOrNull()?.takeIf { it > 0 } ?: throw IllegalArgumentException("INVALID_ID")
}

private fun JsonObject.ids(name: String): List<Int> {
  val values = get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: throw IllegalArgumentException("INVALID_IDS")
  require(values.size() <= 1000) { "TOO_MANY_IDS" }
  return values.map { value ->
    val wrapped = JsonObject().apply { add("id", value) }
    wrapped.integer("id") ?: throw IllegalArgumentException("INVALID_ID")
  }.distinct()
}

private fun JsonObject.mode(): ItemMode = when (get("mode")?.takeIf { it.isJsonPrimitive }?.asString) {
  null, "GENERIC" -> ItemMode.GENERIC
  "BRAND" -> ItemMode.BRAND
  else -> throw IllegalArgumentException("INVALID_MODE")
}
