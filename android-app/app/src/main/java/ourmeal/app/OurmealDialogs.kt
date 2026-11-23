package ourmeal.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.core.widget.doAfterTextChanged
import app.ourmeat.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import ourmeal.app.model.normalizeName

internal fun AppCompatActivity.ourmealDialog(destructive: Boolean = false): MaterialAlertDialogBuilder =
  MaterialAlertDialogBuilder(this, if (destructive) R.style.ThemeOverlay_Ourmeal_DestructiveDialog
    else R.style.ThemeOverlay_Ourmeal_AlertDialog)

/** Shared keyboard, validation and submission behavior for creation forms. */
internal fun AlertDialog.prepareForm(
  field: TextInputLayout,
  input: TextInputEditText,
  onSubmit: (Button) -> Unit
) {
  setCanceledOnTouchOutside(false)
  window?.setSoftInputMode(
    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
      WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
  )
  setOnShowListener {
    val confirm = getButton(AlertDialog.BUTTON_POSITIVE)
    fun updateAction() {
      confirm.isEnabled = input.isEnabled && normalizeName(input.text?.toString().orEmpty()).isNotEmpty()
    }
    updateAction()
    input.doAfterTextChanged {
      updateAction()
      field.error = null
    }
    input.setOnSubmitListener { if (confirm.isEnabled) confirm.performClick() }
    confirm.setOnClickListener { onSubmit(confirm) }
    input.requestFocus()
  }
}

/** Runs [submit] on the keyboard's done action or a physical Enter key. */
internal fun TextView.setOnSubmitListener(submit: () -> Unit) {
  setOnEditorActionListener { _, action, event ->
    val isSubmit = action == EditorInfo.IME_ACTION_DONE ||
      (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
    if (isSubmit) submit()
    isSubmit
  }
}

/** Do not allow an in-flight write to be edited, cancelled or submitted twice. */
internal fun AlertDialog.setFormSaving(field: TextInputLayout, saving: Boolean) {
  field.isEnabled = !saving
  getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !saving
  getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = !saving
  setCancelable(!saving)
}

/** Restore an unfinished form after rotation or system recreation, once data is loaded. */
internal class CreationFormState(activity: AppCompatActivity) {
  private var pending = activity.savedStateRegistry.consumeRestoredStateForKey(STATE_KEY)
  private var dialog: AlertDialog? = null
  private var input: TextInputEditText? = null

  init {
    activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onDestroy(owner: LifecycleOwner) {
        dialog?.dismiss()
      }
    })
    activity.savedStateRegistry.registerSavedStateProvider(STATE_KEY) {
      if (dialog?.isShowing == true && input?.isEnabled == true) {
        Bundle().apply {
          putString("text", input?.text?.toString().orEmpty())
          putInt("selection", input?.selectionStart ?: 0)
        }
      } else pending ?: Bundle()
    }
  }

  val isShowing: Boolean get() = dialog?.isShowing == true

  fun restore(open: () -> Unit) {
    if (pending?.containsKey("text") == true) open()
  }

  fun attach(form: AlertDialog, field: TextInputEditText) {
    dialog = form
    input = field
    pending?.let {
      field.setText(it.getString("text", ""))
      field.setSelection(it.getInt("selection").coerceIn(0, field.length()))
    }
    pending = null
    form.setOnDismissListener {
      dialog = null
      input = null
    }
  }

  private companion object {
    const val STATE_KEY = "creation_form"
  }
}
