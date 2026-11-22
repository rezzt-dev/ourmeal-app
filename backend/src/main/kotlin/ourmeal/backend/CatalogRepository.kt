package ourmeal.backend

import com.google.gson.Gson
import ourmeal.app.model.CandidateDto
import ourmeal.app.model.OfferDto
import ourmeal.app.model.ItemMode
import ourmeal.prices.CityCatalog
import ourmeal.prices.ProductPrice
import ourmeal.prices.shared.ProductText
import java.security.MessageDigest
import java.time.Instant
import java.sql.Timestamp

data class SearchHit(val candidate: CandidateDto, val normalized: String, val unitKind: String, val similarity: Double)

class CatalogRepository(private val db: Database) {
  private val gson = Gson()

  fun saveCity(catalog: CityCatalog) = db.transaction { connection ->
    require(catalog.chains.map { it.id }.distinct().size == catalog.chains.size) { "CHAIN_ID_COLLISION" }
    val stores = catalog.chains.flatMap { it.stores }
    require(stores.map { it.id }.distinct().size == stores.size) { "STORE_ID_COLLISION" }
    val updated = connection.execute("""INSERT INTO city_catalog(id,relation_id,name,province,catalog) VALUES(?,?,?,?,?::jsonb)
      ON CONFLICT(id) DO UPDATE SET name=excluded.name,province=excluded.province,catalog=excluded.catalog,refreshed_at=now()
      WHERE city_catalog.relation_id=excluded.relation_id""",
      catalog.city.id, catalog.city.relationId, catalog.city.name, catalog.city.province, gson.toJson(catalog))
    require(updated == 1) { "CITY_ID_COLLISION" }
    Unit
  }

  fun city(id: Int): CityCatalog? = db.source.connection.use { connection ->
    connection.rows("SELECT catalog::text FROM city_catalog WHERE id=?", id) { gson.fromJson(it.getString(1), CityCatalog::class.java) }.singleOrNull()
  }

  fun hasProducts(cityId: Int, chainIds: Set<Int>): Boolean = db.source.connection.use { connection ->
    connection.rows("SELECT EXISTS(SELECT 1 FROM chain_product WHERE city_id=? AND chain_id=ANY(?))",
      cityId, connection.createArrayOf("integer", chainIds.toTypedArray())) { it.getBoolean(1) }.single()
  }

