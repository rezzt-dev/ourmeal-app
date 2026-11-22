package ourmeal.prices

import com.google.gson.JsonParser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.TimeoutError
import com.microsoft.playwright.options.ServiceWorkerPolicy
import com.microsoft.playwright.options.WaitUntilState
import java.net.URLEncoder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions

/** Optional per-site navigation hints. Chains are still selected from the city discovery result. */
data class SiteRule(val chain: String, val website: String, val searchUrl: String? = null, val searchInput: String? = null)

data class BrowserOptions(
  val state: Path,
  val headless: Boolean = false,
  val channel: String? = null,
  val rules: List<SiteRule> = emptyList()
)

/**
 * A real Chromium process with JavaScript and a persistent, dedicated city/chain profile.
 * All methods run on the creating thread, as required by Playwright Java.
 */
class BrowserReader internal constructor(
  private val options: BrowserOptions,
  private val playwright: Playwright,
  private val prepareContext: (BrowserContext) -> Unit
) : AutoCloseable {
  constructor(options: BrowserOptions) : this(options, createBrowserDriver(), {})
  private val web = PublicWebPolicy()

  /** Setup can select the online shop, city or cookie preferences in the visible browser. */
  fun setup(city: City, chain: Chain, waitForUser: () -> Unit) {
    require(!options.headless) { "La preparación de la tienda necesita un navegador visible." }
    val site = sites(chain).firstOrNull() ?: error("La cadena no tiene una web https. Añádela al archivo de reglas.")
    withPage(city, chain, site) { page ->
      navigate(page, site)
      waitForUser()
    }
  }

  fun read(city: City, chains: List<Chain>, query: String, selectedChainIds: Set<Int>? = null): ReadReport {
    require(query.trim().length in 2..120) { "Indica un producto de entre 2 y 120 caracteres." }
    if (selectedChainIds != null) require(selectedChainIds.all { id -> chains.any { it.id == id } }) {
      "Una cadena seleccionada no pertenece a la ciudad. Actualiza las tiendas al cambiar de ciudad."
    }
    val selected = chains.filter { selectedChainIds == null || it.id in selectedChainIds }
    return ReadReport(city.name, selected.map { chain ->
      val sites = sites(chain)
      if (sites.isEmpty()) ChainRead(chain.id, chain.name, ReadStatus.NO_WEBSITE)
      else readChain(city, chain, query.trim(), sites)
    })
  }

  private fun sites(chain: Chain): List<String> {
    val rule = options.rules.firstOrNull { normalized(it.chain) == normalized(chain.name) }
    return (rule?.let { listOf(it.website) } ?: chain.websites).mapNotNull(::publicWebsite).distinct().take(3)
  }

  private fun readChain(city: City, chain: Chain, query: String, sites: List<String>): ChainRead {
    var status = ReadStatus.UNAVAILABLE
    for (site in sites) {
      try {
        val result = withPage(city, chain, site) { page -> readSite(page, city, chain, site, query) }
        if (result.status == ReadStatus.OK || result.status == ReadStatus.INTERACTION_REQUIRED) return result
        status = result.status
      } catch (_: PlaywrightException) {
        status = ReadStatus.UNAVAILABLE
      } catch (_: java.io.IOException) {
        status = ReadStatus.UNAVAILABLE
      }
    }
    return ChainRead(chain.id, chain.name, status)
  }

  private fun readSite(page: Page, city: City, chain: Chain, site: String, query: String): ChainRead {
    var navigationStatus: Int? = null
    page.onResponse { response ->
      if (response.request().isNavigationRequest && response.request().frame() == page.mainFrame()) {
        navigationStatus = response.status()
      }
    }
    val rule = options.rules.firstOrNull { normalized(it.chain) == normalized(chain.name) }
    val result = navigate(page, site)
    if (requiresInteraction(page, result)) return ChainRead(chain.id, chain.name, ReadStatus.INTERACTION_REQUIRED)
    if (result != null && result >= 400) return ChainRead(chain.id, chain.name, ReadStatus.UNAVAILABLE)
    if (rule?.searchUrl != null) {
      val url = rule.searchUrl.replace("{query}", encode(query)).replace("{city}", encode(city.name))
      val response = navigate(page, url)
      if (requiresInteraction(page, response)) return ChainRead(chain.id, chain.name, ReadStatus.INTERACTION_REQUIRED)
      if (response != null && response >= 400) return ChainRead(chain.id, chain.name, ReadStatus.UNAVAILABLE)
    } else {
      val inputs = page.locator(rule?.searchInput ?: SEARCH_INPUT)
      val input = (0 until inputs.count()).map { inputs.nth(it) }.firstOrNull { it.isVisible && it.isEditable }
        ?: return ChainRead(chain.id, chain.name, ReadStatus.SEARCH_UNSUPPORTED)
      input.fill(query)
      throttle(page.url())
      // Browser keyboard events also work with JavaScript search forms; no private API calls.
      input.press("Enter")
    }
    try {
      page.waitForFunction(HAS_PRODUCTS, null, Page.WaitForFunctionOptions().setTimeout(12_000.0))
    } catch (_: TimeoutError) {
      if (requiresInteraction(page, navigationStatus)) return ChainRead(chain.id, chain.name, ReadStatus.INTERACTION_REQUIRED)
      if (navigationStatus?.let { it >= 400 } == true) return ChainRead(chain.id, chain.name, ReadStatus.UNAVAILABLE)
      return ChainRead(chain.id, chain.name, ReadStatus.EMPTY)
    }
    val documents = page.locator("script[type='application/ld+json']").allTextContents()
    val products = documents.flatMap { document ->
      try { pricesFromJson(JsonParser.parseString(document), chain.id, page.url()) }
      catch (_: RuntimeException) { emptyList() }
    }.distinctBy { listOf(it.externalId, it.price, it.packTotal) }
    return ChainRead(chain.id, chain.name, if (products.isEmpty()) ReadStatus.EMPTY else ReadStatus.OK, products)
  }

  /** Profiles are never shared across cities, brands or sites, or with the personal browser. */
  private fun <T> withPage(city: City, chain: Chain, site: String, action: (Page) -> T): T {
    val siteHost = java.net.URI(site).host
    val directory = options.state.resolve("profiles")
      .resolve("${city.relationId}-${stableKey(normalized(chain.name) + ":" + siteHost)}")
    Files.createDirectories(directory)
    if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
    }
    FileChannel.open(directory.resolve(".reader.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
      val lock = try { channel.tryLock() } catch (_: java.nio.channels.OverlappingFileLockException) { null }
      if (lock == null) throw java.io.IOException("El perfil ya está abierto por otro lector.")
      lock.use {
        val launch = BrowserType.LaunchPersistentContextOptions()
          .setHeadless(options.headless)
          .setLocale("es-ES")
          .setAcceptDownloads(false)
          .setServiceWorkers(ServiceWorkerPolicy.BLOCK)
          .setIgnoreDefaultArgs(listOf("--enable-automation"))
          .setArgs(listOf("--disable-blink-features=AutomationControlled"))
        options.channel?.let { launch.setChannel(it) }
        playwright.chromium().launchPersistentContext(directory, launch).use { context ->
          context.setDefaultTimeout(12_000.0)
          context.setDefaultNavigationTimeout(30_000.0)
          protect(context)
          prepareContext(context)
          val page = context.pages().firstOrNull() ?: context.newPage()
          return action(page)
        }
      }
    }
  }

  private fun protect(context: BrowserContext) {
    context.route("**/*") { route ->
      if (web.allows(route.request().url())) route.resume() else route.abort()
    }
    // WebSockets have no role in reading public catalogue pages and can access private hosts.
    context.routeWebSocket("**/*") { socket -> socket.close() }
    context.onPage { page -> page.onDialog { dialog -> dialog.dismiss() } }
  }

  private fun navigate(page: Page, url: String): Int? {
    require(publicWebsite(url) != null) { "La web necesita una dirección https pública." }
    throttle(url)
    return page.navigate(url, Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED))?.status()
  }

  /** Main navigations/searches are spaced; page assets load as in a normal browser. */
  private fun throttle(url: String) {
    val host = java.net.URI(url).host ?: return
    val scheduled = synchronized(navigationLock) {
      val next = maxOf(System.currentTimeMillis(), (lastNavigation[host] ?: 0) + 1100)
      lastNavigation[host] = next
      next
    }
    val remaining = scheduled - System.currentTimeMillis()
    if (remaining > 0) Thread.sleep(remaining)
  }

  private fun requiresInteraction(page: Page, status: Int?): Boolean {
    if (status in setOf(401, 403, 429)) return true
    val visible = page.locator("body").innerText().take(20_000).lowercase()
    return listOf("verify you are human", "verifica que eres humano", "checking your browser", "access denied", "just a moment...")
      .any { it in visible }
  }

  override fun close() = playwright.close()

  companion object {
    private val navigationLock = Any()
    private val lastNavigation = mutableMapOf<String, Long>()
    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
    private const val SEARCH_INPUT = "input[type='search'], input[name='q'], input[name='search'], input[placeholder*='buscar' i]"
    private val HAS_PRODUCTS = """
      () => {
        const containsProduct = (v, depth = 0) => {
          if (!v || typeof v !== 'object' || depth > 24) return false;
          if (v['@type'] === 'Product' || (Array.isArray(v['@type']) && v['@type'].includes('Product'))) return true;
          return Object.values(v).some(child => containsProduct(child, depth + 1));
        };
        return [...document.querySelectorAll('script[type="application/ld+json"]')].some(script => {
          try { return containsProduct(JSON.parse(script.textContent)); } catch { return false; }
        });
      }
    """.trimIndent()
  }
}

/** Browser installation is an explicit build task, not a side effect of a catalogue read. */
internal fun createBrowserDriver(): Playwright = Playwright.create(Playwright.CreateOptions().setEnv(mapOf(
  "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD" to "1",
  "PLAYWRIGHT_SKIP_BROWSER_GC" to "1"
)))
