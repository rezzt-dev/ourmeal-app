package ourmeal.backend

import ourmeal.app.model.matchKey
import ourmeal.prices.City
import ourmeal.prices.CityCatalog
import ourmeal.prices.Discovery

interface Locations {
  fun cities(query: String): List<City>
  fun stores(name: String, province: String?): CityCatalog
}

class OsmLocations(private val discovery: Discovery, private val catalog: CatalogRepository) : Locations {
  override fun cities(query: String): List<City> = discovery.cities(query)

  override fun stores(name: String, province: String?): CityCatalog {
    val matches = cities(name).filter { matchKey(it.name) == matchKey(name) }
      .filter { province == null || matchKey(it.province.orEmpty()) == matchKey(province) }
    require(matches.size == 1) { "CITY_UNKNOWN_OR_AMBIGUOUS" }
    return discovery.stores(matches.single()).also(catalog::saveCity)
  }
}
