package ourmeal.app

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.DatePicker
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.ourmeat.R
import app.ourmeat.databinding.ActivityMainBinding
import ourmeal.app.controller.MealDataManager
import ourmeal.app.model.WeekUtils
import ourmeal.app.model.WeeklyMeals
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar

class MainActivity : AppCompatActivity() {

  private lateinit var binding: ActivityMainBinding

  private var currentWeekId: String = ""
  private var currentWeeklyMeals: WeeklyMeals? = null
  private lateinit var dataManager: MealDataManager

  private val dayViews = mutableMapOf<String, View>()
  private val days = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

  private val saveHandler = Handler(Looper.getMainLooper())
  private var saveRunnable: Runnable? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    setupData()
    setupListeners()
    setupCalendarButton()
    setupOpenListsButton()
    loadCurrentWeek()
  }

  private fun setupData() {
    dataManager = MealDataManager(this)
    currentWeekId = WeekUtils.getCurrentWeekId()
  }

  private fun setupListeners() {
    binding.btnPreviousWeek.setOnClickListener {
      saveCurrentWeek()
      currentWeekId = WeekUtils.getPreviousWeek(currentWeekId)
      loadCurrentWeek()
    }

    binding.btnNextWeek.setOnClickListener {
      saveCurrentWeek()
      currentWeekId = WeekUtils.getNextWeek(currentWeekId)
      loadCurrentWeek()
    }
  }

  private fun setupCalendarButton() {
    binding.btnOpenCalendar.setOnClickListener {
      val calendar = Calendar.getInstance()
      val year = calendar.get(Calendar.YEAR)
      val month = calendar.get(Calendar.MONTH)
      val day = calendar.get(Calendar.DAY_OF_MONTH)

      val datePickerDialog = DatePickerDialog(
        this,
        { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDay: Int ->
          val selectedCalendar = Calendar.getInstance().apply {
            set(selectedYear, selectedMonth, selectedDay)
          }
          currentWeekId = WeekUtils.getWeekIdFromDate(selectedCalendar.time)
          loadCurrentWeek()
        },
        year,
        month,
        day
      )
      datePickerDialog.show()
    }
  }

  private fun setupOpenListsButton() {
    binding.btnOpenLists.setOnClickListener {
      val intent = Intent(this, ListActivity::class.java)
      startActivity(intent)
    }
  }

  private fun loadCurrentWeek() {
    currentWeeklyMeals = dataManager.getWeeklyMeals(currentWeekId)
    updateUI()
    createDayViews()
  }

  private fun updateUI() {
    binding.tvWeekTitle.text = WeekUtils.getWeekDisplay(currentWeekId)
  }

  private fun createDayViews() {
    binding.layoutDays.removeAllViews()
    dayViews.clear()

    val inflater = LayoutInflater.from(this)

    days.forEach { day ->
      val dayView = inflater.inflate(R.layout.day_meal_item, binding.layoutDays, false)

      val tvDayName = dayView.findViewById<TextView>(R.id.tvDayName)
      val etComida = dayView.findViewById<TextInputEditText>(R.id.etComida)
      val etCena = dayView.findViewById<TextInputEditText>(R.id.etCena)

      tvDayName.text = day

      currentWeeklyMeals?.let { weeklyMeals ->
        val meal = weeklyMeals.getMealForDay(day)
        etComida.setText(meal.comida)
        etCena.setText(meal.cena)
      }

      setupTextWatcher(etComida, day, true)
      setupTextWatcher(etCena, day, false)

      binding.layoutDays.addView(dayView)
      dayViews[day] = dayView
    }
  }

  private fun setupTextWatcher(editText: TextInputEditText, day: String, isComida: Boolean) {
    editText.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
      override fun afterTextChanged(s: Editable?) {
        currentWeeklyMeals?.let { weeklyMeals ->
          val meal = weeklyMeals.getMealForDay(day)
          if (isComida) meal.comida = s.toString() else meal.cena = s.toString()
          weeklyMeals.setMealForDay(day, meal)
        }
        scheduleAutoSave()
      }
    })
  }

  private fun scheduleAutoSave() {
    saveRunnable?.let { saveHandler.removeCallbacks(it) }
    saveRunnable = Runnable { saveCurrentWeek() }
    saveRunnable?.let { saveHandler.postDelayed(it, 1000) }
  }

  private fun saveCurrentWeek() {
    currentWeeklyMeals?.let { weeklyMeals ->
      dataManager.saveWeeklyMeal(weeklyMeals)
    }
  }

  override fun onPause() {
    super.onPause()
    saveCurrentWeek()
  }

  override fun onDestroy() {
    super.onDestroy()
    saveCurrentWeek()
    saveRunnable?.let { saveHandler.removeCallbacks(it) }
  }
}
