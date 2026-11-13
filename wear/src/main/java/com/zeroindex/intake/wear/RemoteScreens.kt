package com.zeroindex.intake.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.zeroindex.intake.wearprotocol.PlannedMeal
import com.zeroindex.intake.wearprotocol.RemoteProtocol
import com.zeroindex.intake.wearprotocol.RemoteRequest
import com.zeroindex.intake.wearprotocol.RemoteResponse
import com.zeroindex.intake.wearprotocol.RemoteState
import com.zeroindex.intake.wearprotocol.RequestKind
import com.zeroindex.intake.wearprotocol.ResponseStatus
import com.zeroindex.intake.wearprotocol.WatchFood
import com.zeroindex.intake.wearprotocol.WatchSlot
import com.zeroindex.intake.wearprotocol.pendingLine
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

private enum class Screen { HOME, WATER, FOOD, RECENTS, FAVOURITES, MEALS, QUICK_ADD, WORKOUT }

/**
 * The watch home when no workout is running: a quick remote for when the phone is not in the hand. Every action is a
 * request to the phone, which logs it through its own paths (allergen check included) and answers; with the phone out of
 * reach the request waits and shows as "pending, will sync". Nothing here logs on its own or decides allergen safety.
 */
@Composable
internal fun RemoteHome(remote: WatchRemote, connected: Boolean, workoutRunning: Boolean) {
    val state by remote.state.collectAsStateWithLifecycle()
    val pending by remote.pending.collectAsStateWithLifecycle()
    val notice by remote.notice.collectAsStateWithLifecycle()
    var screenName by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    var slotName by rememberSaveable { mutableStateOf(WatchSlot.forHour(LocalTime.now().hour).name) }
    val screen = Screen.valueOf(screenName)
    val slot = WatchSlot.valueOf(slotName)
    val s = state

    fun go(to: Screen) { screenName = to.name }
    fun send(request: RemoteRequest) {
        remote.send(request)
        go(Screen.HOME)
    }
    fun request(kind: RequestKind, label: String, fill: (RemoteRequest) -> RemoteRequest = { it }) =
        fill(RemoteRequest(UUID.randomUUID().toString(), kind, System.currentTimeMillis(), label = label))

    BackHandler(enabled = screen != Screen.HOME) {
        go(
            when (screen) {
                Screen.RECENTS, Screen.FAVOURITES, Screen.MEALS, Screen.QUICK_ADD -> Screen.FOOD
                else -> Screen.HOME
            },
        )
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                state = s, connected = connected, workoutRunning = workoutRunning,
                pendingLines = pending.values.sortedBy { it.at }.map(::pendingLine),
                notice = notice, onDismissNotice = remote::dismissNotice, onGo = ::go,
            )
            Screen.WATER -> WaterScreen(s, onBack = { go(Screen.HOME) }) { ml, presetId, label ->
                send(request(RequestKind.WATER, label) { it.copy(volumeMl = ml, presetId = presetId) })
            }
            Screen.FOOD -> FoodScreen(
                s, slot, onSlot = { slotName = WatchSlot.entries[(slot.ordinal + 1) % WatchSlot.entries.size].name },
                onBack = { go(Screen.HOME) }, onGo = ::go,
                onPlanned = { send(request(RequestKind.LOG_PLANNED, "Log ${it.name}") { r -> r.copy(slot = slot) }) },
            )
            Screen.RECENTS -> FoodList("Recent", s?.recents.orEmpty(), "Nothing logged on the phone yet.", slot, onBack = { go(Screen.FOOD) }) { f -> send(foodRequest(f, slot)) }
            Screen.FAVOURITES -> FoodList("Favourites", s?.favourites.orEmpty(), "Star foods on the phone to find them here.", slot, onBack = { go(Screen.FOOD) }) { f -> send(foodRequest(f, slot)) }
            Screen.MEALS -> MealsScreen(s, slot, onBack = { go(Screen.FOOD) }) { t ->
                send(request(RequestKind.LOG_TEMPLATE, "Log ${t.name}") { it.copy(templateId = t.id, slot = slot) })
            }
            Screen.QUICK_ADD -> QuickAddScreen(slot, onSlot = { slotName = WatchSlot.entries[(slot.ordinal + 1) % WatchSlot.entries.size].name }, onBack = { go(Screen.FOOD) }) { kcal ->
                send(request(RequestKind.QUICK_ADD, "$kcal kcal") { it.copy(kcal = kcal, slot = slot) })
            }
            Screen.WORKOUT -> WorkoutStartScreen(s, workoutRunning, onBack = { go(Screen.HOME) }) { dayId, name ->
                send(request(RequestKind.START_WORKOUT, name) { it.copy(programDayId = dayId) })
            }
        }
    }
}

private fun foodRequest(food: WatchFood, slot: WatchSlot) = RemoteRequest(
    id = UUID.randomUUID().toString(),
    kind = RequestKind.LOG_FOOD,
    at = System.currentTimeMillis(),
    foodId = food.id,
    grams = food.grams,
    slot = slot,
    label = "Log ${food.name}",
)

// ---- pieces --------------------------------------------------------------------------------------------------------

