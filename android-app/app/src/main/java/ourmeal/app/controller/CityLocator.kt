package ourmeal.app.controller

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import ourmeal.app.model.PRICE_LOCALE
import ourmeal.app.model.cleanServiceText
import kotlin.coroutines.resume

/** How the search for the user's city ended. */
sealed interface CityLookup {
  data class Found(val name: String, val province: String?) : CityLookup
  data object NoPermission : CityLookup
  data object LocationOff : CityLookup
  /** No position, or no city name for it (no geocoder, no connection). */
  data object NotFound : CityLookup
}

/**
 * Names the user's city from an approximate position. The position stays on the device: it is
 * neither stored nor sent, only the city name leaves this class.
 */
class CityLocator(context: Context) {
  private val appContext = context.applicationContext
  private val locations = appContext.getSystemService(LocationManager::class.java)

  fun hasPermission(): Boolean =
    appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

  suspend fun locate(): CityLookup {
    if (!hasPermission()) return CityLookup.NoPermission
    val manager = locations ?: return CityLookup.NotFound
    if (!manager.isLocationEnabled) return CityLookup.LocationOff
    val position = try {
      currentPosition(manager) ?: lastPosition(manager)
    } catch (_: SecurityException) {
      return CityLookup.NoPermission
    } ?: return CityLookup.NotFound
    val address = cityOf(position) ?: return CityLookup.NotFound
    val city = (address.locality ?: address.subAdminArea)?.let(::cleanServiceText).orEmpty()
    if (city.isEmpty()) return CityLookup.NotFound
    val province = address.subAdminArea?.let(::cleanServiceText)?.takeIf { it.isNotEmpty() }
    return CityLookup.Found(city, province)
  }

  private fun providers(manager: LocationManager): List<String> =
    listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
      .filter { it in manager.allProviders && manager.isProviderEnabled(it) }

  /**
   * Tries each enabled provider in turn within one time budget: the fused one may have no
   * network location (no Google services, network location off), and the system still blurs
   * a satellite fix to an approximate one for an app without precise permission.
   */
  private suspend fun currentPosition(manager: LocationManager): Location? =
    withTimeoutOrNull(LOCATE_TIMEOUT_MS) {
      providers(manager).firstNotNullOfOrNull { provider ->
        withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { positionFrom(manager, provider) }
      }
    }

  // Only called from locate(), which checks the permission first and handles its revocation.
  @SuppressLint("MissingPermission")
  private suspend fun positionFrom(manager: LocationManager, provider: String): Location? =
    suspendCancellableCoroutine { continuation ->
      val cancel = CancellationSignal()
      continuation.invokeOnCancellation { cancel.cancel() }
      manager.getCurrentLocation(provider, cancel, appContext.mainExecutor) { location ->
        if (continuation.isActive) continuation.resume(location)
      }
    }

  @SuppressLint("MissingPermission")
  private fun lastPosition(manager: LocationManager): Location? =
    providers(manager).mapNotNull { manager.getLastKnownLocation(it) }
      .filter { System.currentTimeMillis() - it.time <= LAST_POSITION_MAX_AGE_MS }
      .maxByOrNull { it.time }

  private suspend fun cityOf(position: Location): Address? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(appContext, PRICE_LOCALE)
    return withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
      suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocation(position.latitude, position.longitude, 1, object : Geocoder.GeocodeListener {
          override fun onGeocode(addresses: MutableList<Address>) {
            if (continuation.isActive) continuation.resume(addresses.firstOrNull())
          }

          override fun onError(errorMessage: String?) {
            if (continuation.isActive) continuation.resume(null)
          }
        })
      }
    }
  }

  private companion object {
    /** The whole search for a position; each provider gets a slice of it. */
    const val LOCATE_TIMEOUT_MS = 15_000L
    const val PROVIDER_TIMEOUT_MS = 6_000L
    const val GEOCODE_TIMEOUT_MS = 10_000L
    const val LAST_POSITION_MAX_AGE_MS = 30L * 60 * 1000
  }
}
