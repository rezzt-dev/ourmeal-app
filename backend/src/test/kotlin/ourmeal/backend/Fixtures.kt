package ourmeal.backend

import ourmeal.prices.Chain
import ourmeal.prices.ChainRead
import ourmeal.prices.City
import ourmeal.prices.CityCatalog
import ourmeal.prices.ProductPrice
import ourmeal.prices.ReadStatus
import ourmeal.prices.Store
import java.time.Instant

/** Synthetic data is confined to test sources and never included in the production distribution. */
object Fixtures {
  val city = CityCatalog(City(7, "Sevilla", "Sevilla", 7007), listOf(
    Chain(1, "Cadena A", emptyList(), listOf(Store(10, 1, "Cadena A", "Tienda A"))),
    Chain(2, "Cadena B", emptyList(), listOf(Store(20, 2, "Cadena B", "Tienda B"))),
    Chain(3, "Cadena C", emptyList(), listOf(Store(30, 3, "Cadena C", "Tienda C")))
  ))

  fun product(chain: Int, name: String = "Arroz redondo", price: Double = if (chain == 1) 0.99 else 1.79,
              pack: Double = if (chain == 1) 500.0 else 1000.0, kind: String = "mass",
              observedAt: Instant = Instant.now(), id: String = "rice-$chain"): ProductPrice =
    ProductPrice(id, chain, "$name Marca $chain ${pack.toInt()} ${if (kind == "volume") "ml" else if (kind == "mass") "g" else "ud"}",
      price, kind, pack, -10.0, observedAt.toString(), "https://fixture.example/$id", brand = "Marca $chain")

  class Source : PriceSource {
    override fun read(city: CityCatalog, chainId: Int, query: String): ChainRead = ChainRead(chainId,
      city.chains.single { it.id == chainId }.name, ReadStatus.OK, listOf(product(chainId)))
  }

  class LocationsFixture : Locations {
    override fun cities(query: String): List<City> = listOf(city.city).filter { it.name.contains(query, true) }
    override fun stores(name: String, province: String?): CityCatalog {
      require(name.equals(city.city.name, true))
      return city
    }
  }
}
