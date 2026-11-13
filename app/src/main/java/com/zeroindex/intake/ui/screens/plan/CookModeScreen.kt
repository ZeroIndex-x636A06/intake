package com.zeroindex.intake.ui.screens.plan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.dao.RecipeDao
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.recipe.RecipeNutritionEstimator
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.IngredientView
import com.zeroindex.intake.data.repository.RecipeLogger
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.recipe.IngredientScaling
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import com.zeroindex.intake.ui.addfood.PendingRecipeLog
import com.zeroindex.intake.ui.allergen.AllergenWarningSheet
import com.zeroindex.intake.ui.screens.today.SlotPickerSheet
import com.zeroindex.intake.data.repository.RecipeRepository
import com.zeroindex.intake.domain.recipe.IngredientSections
import com.zeroindex.intake.domain.plan.CookSteps
import com.zeroindex.intake.domain.plan.StepTimer
import com.zeroindex.intake.timer.CookTimer
import com.zeroindex.intake.timer.CookTimerService
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.theme.IntakeShape
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CookModeViewModel @Inject constructor(
    private val recipeDao: RecipeDao,
    private val recipes: RecipeRepository,
    private val logger: RecipeLogger,
    private val estimator: RecipeNutritionEstimator,
    private val foodLog: FoodLogRepository,
    private val messages: AppMessages,
) : ViewModel() {
    private val _ingredients = MutableStateFlow<List<IngredientView>>(emptyList())
    val ingredients: StateFlow<List<IngredientView>> = _ingredients.asStateFlow()
    private val _recipe = MutableStateFlow<RecipeEntity?>(null)
    val recipe: StateFlow<RecipeEntity?> = _recipe.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _pending = MutableStateFlow<PendingRecipeLog?>(null)
    val pending: StateFlow<PendingRecipeLog?> = _pending.asStateFlow()

    /**
     * "Done cooking, log a serving": the same recipe logging path as the recipe screen and Log food. The allergen scan
     * runs first and a match waits behind the warning sheet. [onLogged] runs once a serving is saved.
     */
    fun requestLog(slot: MealSlot, onLogged: () -> Unit) {
        val recipe = _recipe.value ?: return
        viewModelScope.launch {
            val scan = logger.scan(recipe)
            if (scan.hasAnything) {
                _pending.value = PendingRecipeLog(recipe.id, recipe.name, scan, recipe.ingredientsText.takeIf { it.isNotBlank() })
            } else if (logNow(recipe, slot)) {
                onLogged()
            }
        }
    }

    fun confirmPending(slot: MealSlot, onLogged: () -> Unit) {
        val recipe = _recipe.value ?: return
        _pending.value = null
        viewModelScope.launch { if (logNow(recipe, slot)) onLogged() }
    }

    fun dismissPending() { _pending.value = null }

    private suspend fun logNow(recipe: RecipeEntity, slot: MealSlot): Boolean {
        // Protein, carbs or fat the recipe lacks come from the on-phone estimate when there is one, as on its own screen.
        val missing = recipe.kcalPerServing == null || recipe.proteinPerServing == null ||
            recipe.carbsPerServing == null || recipe.fatPerServing == null
        val lines = _ingredients.value.map { it.text }
        val estimate = if (missing && lines.isNotEmpty()) {
            runCatching { estimator.estimate(lines, recipe.servings).perServing }.getOrNull()
        } else {
            null
        }
        val entry = logger.logServings(recipe, slot, estimate)
        if (entry == null) {
            messages.post(AppMessage("${recipe.name} has no calories listed, so a serving was not logged."))
            return false
        }
        messages.post(AppMessage("Logged ${recipe.name} to ${slot.name.lowercase()}", "Undo") { foodLog.softDelete(entry.id) })
        return true
    }

    fun load(recipeId: String) {
        viewModelScope.launch {
            _recipe.value = recipeDao.findById(recipeId)
            _ingredients.value = runCatching { recipes.view(recipeId)?.ingredients.orEmpty() }.getOrDefault(emptyList())
            _loaded.value = true
        }
    }
}

/**
 * Cook mode (SPEC §9): full screen, screen kept awake, one step at a time in
 * large type, swipe between steps. Used with greasy hands, so every control is
 * at least 72dp and nothing needs precision.
 */
