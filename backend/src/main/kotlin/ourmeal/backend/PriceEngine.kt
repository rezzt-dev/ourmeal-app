package ourmeal.backend

import ourmeal.app.model.CandidateDto
import ourmeal.app.model.CompareRequest
import ourmeal.app.model.CompareResponse
import ourmeal.app.model.ItemComparisonDto
import ourmeal.app.model.ItemMode
import ourmeal.app.model.ResolveRequest
import ourmeal.app.model.ResolveResponse
import ourmeal.prices.CityCatalog
import ourmeal.prices.shared.ProductText
import java.time.Instant

class PriceEngine(val catalog: CatalogRepository, private val ingestion: Ingestion) {
  fun scope(cityId: Int?, chainIds: Collection<Int>): CityCatalog {
    require(cityId != null && chainIds.isNotEmpty() && chainIds.size <= 200) { "CITY_AND_CHAINS_REQUIRED" }
    val city = catalog.city(cityId) ?: throw IllegalArgumentException("CITY_UNKNOWN")
    require(chainIds.all { id -> city.chains.any { it.id == id } }) { "CHAIN_OUTSIDE_CITY" }
    return city
  }

  fun resolve(request: ResolveRequest): ResolveResponse {
    require(request.query.length in 2..120) { "INVALID_QUERY" }
    val city = scope(request.cityId, request.chainIds)
    val requestedBrand = catalog.brands(city.city.id, request.chainIds.toSet()).sortedByDescending { it.length }
      .firstOrNull { " ${ProductText.normalize(it)} " in " ${ProductText.normalize(request.query)} " }
    val mode = if (requestedBrand != null) ItemMode.BRAND else request.mode
    val requestedPack = ProductText.quantity(request.query)
    val available = catalog.search(city.city.id, request.chainIds.toSet(), request.query, mode)
      .filter { ProductText.compatible(request.query, it.normalized) }
      .filter { requestedBrand == null || ProductText.normalize(it.candidate.brand.orEmpty()) == ProductText.normalize(requestedBrand) }
      .filter { hit ->
        requestedPack == null || (requestedPack.kind == hit.unitKind && (mode != ItemMode.BRAND ||
          ProductText.quantity(hit.normalized)?.let { kotlin.math.abs(it.total - requestedPack.total) / requestedPack.total <= 0.05 } == true))
      }
    val covered = available.takeIf { it.size == 1 }?.single()?.candidate?.let {
      catalog.offers(city.city.id, request.chainIds.toSet(), it.groupId, it.canonicalId)
        .filter { offer -> Instant.parse(offer.observedAt).isAfter(Instant.now().minusSeconds(6 * 3600)) }.mapNotNull { offer -> offer.chainId }.toSet()
    }.orEmpty()
    val pending = ingestion.ensure(city, request.chainIds.toSet() - covered, request.query)
    // Finish the selected sources before claiming a winning supermarket from a partial first response.
    if (pending) return ResolveResponse("PROCESSING", emptyList())
    if (available.isEmpty() && (ingestion.allUnavailable(city.city.id, request.chainIds) ||
      (!ingestion.enabled && !catalog.hasProducts(city.city.id, request.chainIds.toSet())))) {
      throw IllegalStateException("PRICE_SOURCE_UNAVAILABLE")
    }
    val query = ProductText.core(request.query)
    val hits = available
      .map { hit ->
        val terms = query.split(' ').filter { it.isNotBlank() }.toSet()
        val target = ProductText.core(hit.normalized).split(' ').toSet()
        val coverage = if (terms.isEmpty()) 0.0 else terms.intersect(target).size.toDouble() / terms.size
        hit to (0.75 * coverage + 0.25 * hit.similarity).coerceIn(0.0, 1.0)
      }.filter { it.second >= 0.42 }.sortedByDescending { it.second }
    val exact = hits.filter { ProductText.core(it.first.normalized).split(' ').toSet() == query.split(' ').toSet() }
    val alias = catalog.confidentAlias(request.query)?.takeIf { proposed ->
      hits.any { sameTarget(it.first.candidate, proposed) }
    }
    val candidates = hits.take(3).map { it.first.candidate.copy(score = it.second) }
    return when {
      alias != null -> ResolveResponse("RESOLVED", listOf(alias))
      exact.size == 1 -> ResolveResponse("RESOLVED", listOf(exact.single().first.candidate.copy(score = 1.0)))
      candidates.isNotEmpty() -> ResolveResponse("CHOOSE", candidates)
      else -> ResolveResponse("REFINE", emptyList())
    }
  }

  fun compare(request: CompareRequest): CompareResponse {
    require(request.items.size in 1..40 && request.items.map { it.id }.distinct().size == request.items.size) { "INVALID_ITEMS" }
    val city = scope(request.cityId, request.chainIds)
    val allowedStores = city.chains.filter { it.id in request.chainIds }.flatMap { it.stores }.map { it.id }.toSet()
    require(request.storeIds.isNotEmpty() && request.storeIds.all { it in allowedStores }) { "STORE_OUTSIDE_CITY_OR_CHAIN" }
    var pending = false
    val items = request.items.map { item ->
      require(item.id.length in 1..80 && item.quantity in 1..1000) { "INVALID_ITEM" }
      require((item.mode == ItemMode.BRAND && item.canonicalId != null) ||
        (item.mode == ItemMode.GENERIC && item.groupId != null && item.canonicalId == null)) { "INVALID_TARGET" }
      val key = item.canonicalId?.let { "c:$it" } ?: "g:${item.groupId}"
      val target = catalog.candidate(key)
      val offers = if (target == null) emptyList() else catalog.offers(city.city.id, request.chainIds.toSet(), item.groupId, item.canonicalId)
      val covered = offers.filter { Instant.parse(it.observedAt).isAfter(Instant.now().minusSeconds(6 * 3600)) }.mapNotNull { it.chainId }.toSet()
      if (target != null) pending = ingestion.ensure(city, request.chainIds.toSet() - covered, target.name.orEmpty()) || pending
      ItemComparisonDto(item.id, offers)
    }
    return CompareResponse(items, pending)
  }

  private fun sameTarget(a: CandidateDto, b: CandidateDto): Boolean =
    if (a.canonicalId != null || b.canonicalId != null) a.canonicalId == b.canonicalId else a.groupId == b.groupId
}
