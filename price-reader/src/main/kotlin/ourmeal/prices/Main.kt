package ourmeal.prices

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/** CLI for the backend ingestion worker; Android continues to consume its price-service API. */
fun main(arguments: Array<String>) {
  val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
  try {
    val args = Arguments(arguments.toList())
    if (args.command == "help") {
      println("""
        Uso: price-reader <cities|stores|read|setup> --city <ciudad> [opciones]
          --province <provincia>     Distingue ciudades con el mismo nombre.
          --query <producto>         Producto que buscar con read.
          --chain <id,id>            Solo cadenas detectadas en esa ciudad; setup necesita una.
          --rules <archivo.json>    Reglas opcionales para la navegación de cada cadena.
          --state <directorio>      Caché y perfiles privados; por defecto fuera del repositorio.
          --headless                Sin ventana (read); por defecto el navegador es visible.
          --channel chrome          Usa Google Chrome instalado; por defecto usa Chromium.
        cities y stores no abren el navegador. setup permite elegir ciudad/tienda en su web.
      """.trimIndent())
      return
    }
    val state = Path.of(args.value("state") ?: Path.of(System.getProperty("user.home"), ".local", "state", "ourmeal-price-reader").toString())
    val discovery = Discovery(
      state,
      System.getenv("OURMEAL_NOMINATIM_URL") ?: "https://nominatim.openstreetmap.org/search",
      System.getenv("OURMEAL_OVERPASS_URL") ?: "https://overpass-api.de/api/interpreter"
    )
    val candidates = discovery.cities(args.required("city"))
    if (args.command == "cities") {
      println(gson.toJson(candidates.map { mapOf("id" to it.id, "name" to it.name, "province" to it.province) }))
      return
    }
    val matches = candidates.filter { normalized(it.name) == normalized(args.required("city")) }
      .filter { args.value("province") == null || normalized(it.province.orEmpty()) == normalized(args.required("province")) }
    val city = matches.singleOrNull() ?: error("Ciudad no encontrada o ambigua. Consulta cities e indica --province cuando corresponda.")
    val catalog = discovery.stores(city)
    if (args.command == "stores") {
      println(gson.toJson(catalog.toStoresResponse()))
      return
    }
    val selected = args.value("chain")?.split(',')?.map { it.toInt() }?.toSet()
    require(selected == null || selected.all { id -> catalog.chains.any { it.id == id } }) { "Una cadena no pertenece a la ciudad elegida." }
    val rules = args.value("rules")?.let { file ->
      val json = JsonParser.parseString(Files.readString(Path.of(file)))
      require(json.isJsonArray) { "El archivo de reglas debe ser una lista." }
      json.asJsonArray.map { element ->
        val rule = element.asJsonObject
        SiteRule(
          rule.text("chain") ?: error("Falta chain en una regla."),
          rule.text("website")?.let(::publicWebsite) ?: error("Una regla necesita website https."),
          rule.text("searchUrl"), rule.text("searchInput")
        )
      }
    }.orEmpty()
    val options = BrowserOptions(state, args.flag("headless"), args.value("channel"), rules)
    BrowserReader(options).use { browser ->
      if (args.command == "setup") {
        val id = selected?.singleOrNull() ?: error("setup necesita --chain con una sola cadena de la ciudad.")
        browser.setup(city, catalog.chains.single { it.id == id }) {
          System.err.println("Elige la ciudad o tienda en el navegador, sin iniciar sesión. Pulsa intro aquí para guardar el perfil y cerrar.")
          readlnOrNull() ?: error("setup necesita una terminal interactiva.")
        }
      } else {
        println(gson.toJson(browser.read(city, catalog.chains, args.required("query"), selected)))
      }
    }
  } catch (error: DiscoveryFailure) {
    System.err.println("No se pudo completar el descubrimiento: ${error.code}. Vuelve a intentarlo o configura otra instancia del servicio.")
    exitProcess(1)
  } catch (_: com.microsoft.playwright.PlaywrightException) {
    System.err.println("No se pudo abrir o utilizar el navegador. Ejecuta :price-reader:installBrowser y revisa la sesión gráfica.")
    exitProcess(1)
  } catch (error: Exception) {
    // External error messages may contain URLs, queries, cookies or local paths.
    System.err.println(if (error is IllegalArgumentException || error is IllegalStateException) error.message else "No se completó la lectura. Revisa la conexión y los servicios de ciudades y tiendas.")
    exitProcess(1)
  }
}

private class Arguments(raw: List<String>) {
  val command = raw.firstOrNull() ?: "help"
  private val values = mutableMapOf<String, String>()
  private val flags = mutableSetOf<String>()
  init {
    require(command in setOf("help", "cities", "stores", "read", "setup")) { "Comando desconocido. Usa help." }
    var index = 1
    while (index < raw.size) {
      val key = raw[index++].removePrefix("--")
      if (key == "headless") flags += key
      else {
        require(key in setOf("city", "province", "query", "chain", "rules", "state", "channel") && index < raw.size) { "Opción desconocida o sin valor. Usa help." }
        values[key] = raw[index++]
      }
    }
  }
  fun value(key: String): String? = values[key]
  fun required(key: String): String = values[key]?.takeIf { it.isNotBlank() } ?: error("Falta --$key. Usa help.")
  fun flag(key: String): Boolean = key in flags
}