@Composable
fun CookModeScreen(
    recipeId: String,
    contentPadding: PaddingValues,
    onClose: () -> Unit,
    viewModel: CookModeViewModel = hiltViewModel(),
) {
    LaunchedEffect(recipeId) { viewModel.load(recipeId) }
    val recipe by viewModel.recipe.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val ingredients by viewModel.ingredients.collectAsStateWithLifecycle()

    // The screen stays on only while cook mode is showing.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val steps = remember(recipe) { recipe?.let { CookSteps.split(it.instructions) }.orEmpty() }
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    var finished by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var pickedSlot by remember { mutableStateOf(MealSlot.DINNER) }
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        if (recipe == null || steps.isEmpty()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    if (!loaded) "Loading…" else "No steps to cook from",
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (loaded) Text("This recipe has no saved steps.", style = MaterialTheme.typography.bodyLarge)
                BigButton("Close", onClose, Modifier.fillMaxWidth(), primary = true)
            }
        } else {
            CookPager(recipe!!, steps, ingredients, onClose, onFinished = { finished = true })
        }
    }

    if (finished && recipe != null) {
        AlertDialog(
            onDismissRequest = { finished = false },
            title = { Text("Done cooking") },
            text = { Text("Log a serving of ${recipe!!.name}? You can change the amount later on Today.") },
            confirmButton = { TextButton(onClick = { finished = false; picking = true }) { Text("Log a serving") } },
            dismissButton = { TextButton(onClick = onClose) { Text("Close without logging") } },
        )
    }
    if (picking) {
        SlotPickerSheet(
            title = "Log a serving to",
            onDismiss = { picking = false },
            onPick = { slot ->
                picking = false
                pickedSlot = slot
                viewModel.requestLog(slot, onClose)
            },
        )
    }
    pending?.let { p ->
        AllergenWarningSheet(
            foodName = p.name,
            scan = p.scan,
            ingredientText = p.ingredientText,
            onDismiss = viewModel::dismissPending,
            onLogAnyway = { viewModel.confirmPending(pickedSlot, onClose) },
        )
    }
}

