package com.zeroindex.intake.ui.screens.drinks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.foodref.FoodRefRepository
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.CustomDrinkDraft
import com.zeroindex.intake.data.repository.CustomDrinkFood
import com.zeroindex.intake.data.repository.DrinkRepository
import com.zeroindex.intake.data.repository.FoodRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.drinks.AddIn
import com.zeroindex.intake.domain.drinks.AddInCatalog
import com.zeroindex.intake.domain.drinks.AddInUnit
import com.zeroindex.intake.domain.drinks.CustomDrinkRecipe
import com.zeroindex.intake.domain.drinks.CustomDrinkResult
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An add-in found by searching: a food of the person's, or one from the bundled USDA database (nothing is saved for it). */
data class AddInFood(val key: String, val food: FoodEntity)

data class CustomDrinkUi(
    val loaded: Boolean = false,
    /** True when the drink to start from could not be found (or its base drink was removed). */
    val missing: Boolean = false,
    val base: FoodEntity? = null,
    val baseVolumeMl: Double = 240.0,
    val addIns: List<AddIn> = emptyList(),
    /** What the person typed for the name; blank uses the generated one ("Black coffee + 3 tsp sugar"). */
    val typedName: String = "",
    val editingId: String? = null,
    val volumeUnit: VolumeUnit = VolumeUnit.MILLILITRES,
    val searchQuery: String = "",
    val searchResults: List<AddInFood> = emptyList(),
    val pending: PendingDrink? = null,
) {
    val draft: CustomDrinkDraft? get() = base?.let { CustomDrinkDraft(it, baseVolumeMl, addIns) }
    val result: CustomDrinkResult? get() = draft?.let(CustomDrinkFood::result)
    /** True when the base is a plain tea or coffee whose saved numbers were impossible for a brew (see `PlainBrew`). */
    val baseReplaced: Boolean get() = base?.let(CustomDrinkFood::baseReplaced) ?: false
    val shownName: String get() = typedName.trim().ifBlank { draft?.let(CustomDrinkFood::defaultName).orEmpty() }
}

