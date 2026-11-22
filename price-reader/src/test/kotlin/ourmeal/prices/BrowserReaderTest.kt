package ourmeal.prices

import com.microsoft.playwright.options.Cookie
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real browser, intercepted HTTPS responses, temporary profiles; no external shops or user data. */
class BrowserReaderTest {
  @get:Rule val temporary = TemporaryFolder()
  private val city = City(1, "Primera ciudad", null, 1)
  private val chain = Chain(11, "Cadena descubierta", listOf("https://shop.ourmeal.test/"), listOf(Store(111, 11, "Cadena descubierta", "Tienda")))

  @Test fun `JavaScript prices are read and city profiles preserve separate cookie sessions`() {
    var seedCookie = true
    val automationMarkers = mutableListOf<Boolean>()
    val options = BrowserOptions(temporary.root.toPath(), headless = true)
    BrowserReader(options, createBrowserDriver()) { context ->
      if (seedCookie) {
        context.addCookies(listOf(Cookie("store", "chosen").setUrl("https://shop.ourmeal.test").setExpires(System.currentTimeMillis() / 1000.0 + 3600)))
        seedCookie = false
      }
      context.route("**/*") { route ->
        route.fulfill(com.microsoft.playwright.Route.FulfillOptions().setContentType("text/html").setBody(SPA))
      }
      fun watch(page: com.microsoft.playwright.Page) {
        page.onDOMContentLoaded { automationMarkers += page.evaluate("navigator.webdriver === true") as Boolean }
      }
      context.pages().forEach(::watch)
      context.onPage(::watch)
    }.use { reader ->
      val first = reader.read(city, listOf(chain), "leche").results.single()
      assertEquals(ReadStatus.OK, first.status)
      assertEquals(2.0, first.products.single().price, 0.0)
      assertEquals(1000.0, first.products.single().packTotal, 0.0)
      val persisted = reader.read(city, listOf(chain), "leche").results.single()
      assertEquals(2.0, persisted.products.single().price, 0.0)
      val anotherCity = reader.read(City(2, "Otra ciudad", null, 2), listOf(chain), "leche").results.single()
      assertEquals(1.0, anotherCity.products.single().price, 0.0)
      assertEquals(3, automationMarkers.size)
      assertTrue(automationMarkers.none { it })
    }
  }

  @Test fun `one unavailable shop does not hide another chains products`() {
    val other = Chain(22, "Otra cadena", listOf("https://another.ourmeal.test/"), emptyList())
    BrowserReader(BrowserOptions(temporary.root.toPath(), headless = true), createBrowserDriver()) { context ->
      context.route("**/*") { route ->
        if (route.request().url().contains("another.")) {
          route.fulfill(com.microsoft.playwright.Route.FulfillOptions().setStatus(403).setBody("Access denied"))
        } else {
          route.fulfill(com.microsoft.playwright.Route.FulfillOptions().setContentType("text/html").setBody(SPA))
        }
      }
    }.use { reader ->
      val results = reader.read(city, listOf(other, chain), "leche").results
      assertEquals(ReadStatus.INTERACTION_REQUIRED, results[0].status)
      assertTrue(results[0].products.isEmpty())
      assertEquals(ReadStatus.OK, results[1].status)
    }
  }

  @Test fun `selected chains must belong to the current city`() {
    BrowserReader(BrowserOptions(temporary.root.toPath(), headless = true)).use { reader ->
      assertThrows(IllegalArgumentException::class.java) { reader.read(city, listOf(chain), "leche", setOf(999)) }
      assertTrue(reader.read(city, listOf(chain), "leche", emptySet()).results.isEmpty())
      assertEquals(ReadStatus.NO_WEBSITE, reader.read(city, listOf(chain.copy(websites = emptyList())), "leche").results.single().status)
    }
  }

  @Test fun `a configured search route can be used when a site has no search input`() {
    val rules = listOf(SiteRule(chain.name, chain.websites.single(), "https://shop.ourmeal.test/search?q={query}"))
    BrowserReader(BrowserOptions(temporary.root.toPath(), headless = true, rules = rules), createBrowserDriver()) { context ->
      context.route("**/*") { route ->
        val body = if (route.request().url().contains("/search?q=leche")) {
          """<script type="application/ld+json">{"@type":"Product","name":"Leche 1 L","offers":{"price":1.5,"priceCurrency":"EUR"}}</script>"""
        } else "<main>Tienda</main>"
        route.fulfill(com.microsoft.playwright.Route.FulfillOptions().setContentType("text/html").setBody(body))
      }
    }.use { reader ->
      assertEquals(1.5, reader.read(city, listOf(chain), "leche").results.single().products.single().price, 0.0)
    }
  }

  companion object {
    private val SPA = """
      <!doctype html><html lang="es"><head>
      <script type="application/ld+json">{"@type":"Organization","name":"Tienda de prueba"}</script>
      </head><body><main><input type="search" aria-label="Buscar"><div id="results"></div></main>
      <script>
        document.querySelector('input').addEventListener('keydown', event => {
          if (event.key !== 'Enter') return;
          event.preventDefault();
          setTimeout(() => {
            const script = document.createElement('script');
            script.type = 'application/ld+json';
            script.textContent = JSON.stringify({
              '@type': 'Product', name: 'Leche entera 1 L', sku: 'milk',
              offers: { '@type': 'Offer', price: document.cookie.includes('store=chosen') ? 2 : 1, priceCurrency: 'EUR' }
            });
            document.querySelector('#results').append(script);
          }, 150);
        });
      </script></body></html>
    """.trimIndent()
  }
}
