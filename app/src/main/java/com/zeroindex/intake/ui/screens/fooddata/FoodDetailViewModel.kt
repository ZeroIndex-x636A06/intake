package com.zeroindex.intake.ui.screens.fooddata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.data.remote.openfoodfacts.FoodIngredients
import com.zeroindex.intake.data.remote.openfoodfacts.OffContribution
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.ContributionOutcome
import com.zeroindex.intake.data.repository.DrinkRepository
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.FoodRepository
import com.zeroindex.intake.data.repository.OffContributionRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.FoodDataPrefs
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nfc.TemplateLink
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FoodDetailUiState(
    val food: FoodEntity? = null,
    /** The local engine's verdict on this food, re-run whenever the food, the profile or the keywords change. */
    val scan: AllergenScanResult = AllergenScanResult.CLEAN,
    val settings: AppSettings = AppSettings(),
    val contributeEnabled: Boolean = false,
    val hasCredentials: Boolean = false,
    val message: String? = null,
    val sending: Boolean = false,
    /** True after a Log tap that the allergen check stopped: the screen shows the confirmation sheet. */
    val needsConfirm: Boolean = false,
)

@HiltViewModel
class FoodDetailViewModel @Inject constructor(
    private val foods: FoodRepository,
    private val foodLog: FoodLogRepository,
    private val drinks: DrinkRepository,
    private val allergens: AllergenRepository,
    private val contributions: OffContributionRepository,
    private val messages: AppMessages,
    private val clock: Clock,
    prefs: FoodDataPrefs,
    keys: ApiKeyStore,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val foodId = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val sending = MutableStateFlow(false)
    private val needsConfirm = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val foodAndScan = combine(
        foodId.flatMapLatest { id -> if (id == null) flowOf(null) else foods.observe(id) },
        allergens.observeAvoidedAllergens(),
        allergens.observeAliases(),
    ) { food, _, _ -> food }.mapLatest { food -> food to (food?.let { allergens.scan(it) } ?: AllergenScanResult.CLEAN) }

    val uiState: StateFlow<FoodDetailUiState> = combine(
        foodAndScan,
        prefs.settings,
        keys.state,
        combine(message, sending, needsConfirm) { m, s, n -> Triple(m, s, n) },
        settingsRepository.settings,
    ) { (food, scan), foodPrefs, keyState, flags, settings ->
        FoodDetailUiState(food, scan, settings, foodPrefs.offContribute, keyState.hasOffCredentials, flags.first, flags.second, flags.third)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodDetailUiState())

    fun load(id: String) { foodId.value = id }

    fun dismissMessage() { message.value = null }

    fun dismissConfirm() { needsConfirm.value = false }

    fun preview(food: FoodEntity, quantity: String): OffContribution = contributions.preview(food, quantity)

    /**
     * Logs [amount] (grams, or millilitres for a drink) of [food]. SPEC §7: the allergen engine runs here, on every
     * path, before anything is written. A match stops the log until [confirmed] is true (the red sheet's explicit tap).
     * A drink goes through the one drink flow, so the water and the calories are written together.
     */
    fun log(food: FoodEntity, amount: Double, slotName: String?, atMillis: Long, confirmed: Boolean, onLogged: () -> Unit) {
        viewModelScope.launch {
            val scan = allergens.scan(food)
            if (scan.hasAnything && !confirmed) {
                needsConfirm.value = true
                return@launch
            }
            needsConfirm.value = false
            val at = if (atMillis > 0) atMillis else clock.nowMillis()
            val slot = slotName?.let { n -> MealSlot.entries.firstOrNull { it.name == n } }
                ?: TemplateLink.slotFor(java.time.Instant.ofEpochMilli(at).atZone(clock.zone()).toLocalTime())
            if (food.drinkCategory != null) {
                val logged = drinks.log(food, amount, at, slot)
                messages.post(
                    AppMessage(
                        text = "Added ${amount.toInt()} ml ${food.name}",
                        actionLabel = "Undo",
                        onAction = { drinks.delete(logged.hydration.id) },
                    ),
                )
            } else {
                val entry = foodLog.logFood(food, amount, slot, at)
                messages.post(
                    AppMessage(
                        text = "Logged ${food.name} to ${slot.name.lowercase()}",
                        actionLabel = "Undo",
                        onAction = { foodLog.softDelete(entry.id) },
                    ),
                )
            }
            onLogged()
        }
    }

    /**
     * Saves the user's edits. Editing a product fetched from Open Food Facts makes it
     * the user's own version, which is what makes it eligible to contribute.
     */
    fun save(
        original: FoodEntity,
        name: String,
        brand: String,
        barcode: String,
        servingGrams: Double,
        servingLabel: String,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        fiber: Double,
        sugar: Double,
        sodiumMg: Double,
        saturatedFat: Double,
        ingredients: String,
        unlisted: String,
    ) = viewModelScope.launch {
        val tags = FoodIngredients.tags(original.allergenTags).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val edited = original.copy(
            name = name.trim(),
            brand = brand.trim().ifBlank { null },
            barcode = barcode.ifBlank { null },
            servingSizeGrams = servingGrams,
            servingLabel = servingLabel.trim().ifBlank { "${servingGrams.toInt()} g" },
            kcalPer100g = kcal,
            proteinPer100g = protein,
            carbsPer100g = carbs,
            fatPer100g = fat,
            fiberPer100g = fiber,
            sugarPer100g = sugar,
            sodiumPer100g = sodiumMg,
            saturatedFatPer100g = saturatedFat,
            allergenTags = FoodIngredients.join(tags, ingredients),
            unlistedNutrients = unlisted,
        )
        val changed = edited != original
        val withSource = if (changed && original.source == FoodSource.OPEN_FOOD_FACTS && !edited.barcode.isNullOrBlank()) {
            edited.copy(source = FoodSource.BARCODE)
        } else {
            edited
        }
        foods.save(withSource)
        message.value = "Saved."
    }

    fun remove(food: FoodEntity, done: () -> Unit) = viewModelScope.launch {
        foods.softDelete(food.id)
        done()
    }

    /** Only ever called from the confirmation dialog's button. */
    fun contribute(food: FoodEntity, quantity: String) = viewModelScope.launch {
        sending.value = true
        message.value = when (val outcome = contributions.contribute(food, quantity)) {
            ContributionOutcome.Sent -> "Sent to Open Food Facts. Thank you."
            ContributionOutcome.NoCredentials -> "Add your Open Food Facts account in Food database settings first."
            ContributionOutcome.NotEnabled -> "Contributing is switched off in Food database settings."
            ContributionOutcome.NotEligible -> "This food cannot be contributed: it needs a barcode and nutrition values."
            is ContributionOutcome.Rejected -> "Open Food Facts did not accept it: ${outcome.message}"
            is ContributionOutcome.Failed -> outcome.message
        }
        sending.value = false
    }
}