/**
 * Builds a custom version of a drink: pick add-ins with amounts, see the nutrition, then log it once or save it as a
 * "My drink". The maths is `domain/drinks/CustomDrink`; this only holds the draft. Logging runs the local allergen
 * check on the finished drink (the base drink's allergens and every add-in's) before anything is written.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CustomDrinkViewModel @Inject constructor(
    private val drinks: DrinkRepository,
    private val foodRepository: FoodRepository,
    private val foodRefRepository: FoodRefRepository,
    private val allergens: AllergenRepository,
    private val settingsRepository: SettingsRepository,
    private val messages: AppMessages,
) : ViewModel() {

    private val draftState = MutableStateFlow(CustomDrinkUi())
    private val query = MutableStateFlow("")
    private var loadedFor: String? = null

    private val results = query.debounce(250).flatMapLatest { q ->
        flow {
            if (q.trim().length < 2) {
                emit(emptyList())
            } else {
                val mine = foodRepository.search(q.trim()).first().take(10).map { AddInFood("food:${it.id}", it) }
                val usda = runCatching { foodRefRepository.search(q.trim(), limit = 10) }.getOrDefault(emptyList())
                    .map { AddInFood("usda:${it.id}", FoodRefRepository.toFoodEntity(it)) }
                emit(mine + usda)
            }
        }
    }

    val state: StateFlow<CustomDrinkUi> = combine(draftState, query, results, settingsRepository.settings) { d, q, r, s ->
        d.copy(searchQuery = q, searchResults = r, volumeUnit = s.volumeUnit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomDrinkUi())

    /** [foodId] is a drink to customise, or a saved "My drink" to edit. Safe to call again with the same id. */
    fun load(foodId: String) {
        if (loadedFor == foodId) return
        loadedFor = foodId
        viewModelScope.launch {
            val food = drinks.findFood(foodId)
            if (food == null) {
                draftState.value = CustomDrinkUi(loaded = true, missing = true)
                return@launch
            }
            val recipe = CustomDrinkRecipe.decode(food.customDrink)
            if (recipe == null) {
                draftState.value = CustomDrinkUi(loaded = true, base = food, baseVolumeMl = food.servingSizeGrams.coerceIn(10.0, 2000.0))
            } else {
                val base = drinks.findFood(recipe.baseFoodId)
                draftState.value = if (base == null) {
                    CustomDrinkUi(loaded = true, missing = true)
                } else {
                    CustomDrinkUi(
                        loaded = true, base = base, baseVolumeMl = recipe.baseVolumeMl, addIns = recipe.addIns,
                        typedName = food.name, editingId = food.id,
                    )
                }
            }
        }
    }

    private fun edit(block: (CustomDrinkUi) -> CustomDrinkUi) { draftState.value = block(draftState.value) }

    fun setBaseVolumeMl(ml: Double) = edit { it.copy(baseVolumeMl = ml.coerceIn(10.0, 5000.0)) }
    fun setName(text: String) = edit { it.copy(typedName = text.take(120)) }
    fun setQuery(text: String) { query.value = text }

    fun addBuiltIn(key: String) {
        val option = AddInCatalog.byKey(key) ?: return
        edit { it.copy(addIns = it.addIns + option.toAddIn(option.defaultAmount)) }
    }

    /** Any food from the search: grams of it (millilitres for a drink), starting at its own serving. */
    fun addFood(found: AddInFood) {
        val f = found.food
        val isDrink = f.drinkCategory != null
        val amount = f.servingSizeGrams.coerceIn(1.0, 500.0)
        val add = AddIn(
            key = found.key, name = f.name, amount = amount, unit = (if (isDrink) AddInUnit.ML else AddInUnit.GRAM).name,
            grams = amount, volumeMl = if (isDrink) amount else 0.0,
            kcal = f.kcalPer100g, protein = f.proteinPer100g, carbs = f.carbsPer100g, fat = f.fatPer100g, fiber = f.fiberPer100g,
            sugar = f.sugarPer100g, sodium = f.sodiumPer100g, saturatedFat = f.saturatedFatPer100g,
            allergenTags = f.allergenTags, unlisted = f.unlistedNutrients,
        )
        query.value = ""
        edit { it.copy(addIns = it.addIns + add) }
    }

    fun setAmount(index: Int, amount: Double, unit: AddInUnit? = null) = edit { ui ->
        val old = ui.addIns.getOrNull(index) ?: return@edit ui
        val u = unit ?: AddInUnit.fromStored(old.unit)
        val a = amount.coerceIn(0.1, 2000.0)
        val option = AddInCatalog.byKey(old.key)
        val updated = if (option != null) {
            option.toAddIn(a, u)
        } else {
            // A food from search is in grams (or ml): the amount is the weight.
            old.copy(amount = a, grams = a, volumeMl = if (old.volumeMl > 0) a else 0.0)
        }
        ui.copy(addIns = ui.addIns.toMutableList().also { it[index] = updated })
    }

    fun remove(index: Int) = edit { ui -> ui.copy(addIns = ui.addIns.filterIndexed { i, _ -> i != index }) }

    /** Logs the finished drink once. The allergen check comes first; a match waits for the red sheet's answer. */
    fun logOnce(onLogged: () -> Unit) {
        val ui = draftState.value
        val draft = ui.draft ?: return
        viewModelScope.launch {
            val spec = CustomDrinkFood.build(draft, ui.shownName, draft.base.id, 0L)
            val scan = allergens.scan(spec)
            if (scan.hasAnything) {
                draftState.value = ui.copy(pending = PendingDrink(spec, CustomDrinkFood.result(draft).totalVolumeMl, scan))
            } else {
                write(draft, ui.shownName, onLogged)
            }
        }
    }

    fun confirmPending(onLogged: () -> Unit) {
        val ui = draftState.value
        val draft = ui.draft ?: return
        draftState.value = ui.copy(pending = null)
        viewModelScope.launch { write(draft, ui.shownName, onLogged) }
    }

    fun dismissPending() = edit { it.copy(pending = null) }

    private suspend fun write(draft: CustomDrinkDraft, name: String, onLogged: () -> Unit) {
        val logged = drinks.logCustom(draft)
        val volume = CustomDrinkFood.result(draft).totalVolumeMl
        messages.post(
            AppMessage(
                text = "Added ${settingsRepository.settings.first().volumeUnit.format(volume)} $name",
                actionLabel = "Undo",
                onAction = { drinks.delete(logged.hydration.id) },
            ),
        )
        onLogged()
    }

    /** Saves it as a "My drink" (or updates the one being edited). */
    fun save(onSaved: () -> Unit) {
        val ui = draftState.value
        val draft = ui.draft ?: return
        viewModelScope.launch {
            val saved = drinks.saveCustom(draft, ui.shownName, ui.editingId)
            messages.post(AppMessage("Saved ${saved.name} to My drinks"))
            onSaved()
        }
    }

    /** Soft delete of the saved drink being edited, with an Undo. */
    fun deleteSaved(onDone: () -> Unit) {
        val id = draftState.value.editingId ?: return
        val name = draftState.value.shownName
        viewModelScope.launch {
            drinks.deleteMyDrink(id)
            messages.post(AppMessage("Removed $name", "Undo") { drinks.restoreMyDrink(id) })
            onDone()
        }
    }
}
