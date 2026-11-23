package ourmeal.app.controller

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import app.ourmeat.R

/**
 * Long press renames the row, because a tap already opens or marks it. Screen readers offer the
 * same action by name, like the suggestions that can be hidden. A disabled row offers neither.
 */
internal fun View.setRenameAction(enabled: Boolean, onRename: () -> Unit) {
  val label = context.getString(R.string.rename_action)
  setOnLongClickListener {
    if (enabled) onRename()
    enabled
  }
  isLongClickable = enabled
  ViewCompat.replaceAccessibilityAction(this, AccessibilityActionCompat.ACTION_LONG_CLICK, label, null)
  // The same label always maps to the same id, so rebinding a recycled row never piles actions up.
  val actionId = ViewCompat.addAccessibilityAction(this, label) { _, _ ->
    onRename()
    true
  }
  if (!enabled) ViewCompat.removeAccessibilityAction(this, actionId)
}
