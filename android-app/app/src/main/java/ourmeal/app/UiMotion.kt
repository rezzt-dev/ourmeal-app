package ourmeal.app

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import androidx.core.view.isVisible

/** Short state transitions shared by the three screens. System animation settings take precedence. */
internal fun View.reveal() {
  val wasHidden = !isVisible
  animate().cancel()
  isVisible = true
  if (!wasHidden || !ValueAnimator.areAnimatorsEnabled()) {
    alpha = 1f
    translationY = 0f
    return
  }
  alpha = 0f
  translationY = 8f * resources.displayMetrics.density
  animate()
    .alpha(1f)
    .translationY(0f)
    .setDuration(220L)
    .setInterpolator(DecelerateInterpolator())
    .start()
}

internal fun ProgressBar.setProgressWithMotion(value: Int) {
  val target = value.coerceIn(0, max)
  if (ValueAnimator.areAnimatorsEnabled() && isShown) {
    setProgress(target, true)
  } else {
    progress = target
  }
}
