package ourmeal.backend

import ourmeal.prices.BrowserOptions
import ourmeal.prices.BrowserReader
import ourmeal.prices.CityCatalog
import ourmeal.prices.ChainRead
import ourmeal.prices.ReadStatus
import ourmeal.prices.shared.ProductText
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

interface PriceSource : AutoCloseable {
  fun read(city: CityCatalog, chainId: Int, query: String): ChainRead
  override fun close() {}
}

class BrowserSource(options: BrowserOptions) : PriceSource {
  // Construct and use this instance only inside its ingestion worker.
  private val browser = BrowserReader(options)
  override fun read(city: CityCatalog, chainId: Int, query: String): ChainRead =
    browser.read(city.city, city.chains, query, setOf(chainId)).results.single()
  override fun close() = browser.close()
}

/** Bounded background queue. API requests never wait for page navigation or own a Playwright object. */
class Ingestion(
  private val catalog: CatalogRepository,
  private val sourceFactory: () -> PriceSource,
  val enabled: Boolean,
  workers: Int = 2
) : AutoCloseable {
  private data class Work(val key: String, val city: CityCatalog, val chainId: Int, val query: String)
  private data class State(val pending: Boolean, val expiresAt: Long = 0)
  private data class Circuit(val failures: Int = 0, val resumeAt: Long = 0, val status: String = "UNREAD")
  private val queue = ArrayBlockingQueue<Work>(64)
  private val states = ConcurrentHashMap<String, State>()
  private val circuits = ConcurrentHashMap<String, Circuit>()
  private val chainLocks = ConcurrentHashMap<String, ReentrantLock>()
  private val running = AtomicBoolean(true)
  private val executor = Executors.newFixedThreadPool(workers.coerceIn(1, 4)) { action -> Thread(action, "price-ingestion").apply { isDaemon = true } }

  init {
    if (enabled) repeat(workers.coerceIn(1, 4)) { executor.execute { worker() } }
  }

  /** True while there is pending work for these exact city, chains and product. */
  @Synchronized
  fun ensure(city: CityCatalog, chainIds: Set<Int>, query: String): Boolean {
    if (!enabled || chainIds.isEmpty()) return false
    val now = System.currentTimeMillis()
    states.entries.removeIf { !it.value.pending && now > it.value.expiresAt }
    require(states.size < 10_000) { "INGESTION_BUSY" }
    var pending = false
    for (chain in city.chains.filter { it.id in chainIds }) {
      val circuitKey = "${city.city.id}:${chain.id}"
      if ((circuits[circuitKey]?.resumeAt ?: 0) > now) continue
      val key = "$circuitKey:${hash(ProductText.normalize(query))}"
      val previous = states[key]
      if (previous != null) { pending = pending || previous.pending; continue }
      states[key] = State(true)
      if (queue.offer(Work(key, city, chain.id, query))) pending = true
      else { states.remove(key); throw IllegalStateException("INGESTION_BUSY") }
    }
    return pending
  }

  fun health(): Map<String, Any> = mapOf("enabled" to enabled, "queued" to queue.size, "sources" to circuits.mapValues { it.value.status })

  fun allUnavailable(cityId: Int, chainIds: Collection<Int>): Boolean = enabled && chainIds.isNotEmpty() && chainIds.all { id ->
    circuits["$cityId:$id"]?.status?.let { it !in setOf("OK", "EMPTY", "UNREAD") } == true
  }

  private fun worker() {
    var source: PriceSource? = null
    try {
      while (running.get() && !Thread.currentThread().isInterrupted) {
        val work = queue.take()
        val circuitKey = "${work.city.city.id}:${work.chainId}"
        val lock = chainLocks.computeIfAbsent(circuitKey) { ReentrantLock() }
        var locked = false
        var cacheMs = 30_000L
        try {
          lock.lockInterruptibly()
          locked = true
          if ((circuits[circuitKey]?.resumeAt ?: 0) > System.currentTimeMillis()) continue
          val active = source ?: sourceFactory().also { source = it }
          val result = active.read(work.city, work.chainId, work.query)
          require(result.chainId == work.chainId)
          result.products.forEach { product ->
            require(product.chainId == work.chainId)
            catalog.ingest(work.city.city.id, product)
          }
          val successful = result.status in setOf(ReadStatus.OK, ReadStatus.EMPTY)
          val failures = if (successful) 0 else (circuits[circuitKey]?.failures ?: 0) + 1
          val paused = result.status == ReadStatus.INTERACTION_REQUIRED || failures >= 5
          cacheMs = if (successful) Duration.ofHours(6).toMillis() else 30_000L
          circuits[circuitKey] = Circuit(failures, if (paused) System.currentTimeMillis() + 30 * 60_000 else 0, result.status.name)
        } catch (_: Exception) {
          val failures = (circuits[circuitKey]?.failures ?: 0) + 1
          circuits[circuitKey] = Circuit(failures, if (failures >= 5) System.currentTimeMillis() + 30 * 60_000 else 0, "UNAVAILABLE")
        } finally {
          if (locked) lock.unlock()
          states[work.key] = State(false, System.currentTimeMillis() + cacheMs)
        }
      }
    } catch (_: InterruptedException) {
      Thread.currentThread().interrupt()
    } finally { source?.close() }
  }

  override fun close() {
    running.set(false)
    executor.shutdownNow()
  }
}
