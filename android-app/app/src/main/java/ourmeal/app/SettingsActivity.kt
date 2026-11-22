package ourmeal.app

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import app.ourmeat.R
import app.ourmeat.databinding.ActivitySettingsBinding
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ourmeal.app.controller.CityLocator
import ourmeal.app.controller.CityLookup
import ourmeal.app.controller.PriceResult
import ourmeal.app.controller.PriceService
import ourmeal.app.controller.PriceSettingsManager
import ourmeal.app.model.CityStores
import ourmeal.app.model.PriceChain
import ourmeal.app.model.PriceSettings
import java.io.IOException

/** App settings. Today: turning the price comparison on, the city and the supermarkets to compare. */
class SettingsActivity : AppCompatActivity() {
  private lateinit var binding: ActivitySettingsBinding
  private lateinit var settingsManager: PriceSettingsManager
  private lateinit var locator: CityLocator
  /** Null while loading or when the file cannot be read: nothing can be changed then. */
  private var settings: PriceSettings? = null
  private var service: PriceService? = null
  private var chains: ChainsState = ChainsState.NoCity
  private var storesJob: Job? = null
  private var locateJob: Job? = null
  private var isBindingSwitch = false
  /** Writes keep their order; each applies its change to what the file holds. */
  private val writes = Mutex()
  private var pendingWrites = 0
  private var retryStores: (() -> Unit)? = null
  /** What the supermarket rows were built from, so a tick does not rebuild them and lose focus. */
  private var shownChains: List<Any>? = null

  /** What the supermarkets block shows. */
  private sealed interface ChainsState {
    data object NoCity : ChainsState
    data class Loading(val city: String) : ChainsState
    data class Ready(val stores: CityStores) : ChainsState
    data class Failed(val offline: Boolean) : ChainsState
  }