@Composable
private fun CookPager(
    recipe: RecipeEntity,
    steps: List<String>,
    ingredients: List<IngredientView>,
    onClose: () -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    // The checklist is scaled to the servings chosen here (the recipe's own servings to start); ticks survive rotation.
    var servings by rememberSaveable(recipe.id) { mutableStateOf(recipe.servings.coerceAtLeast(1)) }
    var ticked by rememberSaveable(recipe.id) { mutableStateOf(setOf<String>()) }
    var checklistOpen by rememberSaveable(recipe.id) { mutableStateOf(true) }
    val factor = IngredientScaling.factor(servings, recipe.servings)
    val pager = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    val timers by CookTimerService.timers.collectAsStateWithLifecycle()
    var notificationsDenied by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf<Triple<String, String, Int>?>(null) }

    // Remaining time comes from each timer's deadline; this only repaints it.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); delay(250) } }

    // A timer seen running that is no longer in the service has finished or been cancelled.
    val seen = remember { mutableMapOf<String, CookTimer>() }
    val finished = remember { mutableStateOf(setOf<String>()) }
    timers.forEach { seen[it.id] = it }
    seen.values.toList().forEach { t ->
        if (timers.none { it.id == t.id }) {
            if (t.endsAtElapsed <= now + 1000) finished.value = finished.value + t.id
            seen.remove(t.id)
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsDenied = !granted
        // The timer runs either way; only the notification is lost.
        waiting?.let { (id, label, seconds) -> CookTimerService.start(context, id, label, seconds) }
        waiting = null
    }
    fun startTimer(id: String, label: String, seconds: Int) {
        finished.value = finished.value - id
        val needsAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk && !notificationsDenied) {
            waiting = Triple(id, label, seconds)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (needsAsk) notificationsDenied = true
            CookTimerService.start(context, id, label, seconds)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onClose,
                shape = IntakeShape.Button,
                modifier = Modifier.size(72.dp).semantics { contentDescription = "Close cook mode" },
            ) { Text("×", fontSize = 28.sp) }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Eyebrow("Step ${pager.currentPage + 1} of ${steps.size}")
                Text("◉ Screen stays awake", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LinearProgressIndicator(
            progress = { (pager.currentPage + 1) / steps.size.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        if (notificationsDenied) {
            SandNote("Notifications are off, so timers only show here and will not chime when you leave the app.")
        }

        if (ingredients.isNotEmpty()) {
            Checklist(
                ingredients = ingredients,
                factor = factor,
                servings = servings,
                onServings = { servings = it.coerceIn(1, 99) },
                ticked = ticked,
                onToggle = { key -> ticked = if (key in ticked) ticked - key else ticked + key },
                open = checklistOpen,
                onOpen = { checklistOpen = it },
            )
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            val text = steps[page]
            val durations = remember(text) { CookSteps.durations(text) }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Eyebrow(recipe.name)
                Text(text, fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
                durations.forEachIndexed { i, d ->
                    val id = "${recipe.id}:$page:$i"
                    TimerChip(
                        step = d,
                        timer = timers.firstOrNull { it.id == id },
                        done = id in finished.value,
                        now = now,
                        onStart = { startTimer(id, "Step ${page + 1} · ${recipe.name}", d.seconds) },
                        onCancel = { CookTimerService.cancel(context, id) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Swipe left or right to move between steps",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("←", { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } },
                Modifier.size(width = 88.dp, height = 72.dp), description = "Previous step")
            if (pager.currentPage == steps.lastIndex) {
                BigButton("Done cooking", onFinished, Modifier.weight(1f), primary = true)
            } else {
                BigButton("Next step", { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.weight(1f), primary = true)
            }
        }
    }
}

/**
 * The ingredient checklist at the top of cook mode: scaled to the servings chosen (IngredientScaling), grouped as the
 * recipe groups them, each line ticked off as it goes in. It scrolls inside a limited height and can be folded away so
 * the step below gets the screen.
 */
@Composable
private fun Checklist(
    ingredients: List<IngredientView>,
    factor: Double,
    servings: Int,
    onServings: (Int) -> Unit,
    ticked: Set<String>,
    onToggle: (String) -> Unit,
    open: Boolean,
    onOpen: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Ingredients · ${ticked.size} of ${ingredients.size} ready")
            }
            OutlinedButton(
                onClick = { onServings(servings - 1) },
                shape = IntakeShape.Button,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Fewer servings" },
                contentPadding = PaddingValues(0.dp),
            ) { Text("−", fontSize = 22.sp) }
            Text(
                "$servings ${if (servings == 1) "serving" else "servings"}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            OutlinedButton(
                onClick = { onServings(servings + 1) },
                shape = IntakeShape.Button,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "More servings" },
                contentPadding = PaddingValues(0.dp),
            ) { Text("+", fontSize = 22.sp) }
            TextButton(onClick = { onOpen(!open) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (open) "Hide" else "Show") }
        }
        if (open) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
            ) {
                // Group headers stay, so "butter" under the chicken and "butter" under the sauce are not confused.
                IngredientSections.of(ingredients) { it.group }.forEach { section ->
                    section.label?.let {
                        Text(it, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    }
                    section.items.forEach { line ->
                        val done = line.key in ticked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle(line.key) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = done, onCheckedChange = null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                IngredientScaling.scaleLine(line.text, factor),
                                fontSize = 20.sp,
                                lineHeight = 26.sp,
                                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerChip(step: StepTimer, timer: CookTimer?, done: Boolean, now: Long, onStart: () -> Unit, onCancel: () -> Unit) {
    val running = timer != null
    val label = when {
        running -> "⏱ ${CookSteps.formatClock(timer!!.remainingSeconds(now))} · tap to cancel"
        done -> "✓ ${CookSteps.formatShort(step.seconds)} timer finished · tap to run again"
        else -> "⏱ Start ${CookSteps.formatShort(step.seconds)} timer"
    }
    val modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
    if (running) {
        OutlinedButton(onClick = onCancel, shape = IntakeShape.Button, modifier = modifier) {
            Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        Button(onClick = onStart, shape = IntakeShape.Button, modifier = modifier) {
            Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BigButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    description: String? = null,
) {
    val m = modifier.heightIn(min = 72.dp).let { if (description != null) it.semantics { contentDescription = description } else it }
    if (primary) {
        Button(onClick = onClick, shape = IntakeShape.Button, modifier = m) { Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(onClick = onClick, shape = IntakeShape.Button, modifier = m) { Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}