  /** One transaction per observation. Historical prices are append-only; doubtful jumps are quarantined. */
  fun ingest(cityId: Int, product: ProductPrice) {
    val catalog = city(cityId) ?: error("Unknown city")
    require(catalog.chains.any { it.id == product.chainId }) { "Chain outside selected city" }
    db.transaction { connection ->
      require(product.price.isFinite() && product.price in 0.0001..9999.99 && product.packTotal.isFinite() && product.packTotal > 0)
      require(product.unitKind in setOf("mass", "volume", "count"))
      require(product.source in setOf("online", "open_prices", "user"))
      require(product.externalId.length in 1..1024 && product.name.length in 2..240 && (product.brand?.length ?: 0) <= 120)
      val origin = java.net.URI(product.url)
      require(origin.scheme == "https" && origin.host != null && origin.userInfo == null)
      val observed = Instant.parse(product.observedAt)
      require(!observed.isAfter(Instant.now().plusSeconds(300)))
      val core = ProductText.core(product.name, product.brand)
      require(core.isNotBlank() && core.length <= 240)
      val group = connection.rows("""INSERT INTO product_group(match_key,name,name_norm,unit_kind) VALUES(?,?,?,?)
        ON CONFLICT(match_key) DO UPDATE SET match_key=excluded.match_key RETURNING id""",
        ProductText.groupKey(product.name, product.brand, product.unitKind), ProductText.displayName(product.name, product.brand), core, product.unitKind) { it.getInt(1) }.single()
      // EAN alone cannot override conflicting variant, unit or pack metadata.
      val unknownBrand = if (product.brand.isNullOrBlank() && product.ean.isNullOrBlank()) "${product.chainId}:${product.externalId}" else ""
      val canonicalKey = listOf(group, ProductText.normalize(product.brand.orEmpty()), product.ean.orEmpty(), product.packTotal, unknownBrand).joinToString(":")
      val canonical = connection.rows("""INSERT INTO canonical_product(match_key,group_id,name,brand,ean,pack_total,unit_kind)
        VALUES(?,?,?,?,?,?,?) ON CONFLICT(match_key) DO UPDATE SET match_key=excluded.match_key RETURNING id""",
        canonicalKey, group, product.name, product.brand, product.ean, product.packTotal, product.unitKind) { it.getInt(1) }.single()
      val listing = connection.rows("""INSERT INTO chain_product(city_id,chain_id,external_id,canonical_id,name,url,last_seen_at)
        VALUES(?,?,?,?,?,?,?) ON CONFLICT(city_id,chain_id,external_id) DO UPDATE SET
        canonical_id=excluded.canonical_id,name=excluded.name,url=excluded.url,last_seen_at=excluded.last_seen_at
        WHERE chain_product.last_seen_at<=excluded.last_seen_at RETURNING id""",
        cityId, product.chainId, product.externalId, canonical, product.name, product.url, Timestamp.from(observed)) { it.getLong(1) }.singleOrNull()
        ?: return@transaction
      data class Previous(val price: Double, val observed: Instant, val canonical: Int, val pack: Double, val kind: String)
      val previous = connection.rows("""SELECT p.price::double precision,p.observed_at,p.canonical_id,cp.pack_total,cp.unit_kind
        FROM price p JOIN canonical_product cp ON cp.id=p.canonical_id
        WHERE p.chain_product_id=? ORDER BY p.observed_at DESC,p.id DESC LIMIT 1""", listing) {
        Previous(it.getDouble(1), it.getTimestamp(2).toInstant(), it.getInt(3), it.getDouble(4), it.getString(5))
      }.singleOrNull()
      if (previous != null && previous.price == product.price && previous.canonical == canonical && observed.epochSecond - previous.observed.epochSecond < 86_400) return@transaction
      val ratio = previous?.takeIf { it.kind == product.unitKind }?.let { (product.price / product.packTotal) / (it.price / it.pack) }
      val confidence = if (ratio != null && (ratio > 3 || ratio < 1.0 / 3)) 0.3 else 1.0
      connection.execute("INSERT INTO price(chain_product_id,canonical_id,price,confidence,observed_at,source) VALUES(?,?,?,?,?,?)",
        listing, canonical, product.price, confidence, Timestamp.from(observed), product.source)
      Unit
    }
  }

  fun search(cityId: Int, chainIds: Set<Int>, query: String, mode: ItemMode): List<SearchHit> = db.source.connection.use { connection ->
    val branded = mode == ItemMode.BRAND
    val name = if (branded) "cp.name" else "g.name"
    val sql = """SELECT DISTINCT ${if (branded) "cp.id" else "g.id"} id,g.id group_id,$name name,
      ${if (branded) "cp.brand" else "NULL::text"} brand,g.unit_kind,
      similarity(?,${if (branded) "lower(cp.name)" else "g.name_norm"}) sim
      FROM product_group g JOIN canonical_product cp ON cp.group_id=g.id JOIN chain_product chp ON chp.canonical_id=cp.id
      WHERE chp.city_id=? AND chp.chain_id=ANY(?) AND chp.last_seen_at>now()-interval '14 days'
      AND (g.search_tsv @@ websearch_to_tsquery('spanish',?) OR similarity(?,g.name_norm)>0.15 OR lower(cp.name) LIKE ?)
      ORDER BY sim DESC LIMIT 30"""
    val norm = ProductText.normalize(query)
    connection.rows(sql, norm, cityId, connection.createArrayOf("integer", chainIds.toTypedArray()), norm, norm, "%${norm.replace("%", "").replace("_", "")}%") { row ->
      SearchHit(CandidateDto(row.getInt("group_id"), if (branded) row.getInt("id") else null, row.getString("name"), row.getString("brand"), null),
        ProductText.normalize(row.getString("name")), row.getString("unit_kind"), row.getDouble("sim"))
    }
  }

