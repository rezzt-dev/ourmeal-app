package ourmeal.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.doOnLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import app.ourmeat.R
import app.ourmeat.databinding.ActivityMainBinding
import app.ourmeat.databinding.DaySelectorChipBinding
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ourmeal.app.controller.DishSuggestionManager
import ourmeal.app.controller.MealDataManager
import ourmeal.app.model.DishHistory
import ourmeal.app.model.DishSuggestion
import ourmeal.app.model.MealSlot
import ourmeal.app.model.SuggestionSettings
import ourmeal.app.model.WeekUtils
import ourmeal.app.model.WeeklyMeals
import ourmeal.app.model.keysWrittenSince
import ourmeal.app.model.toDishRecords
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private lateinit var dataManager: MealDataManager
  private lateinit var suggestionManager: DishSuggestionManager
  private lateinit var lunchSuggestions: MealSuggestions
  private lateinit var dinnerSuggestions: MealSuggestions
  private var currentWeekId = ""
  private var currentWeeklyMeals: WeeklyMeals? = null
  /** Last copy of the visible week known to be on disk, to tell which dishes were just written. */
  private var savedWeeklyMeals: WeeklyMeals? = null
  private var history: DishHistory? = null
  /** Null while the settings file cannot be read; hiding suggestions is then unavailable. */
  private var settings: SuggestionSettings? = null
  private val settingsMutex = Mutex()
  private var selectedDayIndex = 0
  private val dayChips = mutableListOf<DayChip>()
  private var loadJob: Job? = null
  private var isBindingMeals = false
  private val saveHandler = Handler(Looper.getMainLooper())
  private var saveRunnable: Runnable? = null
  private val saveMutex = Mutex()
  private var isChangingWeek = false
  private val dateFormat =
    DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-ES"))
  private val dayNumberFormat =
    DateTimeFormatter.ofPattern("d", Locale.forLanguageTag("es-ES"))

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.root.applyScreenInsets()
    dataManager = MealDataManager(this)
    suggestionManager = DishSuggestionManager(this)
    currentWeekId = savedInstanceState?.getString(STATE_WEEK_ID) ?: WeekUtils.getCurrentWeekId()
    val fallbackDay = defaultSelectedDayIndex()
    selectedDayIndex = savedInstanceState
      ?.getInt(STATE_SELECTED_DAY, fallbackDay)
      ?: fallbackDay
    selectedDayIndex = selectedDayIndex.coerceIn(0, WeeklyMeals.DAYS.lastIndex)

    binding.btnPreviousWeek.setOnClickListener { changeWeek(WeekUtils.getPreviousWeek(currentWeekId)) }
    binding.btnNextWeek.setOnClickListener { changeWeek(WeekUtils.getNextWeek(currentWeekId)) }
    binding.btnToday.setOnClickListener { goToToday() }
    binding.btnOpenLists.setOnClickListener {
      startActivity(Intent(this, ListActivity::class.java))
    }
    binding.btnOpenCalendar.setOnClickListener { openWeekPicker() }
    setupMealFields()
    setupSuggestions()
    loadWeek()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putString(STATE_WEEK_ID, currentWeekId)
    outState.putInt(STATE_SELECTED_DAY, selectedDayIndex)
    super.onSaveInstanceState(outState)
  }

  private fun loadWeek() {
    val weekId = currentWeekId
    loadJob?.cancel()
    currentWeeklyMeals = null
    savedWeeklyMeals = null
    history = null
    binding.tvWeekTitle.text = WeekUtils.getWeekDisplay(weekId)
    binding.btnToday.isVisible = weekId != WeekUtils.getCurrentWeekId()
    binding.menuBoard.layoutDaySelector.removeAllViews()
    dayChips.clear()
    binding.menuBoard.root.isVisible = false
    binding.menuError.root.isVisible = false
    binding.menuLoading.root.isVisible = true
    binding.menuLoading.bindLoading(getString(R.string.menu_loading))

    val knownSettings = settings
    loadJob = lifecycleScope.launch {
      val loaded = try {
        withContext(Dispatchers.IO) {
          val weeks = dataManager.loadWeeklyMeals()
          LoadedWeek(
            plan = weeks.find { it.weekId == weekId } ?: WeeklyMeals(weekId),
            history = DishHistory(weeks.toDishRecords(excludeWeekId = weekId)),
            // Settings stay in memory once read, so a hide still being written is never lost.
            settings = knownSettings ?: loadSuggestionSettings()
          )
        }
      } catch (_: IOException) {
        null
      }
      if (weekId != currentWeekId) return@launch
      binding.menuLoading.root.isVisible = false
      if (loaded == null) {
        showMenuError()
        return@launch
      }
      currentWeeklyMeals = loaded.plan
      savedWeeklyMeals = loaded.plan.snapshot()
      history = loaded.history
      if (settings == null) settings = loaded.settings
      bindWeek()
      binding.menuBoard.root.reveal()
      setSaveStatus(SaveStatus.Idle)
    }
  }

  private fun loadSuggestionSettings(): SuggestionSettings? = try {
    suggestionManager.load()
  } catch (_: IOException) {
    null
  }

  private fun showMenuError() {
    binding.menuBoard.root.isVisible = false
    binding.menuError.root.reveal()
    binding.menuError.bindState(
      title = getString(R.string.menu_error_title),
      body = getString(R.string.menu_error_body),
      icon = R.drawable.ic_alert,
      actionLabel = getString(R.string.retry),
      onAction = ::loadWeek
    )
    setSaveStatus(SaveStatus.Idle)
  }

  private fun bindWeek() {
    val startDate = WeekUtils.startOfWeek(currentWeekId)
    val today = LocalDate.now()
    binding.menuBoard.layoutDaySelector.removeAllViews()
    dayChips.clear()

    WeeklyMeals.DAYS.forEachIndexed { index, day ->
      val chip = DaySelectorChipBinding.inflate(
        layoutInflater,
        binding.menuBoard.layoutDaySelector,
        false
      )
      val date = startDate.plusDays(index.toLong())
      val isToday = date == today
      chip.tvChipDay.text = day.take(3)
      chip.tvChipDate.text = date.format(dayNumberFormat)
      chip.root.contentDescription = getString(
        if (isToday) R.string.menu_day_today_description else R.string.menu_day_description,
        day,
        date.format(dateFormat)
      )
      chip.root.setOnClickListener { selectDay(index) }
      dayChips.add(DayChip(chip, date, isToday))
      binding.menuBoard.layoutDaySelector.addView(chip.root)
    }

    renderSelectedDay()
    updateChipStates()
    updateWeekProgress()
  }

  private fun selectDay(index: Int) {
    if (index !in WeeklyMeals.DAYS.indices || index == selectedDayIndex) return
    selectedDayIndex = index
    renderSelectedDay()
    updateChipStates()
  }

  private fun renderSelectedDay() {
    val plan = currentWeeklyMeals ?: return
    val day = WeeklyMeals.DAYS.getOrNull(selectedDayIndex) ?: return
    val chip = dayChips.getOrNull(selectedDayIndex) ?: return
    val meal = plan.getMealForDay(day)

    binding.menuBoard.tvSelectedDay.text = day
    binding.menuBoard.tvSelectedDate.text = chip.date.format(dateFormat)
    binding.menuBoard.tvSelectedTodayBadge.isVisible = chip.isToday

    finishMealEditing()
    isBindingMeals = true
    binding.menuBoard.etComida.setText(meal.comida)
    binding.menuBoard.etCena.setText(meal.cena)
    binding.menuBoard.etComida.setSelection(binding.menuBoard.etComida.text?.length ?: 0)
    binding.menuBoard.etCena.setSelection(binding.menuBoard.etCena.text?.length ?: 0)
    isBindingMeals = false
    renderSuggestions(lunchSuggestions, showStrip = false)
    renderSuggestions(dinnerSuggestions, showStrip = false)
  }

  private fun updateChipStates() {
    val brand = ContextCompat.getColor(this, R.color.brand)
    val ink = ContextCompat.getColor(this, R.color.ink)
    val muted = ContextCompat.getColor(this, R.color.muted)
    val onBrand = ContextCompat.getColor(this, R.color.canvas)

    dayChips.forEachIndexed { index, chip ->
      val selected = index == selectedDayIndex
      chip.binding.root.isSelected = selected
      when {
        selected -> {
          chip.binding.root.setBackgroundResource(R.drawable.bg_day_chip_selected)
          chip.binding.tvChipDay.setTextColor(onBrand)
          chip.binding.tvChipDate.setTextColor(onBrand)
        }
        chip.isToday -> {
          chip.binding.root.setBackgroundResource(R.drawable.bg_day_chip_today)
          chip.binding.tvChipDay.setTextColor(brand)
          chip.binding.tvChipDate.setTextColor(brand)
        }
        else -> {
          chip.binding.root.setBackgroundResource(R.drawable.bg_day_chip_normal)
          chip.binding.tvChipDay.setTextColor(ink)
          chip.binding.tvChipDate.setTextColor(muted)
        }
      }
    }
    // Wait until the visible board is measured before centering its selection.
    binding.menuBoard.daySelectorScroll.doOnLayout { scroll ->
      dayChips.getOrNull(selectedDayIndex)?.binding?.root?.let { selected ->
        val target = (selected.left + selected.width / 2 - scroll.width / 2)
          .coerceAtLeast(0)
        binding.menuBoard.daySelectorScroll.scrollTo(target, 0)
      }
    }
  }

  private fun setupMealFields() {
    watchMeal(binding.menuBoard.etComida, MealSlot.COMIDA)
    watchMeal(binding.menuBoard.etCena, MealSlot.CENA)
    binding.menuBoard.etComida.setOnEditorActionListener { _, action, _ ->
      if (action == EditorInfo.IME_ACTION_NEXT) {
        binding.menuBoard.etCena.requestFocus()
        true
      } else false
    }
    binding.menuBoard.etCena.setOnEditorActionListener { _, action, _ ->
      if (action == EditorInfo.IME_ACTION_DONE) {
        finishMealEditing()
        true
      } else false
    }
  }

  private fun finishMealEditing() {
    if (!binding.menuBoard.etComida.hasFocus() && !binding.menuBoard.etCena.hasFocus()) return
    binding.menuBoard.root.requestFocus()
    WindowCompat.getInsetsController(window, binding.root).hide(WindowInsetsCompat.Type.ime())
  }

  private fun setupSuggestions() {
    val board = binding.menuBoard
    lunchSuggestions = MealSuggestions(
      slot = MealSlot.COMIDA,
      input = board.etComida,
      strip = board.comidaSuggestionsScroll,
      chips = board.chipsComidaSuggestions,
      hint = board.tvComidaRepeat,
      onPick = { fillMeal(board.etComida, it) },
      onHide = { setSuggestionHidden(it, hidden = true) }
    )
    dinnerSuggestions = MealSuggestions(
      slot = MealSlot.CENA,
      input = board.etCena,
      strip = board.cenaSuggestionsScroll,
      chips = board.chipsCenaSuggestions,
      hint = board.tvCenaRepeat,
      onPick = { fillMeal(board.etCena, it) },
      onHide = { setSuggestionHidden(it, hidden = true) }
    )
  }

  private fun suggestionsFor(slot: MealSlot): MealSuggestions = when (slot) {
    MealSlot.COMIDA -> lunchSuggestions
    MealSlot.CENA -> dinnerSuggestions
  }

  private fun renderSuggestions(target: MealSuggestions, showStrip: Boolean) {
    val plan = currentWeeklyMeals ?: return
    val history = history ?: return
    val day = WeeklyMeals.DAYS.getOrNull(selectedDayIndex) ?: return
    val date = dayChips.getOrNull(selectedDayIndex)?.date ?: return
    target.render(
      SuggestionState(
        history = history,
        week = plan,
        day = day,
        slotDate = date,
        hiddenKeys = settings?.hiddenKeys.orEmpty(),
        canHide = settings != null,
        showStrip = showStrip
      )
    )
  }

  /** Repaints both meals of the day; only a focused field shows its strip. */
  private fun refreshSuggestions() {
    renderSuggestions(lunchSuggestions, showStrip = true)
    renderSuggestions(dinnerSuggestions, showStrip = true)
  }

  /** The text watcher then saves the dish like any other edit. */
  private fun fillMeal(input: AppCompatEditText, suggestion: DishSuggestion) {
    input.setText(suggestion.name)
    input.setSelection(input.length())
  }

  /**
   * Updates the hidden dishes in memory at once and then on disk, in call order. A failed write
   * is rolled back so the strip never claims a choice that was not saved.
   */
  private fun setSuggestionHidden(suggestion: DishSuggestion, hidden: Boolean) {
    val current = settings ?: return
    fun SuggestionSettings.withHidden(isHidden: Boolean) =
      if (isHidden) hide(suggestion.key) else unhide(setOf(suggestion.key))
    settings = current.withHidden(hidden)
    refreshSuggestions()
    lifecycleScope.launch {
      val saved = withContext(NonCancellable) {
        settingsMutex.withLock {
          withContext(Dispatchers.IO) { suggestionManager.update { it.withHidden(hidden) } }
        }
      }
      if (!saved) {
        settings = settings?.withHidden(!hidden)
        refreshSuggestions()
        binding.root.showSnackbar(getString(R.string.history_hide_error))
      } else if (hidden) {
        binding.root.showSnackbar(
          message = getString(R.string.history_hidden, suggestion.name),
          actionLabel = getString(R.string.undo),
          onAction = { setSuggestionHidden(suggestion, hidden = false) }
        )
      }
    }
  }

  /** A hidden dish that the user writes again and saves goes back to the suggestions. */
  private fun unhideWrittenDishes(written: Set<String>) {
    val keys = written.intersect(settings?.hiddenKeys ?: return)
    if (keys.isEmpty()) return
    lifecycleScope.launch {
      val saved = withContext(NonCancellable) {
        settingsMutex.withLock {
          withContext(Dispatchers.IO) { suggestionManager.update { it.unhide(keys) } }
        }
      }
      // On failure the dishes stay hidden in memory too, so the next save tries again.
      if (saved) {
        settings = settings?.unhide(keys)
        refreshSuggestions()
      }
    }
  }

  private fun watchMeal(input: AppCompatEditText, slot: MealSlot) {
    input.setOnFocusChangeListener { view, hasFocus ->
      if (hasFocus) {
        scrollFieldIntoView(view)
        renderSuggestions(suggestionsFor(slot), showStrip = true)
      } else {
        suggestionsFor(slot).onFocusLeft()
      }
    }
    input.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
      override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
      override fun afterTextChanged(text: Editable?) {
        if (isBindingMeals) return
        val plan = currentWeeklyMeals ?: return
        val day = WeeklyMeals.DAYS.getOrNull(selectedDayIndex) ?: return
        val meal = plan.getMealForDay(day)
        val value = text?.toString().orEmpty()
        when (slot) {
          MealSlot.COMIDA -> meal.comida = value
          MealSlot.CENA -> meal.cena = value
        }
        plan.setMealForDay(day, meal)
        updateWeekProgress()
        scheduleSave()
        // Both hints may change: the other meal of the day can repeat this one.
        refreshSuggestions()
      }
    })
  }

  /** Keeps the focused meal above the keyboard when the window shrinks. */
  private fun scrollFieldIntoView(field: View) {
    binding.mainScroll.post {
      val rect = Rect()
      field.getDrawingRect(rect)
      binding.mainScroll.offsetDescendantRectToMyCoords(field, rect)
      val clearance = (84 * resources.displayMetrics.density).toInt()
      binding.mainScroll.smoothScrollTo(0, (rect.top - clearance).coerceAtLeast(0))
    }
  }

  private fun updateWeekProgress() {
    val plan = currentWeeklyMeals ?: return
    val filled = WeeklyMeals.DAYS.sumOf { day ->
      val meal = plan.getMealForDay(day)
      (if (meal.comida.isNotBlank()) 1 else 0) + (if (meal.cena.isNotBlank()) 1 else 0)
    }
    val total = WeeklyMeals.DAYS.size * 2
    binding.tvWeekProgress.text =
      resources.getQuantityString(R.plurals.week_progress, total, filled, total)
  }

  private fun scheduleSave() {
    setSaveStatus(SaveStatus.Saving)
    cancelPendingSave()
    saveRunnable = Runnable {
      saveRunnable = null
      saveCurrentWeek()
    }.also { saveHandler.postDelayed(it, SAVE_DELAY_MS) }
  }

  private fun cancelPendingSave() {
    saveRunnable?.let(saveHandler::removeCallbacks)
    saveRunnable = null
  }

  /**
   * Writes a copy of the visible week off the main thread. Writes keep their call order and
   * finish even if the screen is destroyed meanwhile; [onResult] only runs while it is alive.
   */
  private fun saveCurrentWeek(onResult: ((Boolean) -> Unit)? = null) {
    val snapshot = currentWeeklyMeals?.snapshot()
    if (snapshot == null) {
      onResult?.invoke(true)
      return
    }
    lifecycleScope.launch {
      val saved = withContext(NonCancellable) {
        saveMutex.withLock {
          withContext(Dispatchers.IO) { dataManager.saveWeeklyMeal(snapshot) }
        }
      }
      if (saved) {
        // A newer edit may already be waiting; keep showing "saving" until that one is written.
        if (saveRunnable == null && snapshot.weekId == currentWeekId) setSaveStatus(SaveStatus.Saved)
        if (snapshot.weekId == savedWeeklyMeals?.weekId) {
          unhideWrittenDishes(snapshot.keysWrittenSince(savedWeeklyMeals))
          savedWeeklyMeals = snapshot
        }
      } else {
        setSaveStatus(SaveStatus.Error)
        binding.root.showSnackbar(
          message = getString(R.string.save_error),
          actionLabel = getString(R.string.retry),
          onAction = { saveCurrentWeek() }
        )
      }
      onResult?.invoke(saved)
    }
  }

  private fun WeeklyMeals.snapshot(): WeeklyMeals =
    copy(meals = meals.mapValues { it.value.copy() }.toMutableMap())

  private fun setSaveStatus(status: SaveStatus) {
    binding.tvSaveStatus.setText(status.label)
    binding.viewSaveDot.backgroundTintList =
      ColorStateList.valueOf(ContextCompat.getColor(this, status.dotColor))
  }

  private fun changeWeek(weekId: String, targetDayIndex: Int? = null) {
    if (weekId == currentWeekId || isChangingWeek) return
    cancelPendingSave()
    // Stop editing so nothing is typed into the old week while it is being written.
    finishMealEditing()
    isChangingWeek = true
    saveCurrentWeek { saved ->
      isChangingWeek = false
      if (!saved) return@saveCurrentWeek
      currentWeekId = weekId
      targetDayIndex?.let { selectedDayIndex = it }
      binding.mainScroll.smoothScrollTo(0, 0)
      loadWeek()
    }
  }

  private fun openWeekPicker() {
    if (supportFragmentManager.findFragmentByTag(WEEK_PICKER_TAG) != null) return
    val start = WeekUtils.startOfWeek(currentWeekId)
    val constraints = CalendarConstraints.Builder()
      .setFirstDayOfWeek(Calendar.MONDAY)
      .build()
    finishMealEditing()
    val picker = MaterialDatePicker.Builder.datePicker()
      .setTheme(R.style.ThemeOverlay_Ourmeal_Calendar)
      .setPositiveButtonText(R.string.select_date)
      .setNegativeButtonText(R.string.cancel)
      .setTitleText(R.string.week_pick_date)
      .setCalendarConstraints(constraints)
      .setSelection(start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
      .build()
    picker.addOnPositiveButtonClickListener { millis ->
      val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
      val weekId = WeekUtils.weekIdFor(date)
      val targetDay = dayIndexIn(weekId, date)
      if (weekId == currentWeekId) {
        selectedDayIndex = targetDay
        renderSelectedDay()
        updateChipStates()
      } else {
        changeWeek(weekId, targetDay)
      }
    }
    picker.show(supportFragmentManager, WEEK_PICKER_TAG)
  }

  private fun goToToday() {
    val todayWeek = WeekUtils.getCurrentWeekId()
    val today = dayIndexIn(todayWeek)
    if (todayWeek == currentWeekId) {
      selectedDayIndex = today
      renderSelectedDay()
      updateChipStates()
    } else {
      changeWeek(todayWeek, today)
    }
  }

  private fun dayIndexIn(weekId: String, date: LocalDate = LocalDate.now()): Int {
    val start = WeekUtils.startOfWeek(weekId)
    return ChronoUnit.DAYS.between(start, date).toInt()
      .coerceIn(0, WeeklyMeals.DAYS.lastIndex)
  }

  private fun defaultSelectedDayIndex(): Int =
    if (currentWeekId == WeekUtils.getCurrentWeekId()) dayIndexIn(currentWeekId) else 0

  override fun onPause() {
    cancelPendingSave()
    saveCurrentWeek()
    super.onPause()
  }

  override fun onDestroy() {
    cancelPendingSave()
    super.onDestroy()
  }

  private class LoadedWeek(
    val plan: WeeklyMeals,
    val history: DishHistory,
    val settings: SuggestionSettings?
  )

  private data class DayChip(
    val binding: DaySelectorChipBinding,
    val date: LocalDate,
    val isToday: Boolean
  )

  private enum class SaveStatus(@StringRes val label: Int, @ColorRes val dotColor: Int) {
    Idle(R.string.save_idle, R.color.sage_deep),
    Saving(R.string.save_saving, R.color.brand),
    Saved(R.string.save_saved, R.color.sage),
    Error(R.string.save_error, R.color.error)
  }

  private companion object {
    const val STATE_WEEK_ID = "week_id"
    const val STATE_SELECTED_DAY = "selected_day"
    const val WEEK_PICKER_TAG = "week_picker"
    const val SAVE_DELAY_MS = 700L
  }
}
