package ourmeal.app

import android.view.View
import com.google.android.material.snackbar.Snackbar

/** Shows a themed snackbar, optionally anchored above a persistent action bar. */
internal fun View.showSnackbar(
  message: String,
  actionLabel: String? = null,
  anchor: View? = null,
  onAction: (() -> Unit)? = null
) {
  val duration = if (actionLabel != null) 6000 else Snackbar.LENGTH_SHORT
  Snackbar.make(this, message, duration).apply {
    anchor?.let(::setAnchorView)
    if (actionLabel != null && onAction != null) {
      setAction(actionLabel) { onAction() }
    }
  }.show()
}
