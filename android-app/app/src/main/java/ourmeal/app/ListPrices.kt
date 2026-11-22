package ourmeal.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import ourmeal.app.controller.PriceCacheManager
import ourmeal.app.controller.PriceResult
import ourmeal.app.controller.PriceService
import ourmeal.app.controller.ProductAliasManager
import ourmeal.app.model.CachedComparison
import ourmeal.app.model.ItemPriceState
import ourmeal.app.model.PriceCache
import ourmeal.app.model.PriceCandidate
import ourmeal.app.model.PriceSettings
import ourmeal.app.model.PriceTarget
import ourmeal.app.model.ProductAlias
import ourmeal.app.model.ProductAliases
import ourmeal.app.model.Resolution
import ourmeal.app.model.compareOffers
import ourmeal.app.model.matchKey

/** Whether the last attempt to reach the price service worked. */
internal enum class PriceConnection { OK, OFFLINE, FAILED }

/**
 * Works out, for the pending products of one list, the chosen supermarket where each one is
 * cheapest per kilo, litre or unit.
 *
 * Product names are resolved once and remembered in `product_aliases.json`; prices are kept in
 * `price_cache.json` and shown at once, then refreshed when older than six hours. Files are read
 * and written off the main thread; [onChange] is always called on the main thread.
 */
