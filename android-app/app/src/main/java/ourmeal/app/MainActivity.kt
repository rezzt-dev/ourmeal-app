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
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import app.ourmeat.R
import app.ourmeat.databinding.ActivityMainBinding
import app.ourmeat.databinding.DaySelectorChipBinding
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ourmeal.app.controller.MealDataManager
import ourmeal.app.model.WeekUtils
import ourmeal.app.model.WeeklyMeals
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
  private var currentWeekId = ""
  private var currentWeeklyMeals: WeeklyMeals? = null
  private var selectedDayIndex = 0
  private val dayChips = mutableListOf<DayChip>()
  private var loadJob: Job? = null
  private var isBindingMeals = false
  private val saveHandler = Handler(Looper.getMainLooper())
  private var saveRunnable: Runnable? = null
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
    binding.tvWeekTitle.text = WeekUtils.getWeekDisplay(weekId)
    binding.btnToday.isVisible = weekId != WeekUtils.getCurrentWeekId()
    binding.menuBoard.layoutDaySelector.removeAllViews()
    dayChips.clear()
    binding.menuBoard.root.isVisible = false
    binding.menuError.root.isVisible = false
    binding.menuLoading.root.isVisible = true
    binding.menuLoading.bindLoading(getString(R.string.menu_loading))

    loadJob = lifecycleScope.launch {
      val plan = try {
        withContext(Dispatchers.IO) { dataManager.getWeeklyMeals(weekId) }
      } catch (_: IOException) {
        null
      }
      if (weekId != currentWeekId) return@launch
      binding.menuLoading.root.isVisible = false
      if (plan == null) {
        showMenuError()
        return@launch
      }
      currentWeeklyMeals = plan
      bindWeek()
      binding.menuBoard.root.isVisible = true
      setSaveStatus(SaveStatus.Idle)
    }
  }

  private fun showMenuError() {
    binding.menuBoard.root.isVisible = false
    binding.menuError.root.isVisible = true
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
      chip.tvChipDay.text = day.take(2)
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

    isBindingMeals = true
    binding.menuBoard.etComida.setText(meal.comida)
    binding.menuBoard.etCena.setText(meal.cena)
    binding.menuBoard.etComida.setSelection(binding.menuBoard.etComida.text?.length ?: 0)
    binding.menuBoard.etCena.setSelection(binding.menuBoard.etCena.text?.length ?: 0)
    isBindingMeals = false
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
  }

  private fun setupMealFields() {
    watchMeal(binding.menuBoard.etComida, isLunch = true)
    watchMeal(binding.menuBoard.etCena, isLunch = false)
  }

  private fun watchMeal(input: AppCompatEditText, isLunch: Boolean) {
    input.setOnFocusChangeListener { view, hasFocus ->
      if (hasFocus) scrollFieldIntoView(view)
    }
    input.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
      override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
      override fun afterTextChanged(text: Editable?) {
        if (isBindingMeals) return
        val plan = currentWeeklyMeals ?: return
        val day = WeeklyMeals.DAYS.getOrNull(selectedDayIndex) ?: return
        val meal = plan.getMealForDay(day)
        if (isLunch) meal.comida = text?.toString().orEmpty() else meal.cena = text?.toString().orEmpty()
        plan.setMealForDay(day, meal)
        updateWeekProgress()
        scheduleSave()
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
    saveRunnable = Runnable { saveCurrentWeek() }.also { saveHandler.postDelayed(it, SAVE_DELAY_MS) }
  }

  private fun cancelPendingSave() {
    saveRunnable?.let(saveHandler::removeCallbacks)
    saveRunnable = null
  }

  private fun saveCurrentWeek(): Boolean {
    val plan = currentWeeklyMeals ?: return true
    val saved = dataManager.saveWeeklyMeal(plan)
    if (saved) {
      setSaveStatus(SaveStatus.Saved)
    } else {
      setSaveStatus(SaveStatus.Error)
      binding.root.showSnackbar(
        message = getString(R.string.save_error),
        actionLabel = getString(R.string.retry),
        onAction = { saveCurrentWeek() }
      )
    }
    return saved
  }

  private fun setSaveStatus(status: SaveStatus) {
    binding.tvSaveStatus.setText(status.label)
    binding.viewSaveDot.backgroundTintList =
      ColorStateList.valueOf(ContextCompat.getColor(this, status.dotColor))
  }

  private fun changeWeek(weekId: String, targetDayIndex: Int? = null): Boolean {
    if (weekId == currentWeekId) return true
    cancelPendingSave()
    if (!saveCurrentWeek()) return false
    currentWeekId = weekId
    targetDayIndex?.let { selectedDayIndex = it }
    binding.mainScroll.smoothScrollTo(0, 0)
    loadWeek()
    return true
  }

  private fun openWeekPicker() {
    if (supportFragmentManager.findFragmentByTag(WEEK_PICKER_TAG) != null) return
    val start = WeekUtils.startOfWeek(currentWeekId)
    val constraints = CalendarConstraints.Builder()
      .setFirstDayOfWeek(Calendar.MONDAY)
      .build()
    val picker = MaterialDatePicker.Builder.datePicker()
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