@Composable
private fun Chip(label: String, secondary: String? = null, tonal: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = if (tonal) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
        label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = secondary?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
    )
}

@Composable
private fun Heading(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
}

@Composable
private fun Note(text: String, error: Boolean = false) {
    Box(
        Modifier.fillMaxWidth()
            .background(if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
            .padding(10.dp),
    ) {
        Text(
            text, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun Meter(label: String, value: String, fraction: Float?) {
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "$label $value" }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primaryContainer)) {
            if (fraction != null) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun ScreenList(content: androidx.wear.compose.foundation.lazy.ScalingLazyListScope.() -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

private fun androidx.wear.compose.foundation.lazy.ScalingLazyListScope.backChip(onBack: () -> Unit) {
    item { Chip("Back", onClick = onBack) }
}

private fun number(n: Int) = "%,d".format(n)

// ---- screens -------------------------------------------------------------------------------------------------------

@Composable
private fun HomeScreen(
    state: RemoteState?,
    connected: Boolean,
    workoutRunning: Boolean,
    pendingLines: List<String>,
    notice: RemoteResponse?,
    onDismissNotice: () -> Unit,
    onGo: (Screen) -> Unit,
) {
    val fresh = state?.takeIf { it.isFor(LocalDate.now().toString()) }
    ScreenList {
        item { Heading("INTAKE") }
        notice?.let { n ->
            item { Chip(n.message, "Tap to dismiss", tonal = n.status != ResponseStatus.DONE && n.status != ResponseStatus.EXPIRED, onClick = onDismissNotice) }
        }
        pendingLines.forEach { line -> item { Note(line) } }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (fresh == null) {
                    Text(
                        if (connected) "Open Intake on your phone once to load today." else "Phone not connected. Numbers show when it is.",
                        fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Meter("Eaten", number(fresh.kcal) + (fresh.kcalTarget?.let { " / ${number(it)}" } ?: "") + " kcal", fresh.kcalTarget?.let { fresh.kcal / it.toFloat() })
                    Meter("Protein", "${fresh.proteinG}" + (fresh.proteinTargetG?.let { " / $it" } ?: "") + " g", fresh.proteinTargetG?.let { fresh.proteinG / it.toFloat() })
                    Meter(
                        "Water",
                        RemoteProtocol.formatVolume(fresh.waterMl.toDouble(), fresh.volumeUnit) +
                            (fresh.waterTargetMl?.let { " / ${RemoteProtocol.formatVolume(it.toDouble(), fresh.volumeUnit)}" } ?: ""),
                        fresh.waterTargetMl?.let { fresh.waterMl / it.toFloat() },
                    )
                }
            }
        }
        if (!connected) item { Note("Phone not connected. What you add is saved here and sent when it reconnects.", error = true) }
        item { Chip("Add water", tonal = false, onClick = { onGo(Screen.WATER) }) }
        item { Chip("Add food", onClick = { onGo(Screen.FOOD) }) }
        item {
            Chip(
                if (workoutRunning) "Workout running" else "Start workout",
                secondary = if (workoutRunning) "Add sets on the phone" else fresh?.todayWorkout?.name,
                enabled = !workoutRunning,
                onClick = { onGo(Screen.WORKOUT) },
            )
        }
    }
}

@Composable
private fun WaterScreen(state: RemoteState?, onBack: () -> Unit, onAdd: (ml: Double, presetId: String?, label: String) -> Unit) {
    val unit = state?.volumeUnit ?: RemoteProtocol.UNIT_ML
    val step = RemoteProtocol.stepMl(unit)
    val sizes = state?.waterSizes.orEmpty()
    var custom by remember(unit) { mutableDoubleStateOf(sizes.firstOrNull()?.ml?.toDouble() ?: (if (unit == RemoteProtocol.UNIT_OZ) step * 8 else 250.0)) }
    ScreenList {
        item { Heading("ADD WATER") }
        sizes.forEach { size ->
            item {
                Chip(size.label, RemoteProtocol.formatVolume(size.ml.toDouble(), unit)) {
                    onAdd(size.ml.toDouble(), size.presetId, "+" + RemoteProtocol.formatVolume(size.ml.toDouble(), unit))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { custom = (custom - step).coerceAtLeast(step) }, modifier = Modifier.size(44.dp).semantics { contentDescription = "Less water" }) { Text("−", fontSize = 18.sp) }
                Text(RemoteProtocol.formatVolume(custom, unit), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.semantics { contentDescription = "Custom amount ${RemoteProtocol.formatVolume(custom, unit)}" })
                FilledTonalButton(onClick = { custom = (custom + step).coerceAtMost(RemoteProtocol.MAX_WATER_ML) }, modifier = Modifier.size(44.dp).semantics { contentDescription = "More water" }) { Text("＋", fontSize = 18.sp) }
            }
        }
        item {
            Chip("Add this amount", tonal = false) {
                val ml = RemoteProtocol.wholeMl(custom)
                onAdd(ml, null, "+" + RemoteProtocol.formatVolume(ml, unit))
            }
        }
        backChip(onBack)
    }
}

@Composable
private fun FoodScreen(
    state: RemoteState?,
    slot: WatchSlot,
    onSlot: () -> Unit,
    onBack: () -> Unit,
    onGo: (Screen) -> Unit,
    onPlanned: (PlannedMeal) -> Unit,
) {
    ScreenList {
        item { Heading("ADD FOOD") }
        item { Chip("Meal: ${slot.label}", "Tap to change", onClick = onSlot) }
        state?.planned?.let { p -> item { PlannedChip(p, slot, onPlanned) } }
        item { Chip("Recent foods", onClick = { onGo(Screen.RECENTS) }) }
        item { Chip("Favourites", onClick = { onGo(Screen.FAVOURITES) }) }
        item { Chip("Saved meals", onClick = { onGo(Screen.MEALS) }) }
        item { Chip("Quick add calories", onClick = { onGo(Screen.QUICK_ADD) }) }
        backChip(onBack)
    }
}

/** The planned meal. The phone's allergen verdict is shown as it sent it; a meal it blocks is not offered. */
@Composable
private fun PlannedChip(p: PlannedMeal, slot: WatchSlot, onPlanned: (PlannedMeal) -> Unit) {
    val numbers = listOfNotNull(p.kcal?.let { "$it kcal" }, p.proteinG?.let { "$it g protein" }).joinToString(" · ")
    when (p.allergen) {
        "CONTAINS" -> Note("${p.name}: matches your allergen profile. Not offered here.", error = true)
        else -> {
            Chip(
                "Log it: ${p.name}",
                (numbers.ifBlank { slot.label }) + if (p.allergen == "MAY_CONTAIN") " · May contain an allergen you avoid, check labels" else " · ${slot.label}",
                tonal = false,
            ) { onPlanned(p) }
        }
    }
}

@Composable
private fun FoodList(title: String, foods: List<WatchFood>, empty: String, slot: WatchSlot, onBack: () -> Unit, onPick: (WatchFood) -> Unit) {
    ScreenList {
        item { Heading(title.uppercase()) }
        item { Text("To ${slot.label.lowercase()}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (foods.isEmpty()) item { Text(empty, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp)) }
        foods.forEach { f -> item { Chip(f.name, "${f.portionLabel} · ${f.kcal} kcal") { onPick(f) } } }
        backChip(onBack)
    }
}

@Composable
private fun MealsScreen(state: RemoteState?, slot: WatchSlot, onBack: () -> Unit, onPick: (com.zeroindex.intake.wearprotocol.WatchTemplate) -> Unit) {
    val templates = state?.templates.orEmpty()
    ScreenList {
        item { Heading("SAVED MEALS") }
        item { Text("To ${slot.label.lowercase()}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (templates.isEmpty()) item { Text("Save a meal on the phone to find it here.", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp)) }
        templates.forEach { t -> item { Chip(t.name, "${t.kcal} kcal") { onPick(t) } } }
        backChip(onBack)
    }
}

@Composable
private fun QuickAddScreen(slot: WatchSlot, onSlot: () -> Unit, onBack: () -> Unit, onAdd: (Int) -> Unit) {
    var kcal by remember { mutableIntStateOf(200) }
    fun bump(by: Int) { kcal = (kcal + by).coerceIn(10, RemoteProtocol.MAX_QUICK_KCAL) }
    ScreenList {
        item { Heading("QUICK ADD") }
        item { Text("$kcal kcal", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { contentDescription = "$kcal calories" }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = { bump(-100) }, modifier = Modifier.size(width = 56.dp, height = 40.dp).semantics { contentDescription = "100 fewer" }) { Text("−100", fontSize = 11.sp) }
                FilledTonalButton(onClick = { bump(100) }, modifier = Modifier.size(width = 56.dp, height = 40.dp).semantics { contentDescription = "100 more" }) { Text("+100", fontSize = 11.sp) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = { bump(-10) }, modifier = Modifier.size(width = 56.dp, height = 40.dp).semantics { contentDescription = "10 fewer" }) { Text("−10", fontSize = 11.sp) }
                FilledTonalButton(onClick = { bump(10) }, modifier = Modifier.size(width = 56.dp, height = 40.dp).semantics { contentDescription = "10 more" }) { Text("+10", fontSize = 11.sp) }
            }
        }
        item { Chip("Meal: ${slot.label}", "Tap to change", onClick = onSlot) }
        item { Chip("Add $kcal kcal", tonal = false) { onAdd(kcal) } }
        backChip(onBack)
    }
}

@Composable
private fun WorkoutStartScreen(state: RemoteState?, running: Boolean, onBack: () -> Unit, onStart: (dayId: String?, name: String) -> Unit) {
    ScreenList {
        item { Heading("START WORKOUT") }
        if (running) {
            item { Note("A workout is already running.") }
        } else {
            state?.todayWorkout?.let { day -> item { Chip(day.name, "Today's program day", tonal = false) { onStart(day.dayId, "Start ${day.name}") } } }
            item { Chip("Empty workout", "Add exercises on the phone") { onStart(null, "Start empty workout") } }
            item { Text("The phone starts it and the watch shows it.", fontSize = 10.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        backChip(onBack)
    }
}
