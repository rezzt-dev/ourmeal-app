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
import android.view.Menu
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
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
import ourmeal.app.model.CopyMode
import ourmeal.app.model.MealSlot
import ourmeal.app.model.MenuCopy
import ourmeal.app.model.MenuEdit
import ourmeal.app.model.MenuShareLabels
import ourmeal.app.model.SlotChange
import ourmeal.app.model.SuggestionSettings
import ourmeal.app.model.WeekUtils
import ourmeal.app.model.WeeklyMeals
import ourmeal.app.model.cleared
import ourmeal.app.model.deepCopy
import ourmeal.app.model.isDayEmpty
import ourmeal.app.model.keysWrittenSince
import ourmeal.app.model.plannedCount
import ourmeal.app.model.reverted
import ourmeal.app.model.sharedDays
import ourmeal.app.model.toShareText
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
  /** The week before the visible one, as stored, to copy it; null until the week is loaded. */
  private var previousWeekMeals: WeeklyMeals? = null
  private var isSharingImage = false
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
  private var isOpeningShopping = false
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
    binding.btnPrepareShopping.setOnClickListener { openShoppingPrep() }
    binding.btnShareMenu.setOnClickListener { showShareOptions(it) }
    binding.btnMenuTools.setOnClickListener { showMenuTools(it) }
    setupMealFields()
    setupSuggestions()
    loadWeek()
    // Opening the app also brings the home screen widget up to date, for example after midnight.
    if (savedInstanceState == null) TodayMenuWidget.requestRefresh(applicationContext)
  }

  /**
   * The home screen widget opens the menu on today, even if another week was on screen. The
   * action is handled at once, so the launch intent is kept and nothing replays it on recreation.
   */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    if (intent.action == ACTION_SHOW_TODAY) goToToday()
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
    previousWeekMeals = null
    history = null
    binding.btnMenuTools.isEnabled = false
    binding.tvWeekTitle.text = WeekUtils.getWeekDisplay(weekId)
    binding.btnToday.isVisible = weekId != WeekUtils.getCurrentWeekId()
    binding.menuBoard.layoutDaySelector.removeAllViews()
    dayChips.clear()
    binding.menuBoard.root.isVisible = false
    binding.prepareShoppingPanel.isVisible = false
    binding.menuError.root.isVisible = false
    binding.menuLoading.root.isVisible = true
    binding.menuLoading.bindLoading(getString(R.string.menu_loading))

    val knownSettings = settings
    loadJob = lifecycleScope.launch {
      val loaded = try {
        withContext(Dispatchers.IO) {
          val weeks = dataManager.loadWeeklyMeals()
          val previousId = WeekUtils.getPreviousWeek(weekId)
          LoadedWeek(
            plan = weeks.find { it.weekId == weekId } ?: WeeklyMeals(weekId),
            previous = weeks.find { it.weekId == previousId } ?: WeeklyMeals(previousId),
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
      savedWeeklyMeals = loaded.plan.deepCopy()
      previousWeekMeals = loaded.previous
      history = loaded.history
      if (settings == null) settings = loaded.settings
      bindWeek()
      binding.menuBoard.root.reveal()
      binding.prepareShoppingPanel.reveal()
      binding.btnMenuTools.isEnabled = true
      setSaveStatus(SaveStatus.Idle)
    }
  }

  private fun loadSuggestionSettings(): SuggestionSettings? = try {
    suggestionManager.load()
  } catch (_: IOException) {
    null
  }

  private fun showMenuError() {
    binding.btnMenuTools.isEnabled = false
    binding.menuBoard.root.isVisible = false
    binding.prepareShoppingPanel.isVisible = false
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

    spreadDayChips()
    renderSelectedDay()
    updateChipStates()
    updateWeekProgress()
  }

  /**
   * Gives each chip an equal share of the row so the week fills it. A chip that needs more room,
   * e.g. with an enlarged font, keeps its natural width and the row scrolls instead of wrapping.
   */
  private fun spreadDayChips() {
    binding.menuBoard.daySelectorScroll.doOnLayout { scroll ->
      val share = (scroll.width - scroll.paddingLeft - scroll.paddingRight) / WeeklyMeals.DAYS.size
      dayChips.forEach { it.binding.root.minimumWidth = share }
    }
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
    // Focus arrives before the keyboard shrinks the window, so scroll again once it has.
    binding.mainScroll.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
      if (bottom - top >= oldBottom - oldTop) return@addOnLayoutChangeListener
      listOf(binding.menuBoard.etComida, binding.menuBoard.etCena)
        .firstOrNull { it.hasFocus() }
        ?.let(::scrollFieldIntoView)
    }
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
    val filled = plan.plannedCount()
    val total = WeeklyMeals.DAYS.size * 2
    binding.tvWeekProgress.text =
      resources.getQuantityString(R.plurals.week_progress, total, filled, total)
    // The hint explains both disabled actions in words, not only by their color.
    binding.btnPrepareShopping.isEnabled = filled > 0
    binding.btnShareMenu.isEnabled = filled > 0 && !isSharingImage
    binding.tvPrepareShoppingHint.isVisible = filled == 0
  }

  /** Writes the visible week first, so the shopping screen reads exactly what is on screen. */
  private fun openShoppingPrep() {
    if (isOpeningShopping || isChangingWeek) return
    finishMealEditing()
    cancelPendingSave()
    isOpeningShopping = true
    val weekId = currentWeekId
    saveCurrentWeek { saved ->
      isOpeningShopping = false
      if (!saved || weekId != currentWeekId) return@saveCurrentWeek
      startActivity(
        Intent(this, MenuShoppingActivity::class.java)
          .putExtra(MenuShoppingActivity.EXTRA_WEEK_ID, weekId)
      )
    }
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
    val snapshot = currentWeeklyMeals?.deepCopy()
    if (snapshot == null) {
      onResult?.invoke(true)
      return
    }
    lifecycleScope.launch {
      val saved = withContext(NonCancellable) {
        saveMutex.withLock {
          withContext(Dispatchers.IO) {
            dataManager.saveWeeklyMeal(snapshot).also { saved ->
              // Inside the write, which outlives the screen, so closing the app still refreshes it.
              if (saved && snapshot.weekId == WeekUtils.getCurrentWeekId()) {
                TodayMenuWidget.requestRefresh(applicationContext)
              }
            }
          }
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

  private fun setSaveStatus(status: SaveStatus) {
    binding.tvSaveStatus.setText(status.label)
    binding.viewSaveDot.backgroundTintList =
      ColorStateList.valueOf(ContextCompat.getColor(this, status.dotColor))
  }

  private fun changeWeek(weekId: String, targetDayIndex: Int? = null) {
    if (weekId == currentWeekId || isChangingWeek || isOpeningShopping) return
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

  // COMPARTIR EL MENU

  private fun showShareOptions(anchor: View) {
    if ((currentWeeklyMeals?.plannedCount() ?: 0) == 0 || isSharingImage) return
    finishMealEditing()
    PopupMenu(this, anchor).apply {
      menu.add(Menu.NONE, SHARE_AS_TEXT, Menu.NONE, R.string.share_menu_as_text)
      menu.add(Menu.NONE, SHARE_AS_IMAGE, Menu.NONE, R.string.share_menu_as_image)
      setOnMenuItemClickListener { item ->
        when (item.itemId) {
          SHARE_AS_TEXT -> shareMenuAsText()
          SHARE_AS_IMAGE -> shareMenuAsImage()
        }
        true
      }
      show()
    }
  }

  /** "Lunes 24" for each day of the visible week. */
  private fun shareDayTitles(): List<String> {
    val start = WeekUtils.startOfWeek(currentWeekId)
    return WeeklyMeals.DAYS.mapIndexed { index, day ->
      getString(R.string.share_menu_day, day, start.plusDays(index.toLong()).format(dayNumberFormat))
    }
  }

  private fun shareMenuAsText() {
    val plan = currentWeeklyMeals ?: return
    val weekRange = WeekUtils.getWeekDisplay(currentWeekId)
    val labels = MenuShareLabels(
      header = getString(R.string.share_menu_header),
      lunch = getString(R.string.day_lunch),
      dinner = getString(R.string.day_dinner)
    )
    val text = plan.toShareText(labels, weekRange, shareDayTitles())
    val subject = getString(R.string.share_menu_subject, weekRange)
    if (!startShareChooser(textShareIntent(subject, text), getString(R.string.share_menu_chooser))) {
      binding.root.showSnackbar(getString(R.string.share_menu_error))
    }
  }

  /**
   * Draws the week as an image, writes it off the main thread and hands it to the share sheet.
   * The share button stays disabled meanwhile so the image is not prepared twice.
   */
  private fun shareMenuAsImage() {
    val plan = currentWeeklyMeals ?: return
    if (isSharingImage) return
    val weekRange = WeekUtils.getWeekDisplay(currentWeekId)
    setSharingImage(true)
    val bitmap = try {
      renderMenuCard(weekRange, plan.sharedDays(shareDayTitles(), empty = MENU_SHARE_EMPTY))
    } catch (_: OutOfMemoryError) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
    lifecycleScope.launch {
      val uri = bitmap?.let { image ->
        withContext(Dispatchers.IO) {
          try {
            cacheSharedImage(image, MENU_IMAGE_FILE)
          } catch (_: IOException) {
            null
          } catch (_: IllegalArgumentException) {
            null
          } finally {
            image.recycle()
          }
        }
      }
      setSharingImage(false)
      if (uri == null) {
        binding.root.showSnackbar(getString(R.string.share_menu_image_error))
        return@launch
      }
      val subject = getString(R.string.share_menu_subject, weekRange)
      if (!startShareChooser(imageShareIntent(uri, subject), getString(R.string.share_menu_chooser))) {
        binding.root.showSnackbar(getString(R.string.share_menu_error))
      }
    }
  }

  private fun setSharingImage(sharing: Boolean) {
    isSharingImage = sharing
    binding.btnShareMenu.contentDescription =
      getString(if (sharing) R.string.share_menu_preparing else R.string.share_menu)
    updateWeekProgress()
  }

  // COPIAR Y VACIAR EL MENU

  /** Each action says in its own title why it is disabled. */
  private fun showMenuTools(anchor: View) {
    val plan = currentWeeklyMeals ?: return
    if (isChangingWeek || isOpeningShopping) return
    val day = WeeklyMeals.DAYS.getOrNull(selectedDayIndex) ?: return
    finishMealEditing()
    val dayName = day.lowercase(spanish)
    val dayEmpty = plan.isDayEmpty(day)
    val weekEmpty = plan.plannedCount() == 0
    val previousEmpty = (previousWeekMeals?.plannedCount() ?: 0) == 0
    PopupMenu(this, anchor).apply {
      fun add(id: Int, enabled: Boolean, title: String) {
        menu.add(Menu.NONE, id, Menu.NONE, title).isEnabled = enabled
      }
      add(TOOL_COPY_DAY, !dayEmpty,
        getString(if (dayEmpty) R.string.menu_copy_day_empty else R.string.menu_copy_day, dayName))
      add(TOOL_COPY_PREVIOUS, !previousEmpty, getString(
        if (previousEmpty) R.string.menu_copy_previous_week_empty else R.string.menu_copy_previous_week))
      add(TOOL_CLEAR_DAY, !dayEmpty,
        getString(if (dayEmpty) R.string.menu_clear_day_empty else R.string.menu_clear_day, dayName))
      add(TOOL_CLEAR_WEEK, !weekEmpty,
        getString(if (weekEmpty) R.string.menu_clear_week_empty else R.string.menu_clear_week))
      setOnMenuItemClickListener { item ->
        when (item.itemId) {
          TOOL_COPY_DAY -> chooseCopyTargets(day)
          TOOL_COPY_PREVIOUS -> previousWeekMeals?.let { copyIntoWeek(MenuCopy.ofWeek(it)) }
          TOOL_CLEAR_DAY -> clearDay(day)
          TOOL_CLEAR_WEEK -> confirmClearWeek()
        }
        true
      }
      show()
    }
  }

  private fun chooseCopyTargets(from: String) {
    val plan = currentWeeklyMeals ?: return
    val weekId = currentWeekId
    val titles = shareDayTitles()
    val targets = WeeklyMeals.DAYS.filter { it != from }
    val checked = BooleanArray(targets.size)
    val labels = targets.map { day ->
      val title = titles[WeeklyMeals.DAYS.indexOf(day)]
      getString(
        if (plan.isDayEmpty(day)) R.string.menu_copy_target_empty else R.string.menu_copy_target_filled,
        title
      )
    }
    val dialog = ourmealDialog()
      .setTitle(getString(R.string.menu_copy_day_title, from.lowercase(spanish)))
      .setMultiChoiceItems(labels.toTypedArray(), checked) { dialog, which, isChecked ->
        checked[which] = isChecked
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = checked.any { it }
      }
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.menu_copy_confirm) { _, _ ->
        // Built when confirmed, so dishes typed while the dialog was open are copied too.
        val week = currentWeeklyMeals ?: return@setPositiveButton
        if (weekId != currentWeekId) return@setPositiveButton
        copyIntoWeek(MenuCopy.ofDay(week, from, targets.filterIndexed { index, _ -> checked[index] }))
      }
      .create()
    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false }
    dialog.show()
  }

  /** Fills blank meals directly; overwriting written ones is an explicit, confirmed choice. */
  private fun copyIntoWeek(copy: MenuCopy) {
    val plan = currentWeeklyMeals ?: return
    val preview = copy.preview(plan)
    when {
      preview.fillable + preview.replaceable == 0 ->
        binding.root.showSnackbar(getString(R.string.menu_copy_nothing))
      preview.replaceable == 0 -> applyCopy(copy, CopyMode.FILL_EMPTY)
      else -> {
        val builder = ourmealDialog(destructive = true)
          .setTitle(R.string.menu_copy_conflict_title)
          .setMessage(resources.getQuantityString(
            R.plurals.menu_copy_conflict_message, preview.replaceable, preview.replaceable))
          .setNegativeButton(R.string.cancel, null)
          .setPositiveButton(R.string.menu_copy_replace) { _, _ -> applyCopy(copy, CopyMode.REPLACE) }
        if (preview.fillable > 0) {
          builder.setNeutralButton(R.string.menu_copy_fill_only) { _, _ -> applyCopy(copy, CopyMode.FILL_EMPTY) }
        }
        builder.show()
      }
    }
  }

  private fun applyCopy(copy: MenuCopy, mode: CopyMode) {
    val plan = currentWeeklyMeals ?: return
    val edit = copy.applyTo(plan, mode)
    applyMenuEdit(edit, resources.getQuantityString(R.plurals.menu_copied, edit.changes.size, edit.changes.size))
  }

  private fun clearDay(day: String) {
    val plan = currentWeeklyMeals ?: return
    applyMenuEdit(plan.cleared(listOf(day)), getString(R.string.menu_cleared_day, day.lowercase(spanish)))
  }

  private fun confirmClearWeek() {
    val filled = currentWeeklyMeals?.plannedCount() ?: return
    if (filled == 0) return
    ourmealDialog(destructive = true)
      .setTitle(R.string.menu_clear_week_title)
      .setMessage(resources.getQuantityString(R.plurals.menu_clear_week_message, filled, filled))
      .setNegativeButton(R.string.cancel, null)
      .setPositiveButton(R.string.menu_clear_week_confirm) { _, _ ->
        val plan = currentWeeklyMeals ?: return@setPositiveButton
        applyMenuEdit(plan.cleared(), getString(R.string.menu_cleared_week))
      }
      .show()
  }

  /**
   * Shows [edit] and saves it like any typed change. With a [message], the user can undo it
   * while the same week is visible; meals edited again since keep the newer text.
   */
  private fun applyMenuEdit(edit: MenuEdit, message: String?) {
    if (edit.changes.isEmpty() || isChangingWeek) return
    val weekId = currentWeekId
    currentWeeklyMeals = edit.week
    renderSelectedDay()
    updateWeekProgress()
    scheduleSave()
    if (message != null) {
      binding.root.showSnackbar(
        message = message,
        actionLabel = getString(R.string.undo),
        onAction = { undoMenuEdit(weekId, edit.changes) }
      )
    }
  }

  private fun undoMenuEdit(weekId: String, changes: List<SlotChange>) {
    if (weekId != currentWeekId) return
    val plan = currentWeeklyMeals ?: return
    applyMenuEdit(plan.reverted(changes), message = null)
  }

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
    val previous: WeeklyMeals,
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

  companion object {
    /** Shows today's day of the current week; sent by the home screen widget. */
    const val ACTION_SHOW_TODAY = "ourmeal.app.action.SHOW_TODAY"
    private const val STATE_WEEK_ID = "week_id"
    private const val STATE_SELECTED_DAY = "selected_day"
    private const val WEEK_PICKER_TAG = "week_picker"
    private const val SAVE_DELAY_MS = 700L
    private const val SHARE_AS_TEXT = 1
    private const val SHARE_AS_IMAGE = 2
    private const val TOOL_COPY_DAY = 1
    private const val TOOL_COPY_PREVIOUS = 2
    private const val TOOL_CLEAR_DAY = 3
    private const val TOOL_CLEAR_WEEK = 4
    private const val MENU_SHARE_EMPTY = "—"
    private const val MENU_IMAGE_FILE = "menu.png"
    private val spanish: Locale = Locale.forLanguageTag("es-ES")
  }
}