  fun candidate(key: String): CandidateDto? = db.source.connection.use { connection ->
    val id = key.substringAfter(':').toIntOrNull() ?: return@use null
    if (key.startsWith("g:")) connection.rows("SELECT id,name FROM product_group WHERE id=?", id) { CandidateDto(it.getInt(1), null, it.getString(2), null, 1.0) }.singleOrNull()
    else if (key.startsWith("c:")) connection.rows("SELECT id,group_id,name,brand FROM canonical_product WHERE id=?", id) { CandidateDto(it.getInt(2), it.getInt(1), it.getString(3), it.getString(4), 1.0) }.singleOrNull()
    else null
  }

  fun brands(cityId: Int, chainIds: Set<Int>): List<String> = db.source.connection.use { connection ->
    connection.rows("""SELECT DISTINCT cp.brand FROM canonical_product cp JOIN chain_product chp ON chp.canonical_id=cp.id
      WHERE chp.city_id=? AND chp.chain_id=ANY(?) AND cp.brand IS NOT NULL""",
      cityId, connection.createArrayOf("integer", chainIds.toTypedArray())) { it.getString(1) }
  }

  fun offers(cityId: Int, chainIds: Set<Int>, groupId: Int?, canonicalId: Int?): List<OfferDto> = db.source.connection.use { connection ->
    val sql = """SELECT chp.chain_id,chp.name,cp.pack_total,cp.unit_kind,p.price,p.source,
      greatest(p.observed_at,chp.last_seen_at) observed_at
      FROM canonical_product cp JOIN chain_product chp ON chp.canonical_id=cp.id
      JOIN LATERAL(SELECT * FROM price WHERE chain_product_id=chp.id ORDER BY observed_at DESC,id DESC LIMIT 1) p ON true
      WHERE chp.city_id=? AND chp.chain_id=ANY(?) AND ${if (canonicalId != null) "cp.id" else "cp.group_id"}=?
      AND p.confidence>=0.5 AND p.canonical_id=cp.id AND chp.last_seen_at>now()-interval '14 days'"""
    connection.rows(sql, cityId, connection.createArrayOf("integer", chainIds.toTypedArray()), canonicalId ?: groupId) { row ->
      val pack = row.getDouble("pack_total"); val price = row.getDouble("price"); val kind = row.getString("unit_kind")
      OfferDto(row.getInt("chain_id"), row.getString("name"), price, price / if (kind == "count") pack else pack / 1000,
        kind, pack, false, row.getTimestamp("observed_at").toInstant().toString(), row.getString("source"))
    }.groupBy { it.chainId }.values.map { prices ->
      val fresh = prices.filter { Instant.parse(it.observedAt).isAfter(Instant.now().minusSeconds(7 * 86400)) }
      fresh.ifEmpty { prices }.minWith(compareBy<OfferDto> { it.unitPrice }.thenBy { it.price })
    }.sortedBy { it.unitPrice }
  }

  fun alias(query: String, target: String, installation: String) {
    require(candidate(target) != null)
    db.transaction { connection ->
      connection.execute("""INSERT INTO alias_vote(query_hash,target_key,installation_hash) VALUES(?,?,?)
        ON CONFLICT(query_hash,installation_hash) DO UPDATE SET target_key=excluded.target_key,updated_at=now()""",
        hash(ProductText.normalize(query)), target, hash(installation))
      Unit
    }
  }

  fun confidentAlias(query: String): CandidateDto? {
    val targets = db.source.connection.use { connection -> connection.rows("""SELECT target_key FROM alias_vote WHERE query_hash=?
      GROUP BY target_key HAVING count(*)>=2 ORDER BY count(*) DESC LIMIT 2""", hash(ProductText.normalize(query))) { it.getString(1) }
    }
    return if (targets.size == 1) candidate(targets.single()) else null
  }
}

internal fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
