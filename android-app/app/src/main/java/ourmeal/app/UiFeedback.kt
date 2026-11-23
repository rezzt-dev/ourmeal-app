package ourmeal.app

import android.view.View
import android.view.ViewGroup
import app.ourmeat.R
import com.google.android.material.snackbar.Snackbar

/**
 * Shows a themed snackbar, optionally anchored above a persistent action bar. Its sides line up
 * with the screen gutter instead of Material's narrower default margin.
 */
internal fun View.showSnackbar(
  message: String,
  actionLabel: String? = null,
  anchor: View? = null,
  duration: Int = if (actionLabel != null) 6000 else Snackbar.LENGTH_SHORT,
  onAction: (() -> Unit)? = null
) {
  Snackbar.make(this, message, duration).apply {
    anchor?.let(::setAnchorView)
    if (actionLabel != null && onAction != null) {
      setAction(actionLabel) { onAction() }
    }
    (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
      val gutter = view.resources.getDimensionPixelSize(R.dimen.screen_gutter)
      params.leftMargin = gutter
      params.rightMargin = gutter
      view.layoutParams = params
    }
  }.show()
}