internal class ListPrices(
  private val scope: CoroutineScope,
  private val aliasManager: ProductAliasManager,
  private val cacheManager: PriceCacheManager,
  private val onChange: () -> Unit
) {
  private var service: PriceService? = null
  var settings: PriceSettings? = null
    private set
  /** Product key to the name as written in the list. */
  private var names: Map<String, String> = emptyMap()
  private val states = mutableMapOf<String, ItemPriceState>()
  /** Doubtful resolutions of this session, so the service is not asked again on every change. */
  private val doubts = mutableMapOf<String, Resolution>()
  /** Null until loaded. A damaged alias file is used empty and never written. */
  private var aliases: ProductAliases? = null
  private var aliasesWritable = true
  private var cache: PriceCache? = null
  private var job: Job? = null
  private var retryAfter = 0L
  /** The worst failure of the refresh in progress. */
  private var problem = PriceConnection.OK
  private var processing = false
  private val lastResolved = mutableMapOf<String, Long>()

  var connection: PriceConnection = PriceConnection.OK
    private set

  val isActive: Boolean get() = service != null && settings?.isReady == true

  fun stateFor(name: String): ItemPriceState? = states[matchKey(name)]

  /** Starts comparing with [settings]; prices of another city or set of supermarkets are dropped. */
  fun start(service: PriceService, settings: PriceSettings) {
    val changed = this.settings?.signature != settings.signature
    this.service = service
    this.settings = settings
    if (changed) {
      job?.cancel()
      states.clear()
      retryAfter = 0L
      connection = PriceConnection.OK
      doubts.clear()
      lastResolved.clear()
    }
  }

  fun stop() {
    job?.cancel()
    service = null
    settings = null
    states.clear()
    names = emptyMap()
    onChange()
  }

  /** Brings the states in line with the [pending] product names; only new names reach the service. */
  fun update(pending: List<String>, force: Boolean = false) {
    if (!isActive) return
    names = pending.associateBy(::matchKey)
    states.keys.retainAll(names.keys)
    if (force) retryAfter = 0L
    job?.cancel()
    job = scope.launch {
      var attempt = 0
      var sourcePolls = 0
      while (isActive && names.isNotEmpty()) {
        processing = false
        refresh(polling = attempt > 0, force = force && attempt == 0)
        attempt++
        if (processing) {
          if (++sourcePolls >= MAX_POLL_ATTEMPTS) break
          delay(POLL_INTERVAL_MS)
        } else if (connection != PriceConnection.OK) {
          // Retry while this screen is visible, without a new permission or a background service.
          delay(RETRY_PAUSE_MS)
        } else return@launch
      }
      // Never leave an endless spinner after a source stalls. The list itself remains editable.
      states.entries.forEach { if (it.value is ItemPriceState.Loading) it.setValue(ItemPriceState.Failed) }
      connection = PriceConnection.FAILED
      onChange()
    }
  }

  /** The user picked what the product is: remembered here and taught to the service. */
  fun choose(name: String, candidate: PriceCandidate) {
    val key = matchKey(name)
    doubts.remove(key)
    remember(ProductAlias(key, name, candidate.target, ignored = false, updatedAt = System.currentTimeMillis()))
    service?.let { client -> scope.launch { client.sendAlias(name, candidate.target) } }
    states[key] = ItemPriceState.Loading
    onChange()
    update(names.values.toList(), force = true)
  }

  /** The user prefers not to compare this product. */
  fun skip(name: String) {
    val key = matchKey(name)
    doubts.remove(key)
    remember(ProductAlias(key, name, null, ignored = true, updatedAt = System.currentTimeMillis()))
    states[key] = ItemPriceState.Skipped
    onChange()
  }

  /** Forgets what the product was resolved to and asks the service again. */
  fun reset(name: String) {
    val key = matchKey(name)
    doubts.remove(key)
    aliases = aliases?.remove(key)
    if (aliasesWritable) persist { aliasManager.update { it.remove(key) } }
    states[key] = ItemPriceState.Loading
    onChange()
    update(names.values.toList(), force = true)
  }

  private suspend fun refresh(polling: Boolean, force: Boolean) {
    val settings = settings ?: return
    val service = service ?: return
    val now = System.currentTimeMillis()
    val known = loadAliases()
    val saved = cache ?: withContext(Dispatchers.IO) { cacheManager.load() }.also { cache = it }

    val targets = mutableMapOf<String, PriceTarget>()
    val unresolved = mutableListOf<String>()
    names.keys.forEach { key ->
      val alias = known.find(key)
      val doubt = doubts[key]
      when {
        alias?.ignored == true -> states[key] = ItemPriceState.Skipped
        alias?.target != null -> alias.target?.let { targets[key] = it }
        doubt != null -> states[key] = doubt.toState(names.getValue(key))
        else -> unresolved += key
      }
    }
    val stale = targets.filter { (key, target) -> showSaved(key, target, saved, settings, now) || force }.values.toMutableList()

    val online = android.os.SystemClock.elapsedRealtime() >= retryAfter
    if (!online) {
      settleWithoutService(unresolved, stale)
      onChange()
      return
    }
    unresolved.forEach { key ->
      if (!polling || states[key] == null) states[key] = ItemPriceState.Loading
    }
    onChange()
    problem = PriceConnection.OK

    val toResolve = if (polling) unresolved.sortedBy { lastResolved[it] ?: 0L }.take(RESOLVE_PARALLELISM) else unresolved
    if (toResolve.size < unresolved.size) processing = true
    resolve(service, toResolve).forEach { (key, target) ->
      targets[key] = target
      if (showSaved(key, target, saved, settings, now)) stale += target
    }
    onChange()
    compare(service, settings, targets, stale.distinctBy { it.key })
    connection = problem
    onChange()
  }

  /** Resolves [keys] a few at a time and returns the ones the service was sure about. */
  private suspend fun resolve(service: PriceService, keys: List<String>): Map<String, PriceTarget> {
    if (keys.isEmpty()) return emptyMap()
    val permits = Semaphore(RESOLVE_PARALLELISM)
    val results = coroutineScope {
      keys.map { key -> async { permits.withPermit { key to service.resolve(names.getValue(key), settings = settings) } } }.awaitAll()
    }
    val learned = mutableListOf<ProductAlias>()
    val resolved = mutableMapOf<String, PriceTarget>()
    val at = System.currentTimeMillis()
    results.forEach { (key, result) ->
      lastResolved[key] = at
      val name = names.getValue(key)
      when (result) {
        is PriceResult.Success -> when (val resolution = result.value) {
          Resolution.Pending -> {
            processing = true
            states[key] = ItemPriceState.Loading
          }
          is Resolution.Resolved -> {
            resolved[key] = resolution.target
            learned += ProductAlias(key, name, resolution.target, ignored = false, updatedAt = at)
          }
          else -> {
            doubts[key] = resolution
            states[key] = resolution.toState(name)
          }
        }
        PriceResult.Offline -> failed(key, PriceConnection.OFFLINE)
        PriceResult.Unavailable -> failed(key, PriceConnection.FAILED)
      }
    }
    if (learned.isNotEmpty()) {
      learned.forEach(::rememberLocally)
      persistAliases(learned)
    }
    return resolved
  }

  /** Asks prices for [stale] targets and shows them on every product resolved to each target. */
  private suspend fun compare(
    service: PriceService,
    settings: PriceSettings,
    targets: Map<String, PriceTarget>,
    stale: List<PriceTarget>
  ) {
    if (stale.isEmpty()) return
    val received = mutableListOf<CachedComparison>()
    stale.chunked(PriceService.MAX_COMPARE_ITEMS).forEach { chunk ->
      val result = service.compare(chunk, settings.chainIds, settings.storeIds, settings.city?.id)
      val now = System.currentTimeMillis()
      chunk.forEach { target ->
        val keys = targets.filterValues { it.key == target.key }.keys
        when (result) {
          is PriceResult.Success -> {
            val pending = result.value.pending
            processing = processing || pending
            val offers = result.value[target.key].orEmpty()
            val fetchedAt = if (pending) 0L else now
            received += CachedComparison(target.key, settings.signature, offers, fetchedAt)
            keys.forEach {
              states[it] = if (pending && offers.isEmpty()) ItemPriceState.Loading
              else ItemPriceState.Compared(target, compareOffers(offers, settings.chainIds, now), now, refreshing = pending)
            }
          }
          PriceResult.Offline -> keys.forEach { failed(it, PriceConnection.OFFLINE) }
          PriceResult.Unavailable -> keys.forEach { failed(it, PriceConnection.FAILED) }
        }
      }
    }
    if (received.isNotEmpty()) {
      cache = cache?.upsert(received)
      persist { cacheManager.update { it.upsert(received) } }
    }
  }

  /** Shows the saved prices of [target] for [key]; true when they must be asked again. */
  private fun showSaved(key: String, target: PriceTarget, saved: PriceCache, settings: PriceSettings, now: Long): Boolean {
    val entry = saved.find(target.key, settings.signature)
    val current = states[key]
    if (entry == null) {
      if (current !is ItemPriceState.Compared || current.target.key != target.key) states[key] = ItemPriceState.Loading
      return true
    }
    val expired = entry.isExpired(now)
    states[key] = ItemPriceState.Compared(
      target, compareOffers(entry.offers, settings.chainIds, now), entry.fetchedAt, refreshing = expired
    )
    return expired
  }

  /** Inside the retry pause nothing is asked: saved prices stay and the rest shows the last failure. */
  private fun settleWithoutService(unresolved: List<String>, stale: List<PriceTarget>) {
    val failure = if (connection == PriceConnection.FAILED) ItemPriceState.Failed else ItemPriceState.Offline
    unresolved.forEach { states[it] = failure }
    val staleKeys = stale.mapTo(mutableSetOf()) { it.key }
    states.entries.forEach { entry ->
      val state = entry.value
      when {
        state is ItemPriceState.Compared && state.target.key in staleKeys -> entry.setValue(state.copy(refreshing = false))
        state is ItemPriceState.Loading -> entry.setValue(failure)
      }
    }
  }

  /** Keeps saved prices on screen; without them the product shows the failure. Pauses retries. */
  private fun failed(key: String, kind: PriceConnection) {
    // No connection explains any other failure in the same refresh, so it is the one reported.
    if (problem != PriceConnection.OFFLINE) problem = kind
    retryAfter = android.os.SystemClock.elapsedRealtime() + RETRY_PAUSE_MS
    val current = states[key]
    states[key] = if (current is ItemPriceState.Compared) current.copy(refreshing = false)
    else if (kind == PriceConnection.OFFLINE) ItemPriceState.Offline else ItemPriceState.Failed
  }

  private suspend fun loadAliases(): ProductAliases {
    aliases?.let { return it }
    val loaded = withContext(Dispatchers.IO) { runCatching { aliasManager.load() }.getOrNull() }
    aliasesWritable = loaded != null
    return (loaded ?: ProductAliases()).also { aliases = it }
  }

  private fun remember(alias: ProductAlias) {
    rememberLocally(alias)
    persistAliases(listOf(alias))
  }

  private fun rememberLocally(alias: ProductAlias) {
    aliases = (aliases ?: ProductAliases()).upsert(listOf(alias))
  }

  private fun persistAliases(changes: List<ProductAlias>) {
    if (aliasesWritable) persist { aliasManager.update { it.upsert(changes) } }
  }

  /** Writes finish even if the screen goes away or a newer refresh cancels this one. */
  private fun persist(write: () -> Any?) {
    scope.launch(NonCancellable + Dispatchers.IO) { write() }
  }

  private fun Resolution.toState(name: String): ItemPriceState = when (this) {
    Resolution.Pending -> ItemPriceState.Loading
    is Resolution.Choose -> ItemPriceState.NeedsChoice(name, options)
    is Resolution.Refine -> ItemPriceState.NotFound(name, options)
    is Resolution.Resolved -> ItemPriceState.Loading
  }

  private companion object {
    const val RESOLVE_PARALLELISM = 4
    const val POLL_INTERVAL_MS = 15_000L
    const val MAX_POLL_ATTEMPTS = 13
    /** After a failure the service is not asked again for this long, unless the user retries. */
    const val RETRY_PAUSE_MS = 30_000L
  }
}
