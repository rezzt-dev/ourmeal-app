package ourmeal.app

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import app.ourmeat.R
import app.ourmeat.databinding.DialogItemPriceBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import ourmeal.app.model.PriceInput
import ourmeal.app.model.ShownPrice
import ourmeal.app.model.formatPrice
import ourmeal.app.model.parsePrice
import ourmeal.app.model.priceFieldText

/** The parts of an open price form that its submit handler uses. */
internal class PriceForm(
  override val dialog: AlertDialog,
  private val field: TextInputLayout,
  private val input: TextInputEditText
) : OpenForm {
  val price: PriceInput get() = parsePrice(input.text?.toString().orEmpty())

  override fun showError(message: String) {
    field.error = message
  }

  fun clearError() {
    field.error = null
  }

  /** While busy the form cannot be edited, cancelled, cleared or submitted again. */
  fun setBusy(busy: Boolean) {
    dialog.setFormSaving(field, busy)
    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setText(if (busy) R.string.saving else R.string.save)
    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = !busy
  }
}

/**
 * Opens the price form of the product [target], named [itemName]. It starts with the [current]
 * price, written in the list or remembered; leaving it blank or tapping "Quitar precio" submits
 * [PriceInput.Empty]. Its draft survives recreation through [state].
 */
internal fun AppCompatActivity.openPriceForm(
  state: CreationFormState,
  target: Long,
  itemName: String,
  current: ShownPrice?,
  onSubmit: (PriceForm, PriceInput) -> Unit
) {
  val builder = ourmealDialog()
  val parts = DialogItemPriceBinding.inflate(LayoutInflater.from(builder.context))
  parts.tvPriceDialogMessage.text = if (current?.remembered == true) {
    getString(R.string.price_message_remembered, formatPrice(current.cents))
  } else {
    getString(R.string.price_message)
  }
  parts.etPrice.setText(current?.let { priceFieldText(it.cents) }.orEmpty())
  parts.etPrice.setSelection(parts.etPrice.length())
  builder
    .setTitle(getString(R.string.price_title, itemName))
    .setView(parts.root)
    .setNegativeButton(R.string.cancel, null)
    .setPositiveButton(R.string.save, null)
  if (current != null) builder.setNeutralButton(R.string.price_remove, null)
  val dialog = builder.create()
  state.attach(dialog, parts.etPrice, FormKind.PRICE_ITEM, target)
  val form = PriceForm(dialog, parts.priceField, parts.etPrice)
  dialog.prepareForm(parts.priceField, parts.etPrice, allowsEmpty = true) { onSubmit(form, form.price) }
  dialog.show()
  // The buttons exist once shown; like the confirm button, removing keeps the dialog open while it saves.
  dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { onSubmit(form, PriceInput.Empty) }
}
