package ourmeal.app

import app.ourmeat.BuildConfig
import ourmeal.app.controller.PriceService
import ourmeal.app.model.PriceSettings

/** Where the price service lives in this build, and the client every screen uses to reach it. */
internal object PriceFeature {
  /** False when this build has no usable service address: the comparison cannot be turned on. */
  val isAvailable: Boolean
    get() = PriceService.isUsableUrl(BuildConfig.PRICE_API_URL, BuildConfig.DEBUG)

  fun service(settings: PriceSettings): PriceService? = PriceService.create(
    baseUrl = BuildConfig.PRICE_API_URL,
    installationKey = settings.installationKey,
    debug = BuildConfig.DEBUG,
    userAgent = "OurMeal-Android/${BuildConfig.VERSION_NAME}"
  )
}
