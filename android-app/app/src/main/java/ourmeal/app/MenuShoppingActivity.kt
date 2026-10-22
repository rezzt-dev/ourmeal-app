package ourmeal.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import app.ourmeat.R
import app.ourmeat.databinding.ActivityMenuShoppingBinding
import app.ourmeat.databinding.ItemMenuDishBinding
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ourmeal.app.controller.DataManager
import ourmeal.app.controller.DishMemoryManager
import ourmeal.app.controller.MealDataManager
import ourmeal.app.controller.MergeOutcome
import ourmeal.app.model.DishDraft
import ourmeal.app.model.DishMemory
import ourmeal.app.model.IngredientEdit
import ourmeal.app.model.MealSlot
import ourmeal.app.model.MenuDish
import ourmeal.app.model.UserList
import ourmeal.app.model.WeekUtils
import ourmeal.app.model.WeeklyMeals
import ourmeal.app.model.hasEdits
import ourmeal.app.model.ingredientsToAdd
import ourmeal.app.model.memoryChanges
import ourmeal.app.model.normalizeName
import ourmeal.app.model.plannedDishes
import ourmeal.app.model.toDrafts
import ourmeal.app.model.withMergedItems
import java.io.IOException
import java.util.Locale

/** Turns the dishes of one week into products of a new or existing shopping list. */
class MenuShoppingActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMenuShoppingBinding
  private lateinit var mealManager: MealDataManager
  private lateinit var dataManager: DataManager
  private lateinit var memoryManager: DishMemoryManager
  private lateinit var exitGuard: OnBackPressedCallback
  private val gson = Gson()
  private val locale = Locale.forLanguageTag("es-ES")
  private var weekId = ""
  private var drafts: List<DishDraft> = emptyList()
  private val cards = mutableListOf<ItemMenuDishBinding>()
  private var lists: List<UserList> = emptyList()
  private var selectedListId: Long? = null
  /** False while the memory file cannot be read; a damaged file is then never written. */
  private var memoryAvailable = false
  /** Draft of a recreated screen, applied once the files have been read again. */
  private var restoredState: Bundle? = null
  private var loadJob: Job? = null
  private var isReady = false
  private var isSaving = false

  private val destination get() = binding.destinationSection

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )
    binding = ActivityMenuShoppingBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.root.applyScreenInsets()
    mealManager = MealDataManager(this)
    dataManager = DataManager(this)
    memoryManager = DishMemoryManager(this)
    restoredState = savedInstanceState
    weekId = intent.getStringExtra(EXTRA_WEEK_ID).orEmpty()

    exitGuard = object : OnBackPressedCallback(false) {
      override fun handleOnBackPressed() = requestExit()
    }
    onBackPressedDispatcher.addCallback(this, exitGuard)
    binding.btnBack.setOnClickListener { requestExit() }
    binding.btnConfirmShopping.setOnClickListener { confirm() }
    setupDestination()

    if (!WeekUtils.isValidWeekId(weekId)) {
      showInvalidWeek()
      return
    }
    binding.tvWeekRange.text = getString(R.string.menu_shopping_week, WeekUtils.getWeekDisplay(weekId))
    load()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    if (!isReady) {
      // Still loading after a recreation: keep the draft that has not been applied yet.
      restoredState?.let { saved ->
        STATE_KEYS.filter(saved::containsKey).forEach { key -> copyState(saved, outState, key) }
      }
      return
    }
    outState.putString(STATE_DRAFT, gson.toJson(drafts))
    outState.putString(
      STATE_DESTINATION,
      if (destination.rbExistingList.isChecked) DESTINATION_EXISTING else DESTINATION_NEW
    )
    outState.putString(STATE_NEW_NAME, destination.etNewListName.text?.toString().orEmpty())
    selectedListId?.let { outState.putLong(STATE_EXISTING_ID, it) }
  }

  private fun copyState(from: Bundle, to: Bundle, key: String) {
    when (key) {
      STATE_EXISTING_ID -> to.putLong(key, from.getLong(key))
      else -> to.putString(key, from.getString(key))
    }
  }

  // CARGA Y ESTADOS

  private fun load() {
    loadJob?.cancel()
    isReady = false
    updateExitGuard()
    showLoading()
    val weekId = weekId
    loadJob = lifecycleScope.launch {
      val loaded = try {
        withContext(Dispatchers.IO) {
          coroutineScope {
            val weeks = async { mealManager.loadWeeklyMeals() }
            val storedLists = async { dataManager.loadLists() }
            val memory = async { loadMemory() }
            LoadedData(
              dishes = (weeks.await().find { it.weekId == weekId } ?: WeeklyMeals(weekId)).plannedDishes(),
              lists = storedLists.await().sortedByDescending { it.createdAt },
              memory = memory.await()
            )
          }
        }
      } catch (_: IOException) {
        null
      }
      binding.loadingPanel.root.isVisible = false
      when {
        loaded == null -> showErrorState()
        loaded.dishes.isEmpty() -> showEmptyState()
        else -> showReady(loaded)
      }
    }
  }

  private fun loadMemory(): DishMemory? = try {
    memoryManager.load()
  } catch (_: IOException) {
    null
  }

  private fun showLoading() {
    hideContent()
    binding.statePanel.root.isVisible = false
    binding.loadingPanel.root.isVisible = true
    binding.loadingPanel.bindLoading(getString(R.string.menu_shopping_loading))
  }

  private fun hideContent() {
    binding.dishesSection.isVisible = false
    destination.root.isVisible = false
    binding.actionBar.isVisible = false
  }

  private fun showInvalidWeek() {
    binding.tvWeekRange.isVisible = false
    showStatePanel(
      title = getString(R.string.menu_shopping_invalid_week),
      body = "",
      icon = R.drawable.ic_alert,
      actionLabel = getString(R.string.back_to_menu),
      onAction = ::finish
    )
  }

  private fun showErrorState() = showStatePanel(
    title = getString(R.string.menu_shopping_error_title),
    body = getString(R.string.menu_shopping_error_body),
    icon = R.drawable.ic_alert,
    actionLabel = getString(R.string.retry),
    onAction = ::load
  )

  private fun showEmptyState() = showStatePanel(
    title = getString(R.string.menu_shopping_empty_title),
    body = getString(R.string.menu_shopping_empty_body),
    icon = R.drawable.ic_basket,
    actionLabel = getString(R.string.back_to_menu),
    onAction = ::finish
  )

  private fun showStatePanel(
    title: String,
    body: String,
    @DrawableRes icon: Int,
    actionLabel: String,
    onAction: () -> Unit
  ) {
    hideContent()
    binding.statePanel.root.reveal()
    binding.statePanel.bindState(title, body, icon, actionLabel, onAction)
  }

  private fun showReady(loaded: LoadedData) {
    val saved = restoredState
    restoredState = null
    lists = loaded.lists
    memoryAvailable = loaded.memory != null
    drafts = loaded.dishes.toDrafts(loaded.memory, saved?.let(::readDrafts).orEmpty())
    binding.tvMemoryUnavailable.isVisible = !memoryAvailable
    binding.tvDishCount.text =
      resources.getQuantityString(R.plurals.menu_shopping_dish_count, drafts.size, drafts.size)
    inflateCards()
    bindDestination(saved)
    isReady = true
    binding.statePanel.root.isVisible = false
    binding.dishesSection.reveal()
    destination.root.reveal()
    binding.actionBar.reveal()
    updateSummary()
    updateExitGuard()
  }

  private fun readDrafts(saved: Bundle): List<DishDraft> {
    val json = saved.getString(STATE_DRAFT) ?: return emptyList()
    return try {
      gson.fromJson(json, Array<DishDraft?>::class.java)?.filterNotNull().orEmpty()
    } catch (_: JsonParseException) {
      emptyList()
    }
  }

  // TARJETAS DE PLATO

  private fun inflateCards() {
    binding.layoutDishes.removeAllViews()
    cards.clear()
    drafts.forEachIndexed { index, draft ->
      val card = ItemMenuDishBinding.inflate(layoutInflater, binding.layoutDishes, false)
      // Drafts are the source of truth; the repeated ids of the cards must not restore each other.
      card.root.isSaveFromParentEnabled = false
      card.dishHeader.setOnClickListener { if (!isSaving) card.cbIncludeDish.toggle() }
      card.cbIncludeDish.setOnCheckedChangeListener { _, checked -> setIncluded(index, checked) }
      card.etIngredient.doAfterTextChanged {
        if (!drafts[index].isFull) card.ingredientField.error = null
      }
      card.etIngredient.setOnSubmitListener { addIngredient(index) }
      card.ingredientField.setEndIconOnClickListener { addIngredient(index) }
      card.ingredientField.endIconContentDescription =
        getString(R.string.menu_shopping_ingredient_add, draft.name)
      binding.layoutDishes.addView(card.root)
      cards += card
      bindCard(index)
    }
  }

  private fun bindCard(index: Int) {
    val draft = drafts[index]
    val card = cards[index]
    card.tvDishName.text = draft.name
    card.tvDishOccurrences.text = occurrencesText(draft)
    card.cbIncludeDish.contentDescription = getString(R.string.menu_shopping_include, draft.name)
    if (card.cbIncludeDish.isChecked != draft.included) card.cbIncludeDish.isChecked = draft.included
    card.cbIncludeDish.isEnabled = !isSaving
    card.dishHeader.isEnabled = !isSaving
    card.dishBody.isVisible = draft.included
    card.tvRememberedBadge.isVisible = draft.remembered != null && draft.ingredients.isNotEmpty()
    card.tvNoIngredients.isVisible = draft.ingredients.isEmpty()
    card.chipGroupIngredients.isVisible = draft.ingredients.isNotEmpty()
    card.chipGroupIngredients.removeAllViews()
    draft.ingredients.forEach { name ->
      val chip = layoutInflater.inflate(
        R.layout.item_ingredient_chip,
        card.chipGroupIngredients,
        false
      ) as Chip
      chip.text = name
      chip.closeIconContentDescription = getString(R.string.menu_shopping_ingredient_remove, name)
      chip.setOnCloseIconClickListener { removeIngredient(index, name) }
      chip.isEnabled = !isSaving
      card.chipGroupIngredients.addView(chip)
    }
    card.ingredientField.isEnabled = !isSaving && !draft.isFull
    val limit = getString(R.string.menu_shopping_ingredient_limit)
    if (draft.isFull) {
      card.ingredientField.error = limit
    } else if (card.ingredientField.error == limit) {
      card.ingredientField.error = null
    }
  }

  /** «Lunes, comida · Jueves, cena», with at most three days and the exclusion at the end. */
  private fun occurrencesText(draft: DishDraft): String {
    val parts = draft.occurrences.take(OCCURRENCES_SHOWN).map {
      getString(R.string.menu_shopping_occurrence, it.day, slotLabel(it.slot))
    }.toMutableList()
    val hidden = draft.occurrences.size - OCCURRENCES_SHOWN
    if (hidden > 0) parts += getString(R.string.menu_shopping_occurrence_more, hidden)
    if (!draft.included) parts += getString(R.string.menu_shopping_excluded)
    return parts.joinToString(" · ")
  }

  private fun slotLabel(slot: MealSlot): String = getString(
    when (slot) {
      MealSlot.COMIDA -> R.string.day_lunch
      MealSlot.CENA -> R.string.day_dinner
    }
  ).lowercase(locale)

  private fun setIncluded(index: Int, included: Boolean) {
    if (!isReady || isSaving) return
    val draft = drafts[index]
    if (draft.included == included) return
    updateDraft(index, draft.copy(included = included))
  }

  private fun addIngredient(index: Int) {
    if (!isReady || isSaving) return
    val card = cards[index]
    when (val edit = drafts[index].addIngredient(card.etIngredient.text?.toString().orEmpty())) {
      is IngredientEdit.Added -> {
        // Focus stays in the field so the next ingredient can be typed straight away.
        card.etIngredient.text = null
        card.ingredientField.error = null
        updateDraft(index, edit.draft)
        card.ingredientField.doOnLayout { field ->
          field.requestRectangleOnScreen(Rect(0, 0, field.width, field.height), false)
        }
      }
      IngredientEdit.Blank ->
        card.ingredientField.error = getString(R.string.menu_shopping_ingredient_required)
      IngredientEdit.Duplicate ->
        card.ingredientField.error = getString(R.string.menu_shopping_ingredient_exists)
      IngredientEdit.Full ->
        card.ingredientField.error = getString(R.string.menu_shopping_ingredient_limit)
    }
  }

  private fun removeIngredient(index: Int, name: String) {
    if (!isReady || isSaving) return
    val position = drafts[index].ingredients.indexOf(name)
    if (position < 0) return
    updateDraft(index, drafts[index].withoutIngredient(name))
    // Keyboard users keep their place: the next chip, or the field once none is left.
    if (!binding.root.isInTouchMode) {
      val card = cards[index]
      val chips = card.chipGroupIngredients
      (chips.getChildAt(position.coerceAtMost(chips.childCount - 1)) ?: card.etIngredient).requestFocus()
    }
  }

  private fun updateDraft(index: Int, draft: DishDraft) {
    drafts = drafts.toMutableList().also { it[index] = draft }
    bindCard(index)
    updateSummary()
    updateExitGuard()
  }

  // DESTINO

  private fun setupDestination() {
    destination.rgDestination.setOnCheckedChangeListener { _, _ ->
      renderDestination()
      updateSummary()
    }
    destination.etNewListName.doAfterTextChanged { updateSummary() }
    destination.actvExistingList.setOnItemClickListener { _, _, position, _ ->
      selectedListId = lists.getOrNull(position)?.id
      updateSummary()
    }
  }

  private fun bindDestination(saved: Bundle?) {
    val defaultName = getString(R.string.menu_shopping_default_list_name, WeekUtils.getWeekDisplay(weekId))
    destination.etNewListName.setText(saved?.getString(STATE_NEW_NAME) ?: defaultName)
    val savedId = saved?.takeIf { it.containsKey(STATE_EXISTING_ID) }?.getLong(STATE_EXISTING_ID)
    bindListOptions(savedId)
    // A list chosen before the recreation that no longer exists falls back to a new list.
    val keepExisting = saved?.getString(STATE_DESTINATION) == DESTINATION_EXISTING &&
      lists.isNotEmpty() && (savedId == null || selectedListId != null)
    destination.rgDestination.check(if (keepExisting) R.id.rbExistingList else R.id.rbNewList)
    renderDestination()
  }

  private fun bindListOptions(selected: Long?) {
    val hasLists = lists.isNotEmpty()
    destination.rbExistingList.isEnabled = hasLists && !isSaving
    destination.tvNoLists.isVisible = !hasLists
    destination.actvExistingList.setSimpleItems(lists.map(::listLabel).toTypedArray())
    selectedListId = selected?.takeIf { id -> lists.any { it.id == id } }
    val label = lists.firstOrNull { it.id == selectedListId }?.let(::listLabel).orEmpty()
    destination.actvExistingList.setText(label, false)
  }

  private fun listLabel(list: UserList): String = getString(
    R.string.menu_shopping_list_option,
    list.name,
    resources.getQuantityString(R.plurals.item_count, list.items.size, list.items.size)
  )

  private fun renderDestination() {
    val existing = destination.rbExistingList.isChecked
    destination.newListNameField.isVisible = !existing
    destination.existingListField.isVisible = existing
  }

  // RESUMEN Y CONFIRMACION

  private fun updateSummary() {
    if (!isReady) return
    val names = drafts.ingredientsToAdd()
    val existing = destination.rbExistingList.isChecked
    val target = if (existing) lists.firstOrNull { it.id == selectedListId } else null
    val preview = (target ?: blankList()).withMergedItems(names, 0L)
    val nameMissing = !existing &&
      normalizeName(destination.etNewListName.text?.toString().orEmpty()).isEmpty()

    binding.tvShoppingSummary.text = when {
      names.isEmpty() -> getString(R.string.menu_shopping_add_some)
      existing && target == null -> getString(R.string.menu_shopping_pick_list)
      preview.added == 0 -> getString(R.string.menu_shopping_nothing_new)
      preview.skipped > 0 -> getString(
        R.string.menu_shopping_summary_pair,
        resources.getQuantityString(R.plurals.menu_shopping_summary_new, preview.added, preview.added),
        resources.getQuantityString(R.plurals.menu_shopping_summary_skipped, preview.skipped, preview.skipped)
      )
      else -> resources.getQuantityString(R.plurals.menu_shopping_summary_new, preview.added, preview.added)
    }
    binding.btnConfirmShopping.text = if (isSaving) {
      getString(R.string.adding)
    } else {
      resources.getQuantityString(R.plurals.menu_shopping_confirm, preview.added, preview.added)
    }
    binding.btnConfirmShopping.isEnabled =
      !isSaving && preview.added > 0 && !nameMissing && (!existing || target != null)
    destination.newListNameField.error =
      if (nameMissing) getString(R.string.list_name_required) else null
  }

  /** Stands for the new list when previewing what would be added. */
  private fun blankList(): UserList = UserList(0L, "", mutableListOf(), createdAt = 0L, updatedAt = 0L)

  private fun confirm() {
    if (!isReady || isSaving || !binding.btnConfirmShopping.isEnabled) return
    val names = drafts.ingredientsToAdd()
    val existingId = if (destination.rbExistingList.isChecked) selectedListId ?: return else null
    val listName = normalizeName(destination.etNewListName.text?.toString().orEmpty())
    val changes = if (memoryAvailable) drafts.memoryChanges(System.currentTimeMillis()) else emptyList()
    setSaving(true)
    lifecycleScope.launch {
      // The list and then the memory are written even if the screen goes away meanwhile.
      val (outcome, memorySaved) = withContext(NonCancellable + Dispatchers.IO) {
        val outcome = if (existingId != null) {
          dataManager.mergeIntoList(existingId, names)
        } else {
          dataManager.createListWithItems(listName, names)
        }
        outcome to (outcome !is MergeOutcome.Saved || memoryManager.save(changes))
      }
      if (isFinishing || isDestroyed) return@launch
      when (outcome) {
        is MergeOutcome.Saved -> openList(outcome, memoryFailed = !memorySaved)
        MergeOutcome.ListMissing -> onListMissing()
        MergeOutcome.Failed -> {
          setSaving(false)
          binding.rootShopping.showSnackbar(
            message = getString(R.string.menu_shopping_save_error),
            actionLabel = getString(R.string.retry),
            anchor = binding.actionBar,
            onAction = ::confirm
          )
        }
      }
    }
  }

  private fun onListMissing() {
    lifecycleScope.launch {
      val fresh = try {
        withContext(Dispatchers.IO) { dataManager.loadLists() }
      } catch (_: IOException) {
        null
      }
      if (isFinishing || isDestroyed) return@launch
      val missingId = selectedListId
      lists = fresh?.sortedByDescending { it.createdAt } ?: lists.filterNot { it.id == missingId }
      bindListOptions(selected = null)
      setSaving(false)
      destination.rgDestination.check(R.id.rbNewList)
      binding.rootShopping.showSnackbar(
        message = getString(R.string.menu_shopping_list_missing),
        anchor = binding.actionBar
      )
    }
  }

  private fun openList(saved: MergeOutcome.Saved, memoryFailed: Boolean) {
    startActivity(
      Intent(this, ListDetailActivity::class.java)
        .putExtra(ListDetailActivity.EXTRA_LIST_ID, saved.listId)
        .putExtra(ListDetailActivity.EXTRA_ADDED_COUNT, saved.added)
        .putExtra(ListDetailActivity.EXTRA_SKIPPED_COUNT, saved.skipped)
        .putExtra(ListDetailActivity.EXTRA_MEMORY_FAILED, memoryFailed)
    )
    // Back from the list returns to the menu.
    finish()
  }

  private fun setSaving(saving: Boolean) {
    isSaving = saving
    binding.btnBack.isEnabled = !saving
    cards.forEachIndexed { index, card ->
      card.cbIncludeDish.isEnabled = !saving
      card.dishHeader.isEnabled = !saving
      card.ingredientField.isEnabled = !saving && !drafts[index].isFull
      card.chipGroupIngredients.children.forEach { it.isEnabled = !saving }
    }
    destination.rbNewList.isEnabled = !saving
    destination.rbExistingList.isEnabled = !saving && lists.isNotEmpty()
    destination.newListNameField.isEnabled = !saving
    destination.existingListField.isEnabled = !saving
    updateSummary()
    updateExitGuard()
  }

  // SALIDA

  private fun updateExitGuard() {
    exitGuard.isEnabled = isSaving || (isReady && drafts.hasEdits())
  }

  private fun requestExit() {
    if (isSaving) return
    if (!isReady || !drafts.hasEdits()) {
      finish()
      return
    }
    ourmealDialog(destructive = true)
      .setTitle(R.string.menu_shopping_discard_title)
      .setMessage(R.string.menu_shopping_discard_message)
      .setNegativeButton(R.string.menu_shopping_keep_editing, null)
      .setPositiveButton(R.string.menu_shopping_discard) { _, _ -> finish() }
      .show()
  }

  private class LoadedData(
    val dishes: List<MenuDish>,
    val lists: List<UserList>,
    val memory: DishMemory?
  )

  companion object {
    const val EXTRA_WEEK_ID = "WEEK_ID"
    private const val OCCURRENCES_SHOWN = 3
    private const val STATE_DRAFT = "shopping_draft"
    private const val STATE_DESTINATION = "shopping_destination"
    private const val STATE_NEW_NAME = "shopping_new_name"
    private const val STATE_EXISTING_ID = "shopping_existing_id"
    private val STATE_KEYS = listOf(STATE_DRAFT, STATE_DESTINATION, STATE_NEW_NAME, STATE_EXISTING_ID)
    private const val DESTINATION_NEW = "new"
    private const val DESTINATION_EXISTING = "existing"
  }
}
