package ourmeal.app

import android.graphics.Rect
import android.view.LayoutInflater
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import app.ourmeat.R
import app.ourmeat.databinding.ItemDishSuggestionBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import ourmeal.app.model.DishHistory
import ourmeal.app.model.DishSuggestion
import ourmeal.app.model.MealSlot
import ourmeal.app.model.RepeatHint
import ourmeal.app.model.WeeklyMeals
import ourmeal.app.model.dishKeys
import ourmeal.app.model.repeatHint
import java.time.LocalDate
import java.util.Locale

/** Suggestion strip and repeat hint for one meal field of the weekly board. */
internal class MealSuggestions(
  private val slot: MealSlot,
  private val input: AppCompatEditText,
  private val strip: HorizontalScrollView,
  private val chips: ChipGroup,
  private val hint: TextView,
  private val onPick: (DishSuggestion) -> Unit,
  private val onHide: (DishSuggestion) -> Unit
) {
  private val context = input.context
  private val locale = Locale.forLanguageTag("es-ES")
  private var shown: List<DishSuggestion> = emptyList()
  private var shownCanHide = false
  /** Text just filled in from a chip; the strip stays hidden until the user edits it. */
  private var pickedText: String? = null

  /** Called after loading and after every change of day, text or hidden dishes. */
  fun render(state: SuggestionState) {
    renderHint(repeatHint(state.week, state.day, slot, state.slotDate, state.history))
    val text = input.text?.toString().orEmpty()
    if (pickedText != text) pickedText = null
    if (!state.showStrip || pickedText != null || !isEditing()) {
      collapse()
      return
    }
    val suggestions = state.history.suggestions(
      query = text,
      slot = slot,
      slotDate = state.slotDate,
      plannedKeys = state.week.dishKeys(),
      hiddenKeys = state.hiddenKeys
    )
    if (suggestions.isEmpty()) {
      collapse()
      return
    }
    bindChips(suggestions, state.canHide)
    if (!strip.isVisible) {
      // Every time it appears, the strip starts at the best suggestion.
      strip.scrollTo(0, 0)
      strip.reveal()
      // Once measured, let the screen scroll so the chips stay above the keyboard.
      strip.doOnLayout { it.requestRectangleOnScreen(Rect(0, 0, it.width, it.height), false) }
    }
  }

  fun hideStrip() {
    pickedText = null
    collapse()
  }

  /** Hides the strip once focus has left both the field and its chips. */
  fun onFocusLeft() {
    strip.post { if (!isEditing()) hideStrip() }
  }

  private fun isEditing(): Boolean = input.hasFocus() || strip.hasFocus()

  private fun collapse() {
    if (!strip.isVisible) return
    // Keep keyboard focus in the field instead of letting it jump elsewhere on the screen.
    if (strip.hasFocus()) input.requestFocus()
    strip.animate().cancel()
    strip.isVisible = false
  }

  private fun bindChips(suggestions: List<DishSuggestion>, canHide: Boolean) {
    if (suggestions == shown && canHide == shownCanHide) return
    if (chips.hasFocus()) input.requestFocus()
    shown = suggestions
    shownCanHide = canHide
    chips.removeAllViews()
    val inflater = LayoutInflater.from(context)
    suggestions.forEach { chips.addView(createChip(inflater, it, canHide)) }
    strip.scrollTo(0, 0)
  }

  private fun createChip(
    inflater: LayoutInflater,
    suggestion: DishSuggestion,
    canHide: Boolean
  ): Chip = ItemDishSuggestionBinding.inflate(inflater, chips, false).root.apply {
    text = suggestion.name
    contentDescription = context.getString(R.string.history_use_suggestion, suggestion.name)
    setOnClickListener { pick(suggestion) }
    setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) onFocusLeft() }
    if (canHide) {
      val hideLabel = context.getString(R.string.history_hide_action)
      setOnLongClickListener {
        onHide(suggestion)
        true
      }
      ViewCompat.replaceAccessibilityAction(this, AccessibilityActionCompat.ACTION_LONG_CLICK, hideLabel, null)
      ViewCompat.addAccessibilityAction(this, hideLabel) { _, _ ->
        onHide(suggestion)
        true
      }
    }
  }

  private fun pick(suggestion: DishSuggestion) {
    if (!input.hasFocus()) input.requestFocus()
    pickedText = suggestion.name
    collapse()
    onPick(suggestion)
  }

  private fun renderHint(repeat: RepeatHint?) {
    val text = when (repeat) {
      null -> ""
      is RepeatHint.SameWeek -> context.getString(
        R.string.history_repeat_same_week,
        repeat.day.lowercase(locale),
        context.getString(repeat.slot.labelRes).lowercase(locale)
      )
      is RepeatHint.Recent -> context.resources.getQuantityString(
        R.plurals.history_repeat_recent, repeat.daysAgo, repeat.daysAgo
      )
    }
    // Rewriting the same text would make the live region announce it on every keystroke.
    if (hint.text.toString() != text) hint.text = text
    hint.isVisible = text.isNotEmpty()
  }

  private val MealSlot.labelRes: Int
    get() = when (this) {
      MealSlot.COMIDA -> R.string.day_lunch
      MealSlot.CENA -> R.string.day_dinner
    }
}

internal data class SuggestionState(
  val history: DishHistory,
  val week: WeeklyMeals,
  val day: String,
  val slotDate: LocalDate,
  val hiddenKeys: Set<String>,
  /** False when the settings file could not be read: hiding would have nowhere to be saved. */
  val canHide: Boolean,
  val showStrip: Boolean
)
