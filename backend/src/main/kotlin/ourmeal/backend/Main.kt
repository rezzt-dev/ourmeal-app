package ourmeal.backend

import com.google.gson.Gson
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import ourmeal.prices.BrowserOptions
import ourmeal.prices.Discovery
import ourmeal.prices.SiteRule
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
  require(args.firstOrNull() in setOf("serve", "migrate")) { "Usa serve o migrate. Consulta backend/readme.md." }
  val pool = Database.pool(required("OURMEAL_DB_URL"), required("OURMEAL_DB_USER"), required("OURMEAL_DB_PASSWORD"))
  val database = Database(pool)
  if (args.first() == "migrate") {
    pool.use { database.migrate() }
    println("Esquema de precios preparado.")
    return
  }
  val state = Path.of(System.getenv("OURMEAL_PRICE_STATE") ?: Path.of(System.getProperty("user.home"), ".local", "state", "ourmeal-price-reader").toString())
  val rules = System.getenv("OURMEAL_PRICE_RULES")?.let { file ->
    Gson().fromJson(Files.readString(Path.of(file)), Array<SiteRule>::class.java).toList()
  }.orEmpty()
  val options = BrowserOptions(state, headless = true, channel = System.getenv("OURMEAL_BROWSER_CHANNEL"), rules = rules)
  val catalog = CatalogRepository(database)
  val ingestion = Ingestion(catalog, { BrowserSource(options) }, System.getenv("OURMEAL_BROWSER_ENABLED") == "true")
  val locations = OsmLocations(Discovery(state,
    System.getenv("OURMEAL_NOMINATIM_URL") ?: "https://nominatim.openstreetmap.org/search",
    System.getenv("OURMEAL_OVERPASS_URL") ?: "https://overpass-api.de/api/interpreter"), catalog)
  val engine = PriceEngine(catalog, ingestion)
  val host = System.getenv("OURMEAL_PRICE_HOST") ?: "127.0.0.1"
  val port = System.getenv("OURMEAL_PRICE_PORT")?.toIntOrNull() ?: 8080
  val server = embeddedServer(Netty, host = host, port = port) { priceApi(engine, locations, ingestion, database) }
  Runtime.getRuntime().addShutdownHook(Thread {
    server.stop(1000, 5000)
    ingestion.close()
    pool.close()
  })
  server.start(wait = true)
}

private fun required(key: String): String = System.getenv(key)?.takeIf { it.isNotBlank() }
  ?: error("Falta configurar $key; consulta backend/readme.md.")
