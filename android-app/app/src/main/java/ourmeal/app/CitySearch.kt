package ourmeal.app

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import app.ourmeat.R
import app.ourmeat.databinding.DialogCitySearchBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ourmeal.app.controller.PriceResult
import ourmeal.app.controller.PriceService
import ourmeal.app.model.PriceCity
import ourmeal.app.model.normalizeName

private const val CITY_SEARCH_MIN_LENGTH = 2

/** Searches only on explicit submit; public Nominatim must never be used for autocomplete. */
internal fun AppCompatActivity.showCitySearch(service: PriceService, onPick: (PriceCity) -> Unit) {
  val builder = ourmealDialog()
  val view = DialogCitySearchBinding.inflate(LayoutInflater.from(builder.context))
  val dialog = builder
    .setTitle(R.string.city_search_title)
    .setView(view.root)
    .setPositiveButton(R.string.city_search_action, null)
    .setNegativeButton(R.string.cancel, null)
    .create()
  var search: Job? = null

  fun showStatus(text: Int) {
    view.tvCitySearchStatus.setText(text)
    view.tvCitySearchStatus.isVisible = true
  }

  fun showResults(cities: List<PriceCity>) {
    view.cityResults.removeAllViews()
    if (cities.isEmpty()) return showStatus(R.string.city_search_empty)
    view.tvCitySearchStatus.isVisible = false
    val inflater = LayoutInflater.from(view.root.context)
    cities.forEach { city ->
      val row = inflater.inflate(R.layout.item_city_result, view.cityResults, false) as TextView
      row.text = city.province?.takeIf { it != city.name }
        ?.let { getString(R.string.city_search_result, city.name, it) } ?: city.name
      row.setOnClickListener {
        dialog.dismiss()
        onPick(city)
      }
      view.cityResults.addView(row)
    }
  }

  fun searchFor(raw: String) {
    search?.cancel()
    val query = normalizeName(raw)
    if (query.length < CITY_SEARCH_MIN_LENGTH) {
      view.cityResults.removeAllViews()
      showStatus(R.string.city_search_hint)
      return
    }
    search = lifecycleScope.launch {
      showStatus(R.string.city_search_loading)
      when (val result = service.cities(query)) {
        is PriceResult.Success -> showResults(result.value)
        PriceResult.Offline -> {
          view.cityResults.removeAllViews()
          showStatus(R.string.city_search_offline)
        }
        PriceResult.Unavailable -> {
          view.cityResults.removeAllViews()
          showStatus(R.string.city_search_error)
        }
      }
    }
  }

  view.etCity.doAfterTextChanged {
    search?.cancel()
    view.cityResults.removeAllViews()
    showStatus(R.string.city_search_hint)
    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled = (it?.trim()?.length ?: 0) >= CITY_SEARCH_MIN_LENGTH
  }
  view.etCity.setOnEditorActionListener { input, action, event ->
    val submit = action == EditorInfo.IME_ACTION_SEARCH ||
      (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
    if (submit) searchFor(input.text?.toString().orEmpty())
    submit
  }
  dialog.setOnShowListener {
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    val submit = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
    submit.isEnabled = false
    submit.setOnClickListener { searchFor(view.etCity.text?.toString().orEmpty()) }
    view.etCity.requestFocus()
  }
  dialog.setOnDismissListener { search?.cancel() }
  dialog.show()
}
