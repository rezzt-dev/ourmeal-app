package ourmeal.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/** Keep every app surface on the same palette, independently of the device theme. */
class OurmealApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
  }
}
