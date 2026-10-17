package ourmeal.app

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** Keeps content clear of system bars, cutouts and the keyboard in edge-to-edge windows. */
internal fun View.applyScreenInsets() {
  ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
    val bars = insets.getInsets(
      WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    )
    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
    view.updatePadding(
      left = bars.left,
      top = bars.top,
      right = bars.right,
      bottom = maxOf(bars.bottom, ime.bottom)
    )
    insets
  }
  ViewCompat.requestApplyInsets(this)
}
