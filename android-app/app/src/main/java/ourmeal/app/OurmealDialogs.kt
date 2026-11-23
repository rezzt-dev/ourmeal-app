package ourmeal.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.core.widget.doAfterTextChanged
import app.ourmeat.R
import app.ourmeat.databinding.DialogAddItemBinding
import app.ourmeat.databinding.DialogCreateListBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import ourmeal.app.model.normalizeName

internal fun AppCompatActivity.ourmealDialog(destructive: Boolean = false): MaterialAlertDialogBuilder =
  MaterialAlertDialogBuilder(this, if (destructive) R.style.ThemeOverlay_Ourmeal_DestructiveDialog
    else R.style.ThemeOverlay_Ourmeal_AlertDialog)

/**
 * Shared keyboard, validation and submission behavior for creation forms. A form that
 * [allowsEmpty] can be submitted blank, for example to remove an optional value.
 */
internal fun AlertDialog.prepareForm(
  field: TextInputLayout,
  input: TextInputEditText,
  allowsEmpty: Boolean = false,
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
      confirm.isEnabled = input.isEnabled &&
        (allowsEmpty || normalizeName(input.text?.toString().orEmpty()).isNotEmpty())
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

/** Which name a form asks for: both share the length limit and differ in hint and example. */
internal enum class NameKind { LIST, PRODUCT }

/** Texts and starting value of a name form. */
internal data class NameFormSpec(
  @StringRes val title: Int,
  val message: String,
  @StringRes val confirm: Int,
  @StringRes val busy: Int,
  val initial: String = ""
)

/** An open form that can show an error under its field while it is on screen. */
internal interface OpenForm {
  val dialog: AlertDialog
  fun showError(message: String)
}

/** The parts of an open name form that its submit handler uses. */
internal class NameForm(
  override val dialog: AlertDialog,
  private val field: TextInputLayout,
  private val input: TextInputEditText,
  @StringRes private val confirmLabel: Int,
  @StringRes private val busyLabel: Int
) : OpenForm {
  val name: String get() = normalizeName(input.text?.toString().orEmpty())

  override fun showError(message: String) {
    field.error = message
  }

  fun clearError() {
    field.error = null
  }

  /** While busy the form cannot be edited, cancelled or submitted again. */
  fun setBusy(busy: Boolean) {
    dialog.setFormSaving(field, busy)
    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setText(if (busy) busyLabel else confirmLabel)
  }
}

/**
 * Opens the shared form used to create, rename or duplicate a list or a product. Its draft
 * survives recreation through [state], which also records [kind] and the edited [target].
 */
internal fun AppCompatActivity.openNameForm(
  state: CreationFormState,
  kind: FormKind,
  nameKind: NameKind,
  spec: NameFormSpec,
  target: Long = CreationFormState.NO_TARGET,
  onSubmit: (NameForm) -> Unit
) {
  val builder = ourmealDialog()
  val inflater = LayoutInflater.from(builder.context)
  val parts = when (nameKind) {
    NameKind.LIST -> DialogCreateListBinding.inflate(inflater).run {
      FormParts(root, listNameField, etListName, tvListDialogMessage)
    }
    NameKind.PRODUCT -> DialogAddItemBinding.inflate(inflater).run {
      FormParts(root, itemNameField, etItemName, tvItemDialogMessage)
    }
  }
  parts.message.text = spec.message
  parts.input.setText(spec.initial)
  parts.input.setSelection(parts.input.length())
  val dialog = builder
    .setTitle(spec.title)
    .setView(parts.root)
    .setNegativeButton(R.string.cancel, null)
    .setPositiveButton(spec.confirm, null)
    .create()
  state.attach(dialog, parts.input, kind, target)
  val form = NameForm(dialog, parts.field, parts.input, spec.confirm, spec.busy)
  dialog.prepareForm(parts.field, parts.input) { onSubmit(form) }
  dialog.show()
}

private class FormParts(
  val root: View,
  val field: TextInputLayout,
  val input: TextInputEditText,
  val message: TextView
)

/** What an open form does, so it can be reopened for the same purpose after recreation. */
internal enum class FormKind { CREATE, RENAME_LIST, RENAME_ITEM, DUPLICATE_LIST, PRICE_ITEM }

/**
 * Restore an unfinished creation or edit form after rotation or system recreation, once data is
 * loaded. An edit form also remembers which list or product it edits.
 */
internal class CreationFormState(activity: AppCompatActivity) {
  private var pending = activity.savedStateRegistry.consumeRestoredStateForKey(STATE_KEY)
  private var dialog: AlertDialog? = null
  private var input: TextInputEditText? = null
  private var kind = FormKind.CREATE
  private var targetId = NO_TARGET

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
          putString("kind", kind.name)
          putLong("target", targetId)
        }
      } else pending ?: Bundle()
    }
  }

  val isShowing: Boolean get() = dialog?.isShowing == true

  /** Reopens the saved form, if any, with its purpose and the id of what it edits. */
  fun restore(open: (kind: FormKind, targetId: Long) -> Unit) {
    val saved = pending ?: return
    if (!saved.containsKey("text")) return
    val savedKind = FormKind.entries.firstOrNull { it.name == saved.getString("kind") } ?: FormKind.CREATE
    open(savedKind, saved.getLong("target", NO_TARGET))
    // Nothing reopened it, e.g. the edited product was deleted meanwhile: drop the draft.
    if (dialog == null) pending = null
  }

  /** Call after filling [field] with the current value: a restored draft replaces it. */
  fun attach(
    form: AlertDialog,
    field: TextInputEditText,
    formKind: FormKind = FormKind.CREATE,
    target: Long = NO_TARGET
  ) {
    dialog = form
    input = field
    kind = formKind
    targetId = target
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

  companion object {
    const val NO_TARGET = -1L
    private const val STATE_KEY = "creation_form"
  }
}
