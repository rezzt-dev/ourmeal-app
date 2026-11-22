package ourmeal.app.controller

import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import ourmeal.app.model.AliasRequest
import ourmeal.app.model.CityDto
import ourmeal.app.model.CityStores
import ourmeal.app.model.CityStoresResponse
import ourmeal.app.model.CompareItemDto
import ourmeal.app.model.CompareRequest
import ourmeal.app.model.CompareResponse
import ourmeal.app.model.ItemMode
import ourmeal.app.model.PriceCity
import ourmeal.app.model.PriceOffer
import ourmeal.app.model.PriceTarget
import ourmeal.app.model.PriceSettings
import ourmeal.app.model.Resolution
import ourmeal.app.model.ResolveRequest
import ourmeal.app.model.ResolveResponse
import ourmeal.app.model.toCities
import ourmeal.app.model.toCityStores
import ourmeal.app.model.toOffers
import ourmeal.app.model.toResolution
import retrofit2.Converter
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

/** Endpoints of the price service. The app never talks to the supermarkets themselves. */
internal interface PriceApi {
  @GET("v1/cities")
  suspend fun cities(@Query("query") query: String): List<CityDto?>

  @GET("v1/stores")
  suspend fun stores(@Query("city") city: String, @Query("province") province: String?): CityStoresResponse

  @POST("v1/resolve")
  suspend fun resolve(@Body body: ResolveRequest): ResolveResponse

  @POST("v1/aliases")
  suspend fun alias(@Body body: AliasRequest): Response<Unit>

  @POST("v1/compare")
  suspend fun compare(@Body body: CompareRequest): CompareResponse
}

/** Outcome of one call: the validated value, no connection, or a service that failed. */
sealed interface PriceResult<out T> {
  data class Success<T>(val value: T) : PriceResult<T>
  data object Offline : PriceResult<Nothing>
  data object Unavailable : PriceResult<Nothing>
}

/**
 * Client of the price service. Sends only what each endpoint needs (a city name, a product
 * name, ids) with the random installation key, and validates every answer before using it.
 */
class PriceService internal constructor(private val api: PriceApi) {

  suspend fun cities(query: String): PriceResult<List<PriceCity>> =
    call({ cities(query) }) { it.toCities() }

  suspend fun stores(city: String, province: String?): PriceResult<CityStores> =
    call({ stores(city, province) }) { it.toCityStores() }

  suspend fun resolve(query: String, mode: ItemMode = ItemMode.GENERIC, settings: PriceSettings? = null): PriceResult<Resolution> =
    call({ resolve(ResolveRequest(query, mode, settings?.city?.id, settings?.chainIds?.sorted().orEmpty())) }) { it.toResolution() }

  /** Teaches the service what the user meant; failures are harmless and ignored. */
  suspend fun sendAlias(query: String, target: PriceTarget): Boolean =
    call({ alias(AliasRequest(query, target.groupId, target.canonicalId)) }) { response ->
      response.isSuccessful.takeIf { it }
    } is PriceResult.Success

  /** Offers per target key; at most [MAX_COMPARE_ITEMS] targets per call. */
  suspend fun compare(
    targets: Collection<PriceTarget>,
    chainIds: Collection<Int>,
    storeIds: Collection<Int>,
    cityId: Int? = null
  ): PriceResult<PriceBatch> {
    val unique = targets.distinctBy { it.key }
    require(unique.size <= MAX_COMPARE_ITEMS) { "Too many items in one comparison" }
    val request = CompareRequest(
      chainIds = chainIds.sorted(),
      storeIds = storeIds.sorted(),
      items = unique.map { CompareItemDto(it.key, it.mode, it.groupId, it.canonicalId) },
      cityId = cityId
    )
    val requested = unique.mapTo(mutableSetOf()) { it.key }
    return call({ compare(request) }) { PriceBatch(it.toOffers(requested), it.pending == true) }
  }

  private suspend fun <T, R : Any> call(request: suspend PriceApi.() -> T, map: (T) -> R?): PriceResult<R> =
    try {
      map(api.request())?.let { PriceResult.Success(it) } ?: PriceResult.Unavailable
    } catch (error: CancellationException) {
      throw error
    } catch (_: java.io.InterruptedIOException) {
      PriceResult.Unavailable
    } catch (_: IOException) {
      PriceResult.Offline
    } catch (_: HttpException) {
      PriceResult.Unavailable
    } catch (_: RuntimeException) {
      // An unreadable or null body, or a shape the converter cannot build.
      PriceResult.Unavailable
    }

  companion object {
    const val MAX_COMPARE_ITEMS = 40

    /** Shared so every screen reuses the same connections. */
    private val baseClient: OkHttpClient by lazy {
      OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .addInterceptor { chain ->
          // The first OSM city lookup can be slow; pricing calls keep the shorter read timeout.
          val requestChain = if (chain.request().url.encodedPath.endsWith("/v1/stores"))
            chain.withReadTimeout(65, TimeUnit.SECONDS) else chain
          requestChain.proceed(chain.request())
        }
        .build()
    }

    /**
     * True for an https address, or for plain http in a debug build only (local development
     * service). Anything else leaves the comparison unavailable.
     */
    fun isUsableUrl(url: String, debug: Boolean): Boolean {
      val parsed = url.toHttpUrlOrNull() ?: return false
      return parsed.isHttps || (debug && parsed.scheme == "http")
    }

    /** Null when [baseUrl] is not usable. */
    fun create(baseUrl: String, installationKey: String, debug: Boolean, userAgent: String): PriceService? {
      if (!isUsableUrl(baseUrl, debug)) return null
      val client = baseClient.newBuilder()
        .addInterceptor { chain ->
          chain.proceed(
            chain.request().newBuilder()
              .header("X-Api-Key", installationKey)
              .header("User-Agent", userAgent)
              .header("Accept", "application/json")
              .build()
          )
        }
        .build()
      val api = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(UnusableBodyConverterFactory(GsonConverterFactory.create()))
        .build()
        .create(PriceApi::class.java)
      return PriceService(api)
    }
  }
}

/** A partial catalogue remains usable while its pending city/chain reads finish. */
data class PriceBatch(val offers: Map<String, List<PriceOffer>>, val pending: Boolean) {
  operator fun get(key: String): List<PriceOffer>? = offers[key]
  fun getValue(key: String): List<PriceOffer> = offers.getValue(key)
}

/** A body that arrived but cannot be read is the service's fault, not the connection's. */
private class UnusableBodyException(cause: Throwable) : RuntimeException(cause)

/** Gson conversion where an unreadable body (truncated, malformed) fails as [UnusableBodyException]. */
private class UnusableBodyConverterFactory(private val gson: GsonConverterFactory) : Converter.Factory() {
  override fun responseBodyConverter(
    type: Type,
    annotations: Array<out Annotation>,
    retrofit: Retrofit
  ): Converter<ResponseBody, *>? {
    val delegate = gson.responseBodyConverter(type, annotations, retrofit) ?: return null
    return Converter<ResponseBody, Any?> { body ->
      try {
        delegate.convert(body)
      } catch (error: IOException) {
        throw UnusableBodyException(error)
      }
    }
  }

  override fun requestBodyConverter(
    type: Type,
    parameterAnnotations: Array<out Annotation>,
    methodAnnotations: Array<out Annotation>,
    retrofit: Retrofit
  ): Converter<*, RequestBody>? = gson.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)
}
