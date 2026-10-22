package ourmeal.app

import android.view.View
import androidx.annotation.DrawableRes
import app.ourmeat.databinding.ViewLoadingBinding
import app.ourmeat.databinding.ViewStatePanelBinding

/** Fills the reusable loading panel. */
internal fun ViewLoadingBinding.bindLoading(label: String) {
  tvLoadingLabel.text = label
}

/** Fills the reusable empty/error panel, hiding the body and action when they are not needed. */
internal fun ViewStatePanelBinding.bindState(
  title: String,
  body: String,
  @DrawableRes icon: Int,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null
) {
  ivStateIcon.setImageResource(icon)
  tvStateTitle.text = title
  tvStateBody.text = body
  tvStateBody.visibility = if (body.isEmpty()) View.GONE else View.VISIBLE
  if (actionLabel != null && onAction != null) {
    btnStateAction.text = actionLabel
    btnStateAction.setOnClickListener { onAction() }
    btnStateAction.visibility = View.VISIBLE
  } else {
    btnStateAction.setOnClickListener(null)
    btnStateAction.visibility = View.GONE
  }
}