  private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) locateCity() else showCityStatus(getString(R.string.prices_location_denied))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )
    binding = ActivitySettingsBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.root.applyScreenInsets()
    settingsManager = PriceSettingsManager(this)
    locator = CityLocator(this)

    binding.btnBack.setOnClickListener { finish() }
    binding.btnRetrySettings.setOnClickListener { loadSettings() }
    binding.switchPrices.setOnCheckedChangeListener { _, checked -> if (!isBindingSwitch) setEnabled(checked) }
    binding.btnLocate.setOnClickListener { askLocation() }
    binding.btnSearchCity.setOnClickListener { searchCity() }
    binding.btnRetryChains.setOnClickListener { retryStores?.invoke() }
    loadSettings()
  }

  private fun loadSettings() {
    binding.btnRetrySettings.isVisible = false
    binding.tvPricesStatus.isVisible = false
    lifecycleScope.launch {
      val loaded = withContext(Dispatchers.IO) {
        try { settingsManager.load() } catch (_: IOException) { null }
      }
      settings = loaded
      service = loaded?.let(PriceFeature::service)
      if (loaded == null) {
        render()
        binding.tvPricesStatus.text = getString(R.string.settings_error)
        binding.tvPricesStatus.setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.error))
        binding.tvPricesStatus.isVisible = true
        binding.btnRetrySettings.isVisible = true
        return@launch
      }
      chains = loaded.city?.let { ChainsState.Loading(it.name) } ?: ChainsState.NoCity
      render()
      if (loaded.enabled) refreshSavedCity()
    }
  }

  private fun render() {
    val current = settings
    val available = PriceFeature.isAvailable
    isBindingSwitch = true
    binding.switchPrices.isChecked = current?.enabled == true
    isBindingSwitch = false
    binding.switchPrices.isEnabled = current != null && available
    if (current != null && !available) {
      binding.tvPricesStatus.text = getString(R.string.prices_service_unavailable)
      binding.tvPricesStatus.setTextColor(ContextCompat.getColor(this, R.color.muted))
      binding.tvPricesStatus.isVisible = true
    }
    binding.pricesConfig.isVisible = current?.enabled == true && available
    if (!binding.pricesConfig.isVisible) return

    val city = current?.city
    binding.tvCity.text = when {
      city == null -> getString(R.string.prices_city_none)
      city.province != null && city.province != city.name -> getString(R.string.prices_city_value, city.name, city.province)
      else -> city.name
    }
    binding.tvCity.setTextColor(ContextCompat.getColor(this, if (city == null) R.color.muted else R.color.ink))
    binding.btnSearchCity.setText(if (city == null) R.string.prices_search_city else R.string.prices_change_city)
    val locating = locateJob?.isActive == true
    binding.btnLocate.isEnabled = !locating
    binding.btnSearchCity.isEnabled = !locating
    renderChains()
  }

  private fun renderChains() {
    val current = settings ?: return
    val state = chains
    binding.chainsLoading.isVisible = state is ChainsState.Loading
    if (state is ChainsState.Loading) binding.tvChainsLoading.text = getString(R.string.prices_chains_loading, state.city)
    binding.btnRetryChains.isVisible = state is ChainsState.Failed

    val (status, warning) = when (state) {
      ChainsState.NoCity -> getString(R.string.prices_chains_pick_city) to false
      is ChainsState.Loading -> null to false
      is ChainsState.Failed ->
        getString(if (state.offline) R.string.prices_chains_offline else R.string.prices_chains_error) to true
      is ChainsState.Ready -> when {
        state.stores.chains.isEmpty() -> getString(R.string.prices_chains_empty, state.stores.city.name) to false
        current.chains.isEmpty() -> getString(R.string.prices_chains_none_selected) to true
        else -> null to false
      }
    }
    binding.tvChainsStatus.isVisible = status != null
    binding.tvChainsStatus.text = status
    binding.tvChainsStatus.setTextColor(ContextCompat.getColor(this, if (warning) R.color.brand else R.color.muted))
    binding.tvAttribution.isVisible = state is ChainsState.Ready && state.stores.chains.isNotEmpty()
    if (state is ChainsState.Ready) binding.tvAttribution.text = state.stores.attribution

    // Offline, the chosen supermarkets are still listed, read-only, so the user knows what is compared.
    val rows: List<Pair<PriceChain, Int?>> = when (state) {
      is ChainsState.Ready -> state.stores.chains.map { it.chain to it.storeIds.size }
      is ChainsState.Failed -> current.chains.map { it to null }
      else -> emptyList()
    }
    val editable = state is ChainsState.Ready
    val source = listOf(state, rows)
    if (source != shownChains) {
      shownChains = source
      binding.chainsContainer.removeAllViews()
      rows.forEach { (chain, stores) -> addChainRow(chain, stores, editable) }
    }
    for (index in 0 until binding.chainsContainer.childCount) {
      val row = binding.chainsContainer.getChildAt(index) as MaterialCheckBox
      val chainId = row.tag as Int
      row.setOnCheckedChangeListener(null)
      row.isChecked = !editable || chainId in current.chainIds
      if (state is ChainsState.Ready) {
        row.setOnCheckedChangeListener { _, checked -> toggleChain(state.stores, chainId, checked) }
      }
    }
  }

  private fun addChainRow(chain: PriceChain, stores: Int?, editable: Boolean) {
    val row = LayoutInflater.from(this).inflate(R.layout.item_price_chain, binding.chainsContainer, false) as MaterialCheckBox
    row.tag = chain.id
    row.text = stores?.let { resources.getQuantityString(R.plurals.prices_chain_stores, it, chain.name, it) } ?: chain.name
    row.isEnabled = editable
    binding.chainsContainer.addView(row)
  }

  // CAMBIOS

  private fun setEnabled(enabled: Boolean) {
    if (!enabled) {
      storesJob?.cancel()
      locateJob?.cancel()
      locateJob = null
    }
    save({ it.copy(enabled = enabled) }) { saved ->
      if (saved.enabled && saved.city != null) refreshSavedCity()
      if (saved.enabled && saved.city == null) chains = ChainsState.NoCity
    }
  }

  private fun toggleChain(stores: CityStores, chainId: Int, checked: Boolean) {
    val current = settings ?: return
    val selected = if (checked) current.chainIds + chainId else current.chainIds - chainId
    save({ it.withChains(stores, selected) })
  }

  /**
   * Applies [change] on screen at once and saves it in order with any other change. If it cannot
   * be saved, the screen goes back to what the file holds. Writes finish even if the screen closes.
   */
  private fun save(change: (PriceSettings) -> PriceSettings, onSaved: (PriceSettings) -> Unit = {}) {
    val previous = settings ?: return
    settings = change(previous)
    pendingWrites++
    render()
    lifecycleScope.launch {
      val saved = withContext(NonCancellable) {
        writes.withLock { withContext(Dispatchers.IO) { settingsManager.update(change) } }
      }
      pendingWrites--
      if (isFinishing || isDestroyed) return@launch
      if (saved == null) {
        settings = withContext(Dispatchers.IO) { runCatching { settingsManager.load() }.getOrNull() } ?: previous
        render()
        binding.rootSettings.showSnackbar(getString(R.string.settings_save_error))
        return@launch
      }
      // With later changes still on their way, the screen already shows them.
      if (pendingWrites == 0) settings = saved
      onSaved(saved)
      render()
    }
  }

  // CIUDAD

  private fun refreshSavedCity() {
    val city = settings?.city ?: return
    loadStores(city.name, city.province, fromLocation = false)
  }

  /** Asks the supermarkets of a city by name; when the city is new it becomes the chosen one. */
  private fun loadStores(city: String, province: String?, fromLocation: Boolean) {
    if (settings?.enabled != true) return
    val client = service ?: return
    storesJob?.cancel()
    retryStores = { loadStores(city, province, fromLocation) }
    binding.tvCityStatus.isVisible = false
    chains = ChainsState.Loading(city)
    render()
    storesJob = lifecycleScope.launch {
      when (val result = client.stores(city, province)) {
        is PriceResult.Success -> {
          val stores = result.value
          chains = ChainsState.Ready(stores)
          save({ it.withCity(stores) })
          if (fromLocation) {
            binding.rootSettings.showSnackbar(
              message = getString(R.string.prices_city_detected, stores.city.name),
              actionLabel = getString(R.string.prices_change_city),
              onAction = ::searchCity
            )
          }
        }
        PriceResult.Offline -> {
          chains = ChainsState.Failed(offline = true)
          showCityStatus(getString(R.string.city_search_offline))
        }
        PriceResult.Unavailable -> {
          // A failed price or map service does not mean the selected city is unknown.
          chains = ChainsState.Failed(offline = false)
          showCityStatus(getString(R.string.prices_chains_error))
        }
      }
      render()
    }
  }

  private fun searchCity() {
    val client = service ?: return
    showCitySearch(client) { city -> loadStores(city.name, city.province, fromLocation = false) }
  }

  private fun askLocation() {
    if (locator.hasPermission()) return locateCity()
    ourmealDialog()
      .setTitle(R.string.prices_location_rationale_title)
      .setMessage(R.string.prices_location_rationale_body)
      .setNegativeButton(R.string.prices_search_city) { _, _ -> searchCity() }
      .setPositiveButton(R.string.prices_location_continue) { _, _ ->
        locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
      }
      .show()
  }

  private fun locateCity() {
    if (settings?.enabled != true) return
    locateJob?.cancel()
    locateJob = lifecycleScope.launch {
      showCityStatus(getString(R.string.prices_locating))
      render()
      val lookup = locator.locate()
      locateJob = null
      when (lookup) {
        is CityLookup.Found -> loadStores(lookup.name, lookup.province, fromLocation = true)
        CityLookup.NoPermission -> showCityStatus(getString(R.string.prices_location_denied))
        CityLookup.LocationOff -> showCityStatus(getString(R.string.prices_location_off))
        CityLookup.NotFound -> showCityStatus(getString(R.string.prices_location_not_found))
      }
      render()
    }
  }

  private fun showCityStatus(text: String) {
    binding.tvCityStatus.text = text
    binding.tvCityStatus.isVisible = true
  }
}
