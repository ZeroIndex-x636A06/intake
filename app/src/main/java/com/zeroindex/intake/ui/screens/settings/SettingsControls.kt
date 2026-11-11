@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zeroindex.intake.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.BuildConfig
import com.zeroindex.intake.data.foodref.FoodRefInfo
import com.zeroindex.intake.data.settings.ThemeMode
import com.zeroindex.intake.domain.goals.Phase
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.settings.MuscleRegion
import com.zeroindex.intake.domain.settings.SodiumDisplay
import com.zeroindex.intake.domain.settings.TodayCard
import com.zeroindex.intake.domain.settings.WeeklySets
import com.zeroindex.intake.domain.settings.ControlKind
import com.zeroindex.intake.domain.food.OffLanguage
import com.zeroindex.intake.domain.settings.SettingSpec
import com.zeroindex.intake.domain.training.generator.Equipment
import com.zeroindex.intake.domain.training.generator.EquipmentPreset
import com.zeroindex.intake.domain.units.LengthUnit
import com.zeroindex.intake.domain.units.PortionUnit
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.Segmented
import com.zeroindex.intake.ui.components.SettingRow
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.onboarding.HelpFlowViewModel
import com.zeroindex.intake.ui.screens.ai.AiSettingControl
import com.zeroindex.intake.ui.screens.ai.OpenRouterKeyControl
import com.zeroindex.intake.ui.screens.backup.ExportSection
import com.zeroindex.intake.ui.screens.backup.SyncSection
import com.zeroindex.intake.ui.screens.health.HealthControl
import com.zeroindex.intake.ui.screens.today.AdvancedEdits
import com.zeroindex.intake.ui.screens.today.HydrationAdvanced
import com.zeroindex.intake.ui.screens.today.HydrationExtrasViewModel
import com.zeroindex.intake.ui.theme.IntakeShape

/** What the About group's buttons need from navigation. */
class SettingsNav(
    val openScriptReference: (page: String, anchor: String?) -> Unit,
    val openPrivacy: () -> Unit,
    val openWelcome: () -> Unit,
    val goToToday: () -> Unit,
)

private val AI_FAMILIES = listOf("aiModel.", "aiUses.")
private val HEALTH_FAMILIES = listOf("hc.read.", "hc.write.")

/**
 * Draws one control of one Settings page, by inventory id (docs/SETTINGS-INVENTORY.md). Every id of the registry has a
 * branch here (`SettingsRegistryTest` checks it against the file). Controls that are one widget in the inventory's
 * eyes but several rows in its table (the weather block, the sync block, the export block, the hydration advanced block,
 * the Open Food Facts login) are drawn once by their first id; the others return nothing.
 */
@Composable
fun SettingControl(
    spec: SettingSpec,
    nav: SettingsNav,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val s = state.settings
    val id = spec.id
    Column(modifier.fillMaxWidth()) {
        when {
            AI_FAMILIES.any { id.startsWith(it) } -> AiSettingControl(id)
            HEALTH_FAMILIES.any { id.startsWith(it) } -> HealthControl(id)
            else -> when (id) {
                // ---- Appearance and units ----
                "weightUnit" -> SettingRow("Weight", "Display unit. Stored in kg whatever you pick.") {
                    Segmented(WeightUnit.entries.map { it.label }, WeightUnit.entries.indexOf(s.weightUnit), { viewModel.setWeightUnit(WeightUnit.entries[it]) })
                }
                "lengthUnit" -> SettingRow("Length", "Display unit. Stored in cm whatever you pick.") {
                    Segmented(LengthUnit.entries.map { it.label }, LengthUnit.entries.indexOf(s.lengthUnit), { viewModel.setLengthUnit(LengthUnit.entries[it]) })
                }
                "volumeUnit" -> SettingRow("Drinks and water", "Display unit for drinks, water and quick sizes.") {
                    Segmented(VolumeUnit.entries.map { it.label }, VolumeUnit.entries.indexOf(s.volumeUnit), { viewModel.setVolumeUnit(VolumeUnit.entries[it]) })
                }
                "portionUnit" -> SettingRow("Food portions", "Used in the photo estimate and food detail.") {
                    Segmented(PortionUnit.entries.map { it.label }, PortionUnit.entries.indexOf(s.portionUnit), { viewModel.setPortionUnit(PortionUnit.entries[it]) })
                }
                "themeMode" -> SettingRow("Theme", "System follows your phone.") {
                    Segmented(
                        ThemeMode.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                        ThemeMode.entries.indexOf(s.themeMode),
                        { viewModel.setThemeMode(ThemeMode.entries[it]) },
                    )
                }
                "dynamicColor" -> SwitchSetting("Use wallpaper colours", "Overrides the designed palette.", s.dynamicColor, viewModel::setDynamicColor)
                "reduceMotion" -> SwitchSetting(
                    "Reduce motion", "Turns off sweeps, count-ups and bursts. Also on when your phone has animations off.",
                    s.reduceMotion, viewModel::setReduceMotion,
                )
                "todayCard.next" -> SwitchSetting("Today card: Next up", "The planned meal, today's workout and the one thing to do.", s.todayCardNext) { viewModel.setTodayCard(TodayCard.NEXT, it) }
                "todayCard.water" -> SwitchSetting("Today card: Water", "Today's drinks with a minus and a plus.", s.todayCardWater) { viewModel.setTodayCard(TodayCard.WATER, it) }
                "todayCard.weight" -> SwitchSetting("Today card: Weight", "Your last weigh-in and a Weigh in button.", s.todayCardWeight) { viewModel.setTodayCard(TodayCard.WEIGHT, it) }
                "todayCard.meals" -> SwitchSetting("Today card: Meals", "Four compact rows with what you ate. Calories is always shown.", s.todayCardMeals) { viewModel.setTodayCard(TodayCard.MEALS, it) }

                // ---- Nutrition ----
                "autoTargets" -> SwitchSetting(
                    "Work my targets out from my data",
                    "Uses your profile, weight trend and goal. Off lets you set calories by hand.",
                    s.autoCalculateTargets, viewModel::setAutoTargets,
                )
                "manualCalories" -> ManualCaloriesRow(state.currentKcal, viewModel::setManualCalories)
                "proteinPerKg" -> NumberSetting("Protein", "Per kg of goal weight. 1.6 to 2.2 is the usual range.", s.proteinGramsPerKg, "g per kg", 0.8..3.5) { viewModel.setProteinPerKg(it) }
                "fatPerKg" -> NumberSetting("Fat", "Per kg of weight. 0.6 is the minimum.", s.fatGramsPerKg, "g per kg", 0.3..2.0) { viewModel.setFatPerKg(it) }
                "addActiveKcal" -> SwitchSetting(
                    "Add active calories to my target",
                    "Off by default. Wearable burn estimates are often high, and counting them twice stalls a cut.",
                    s.addActiveCaloriesToTarget, viewModel::setAddActiveCalories,
                )
                "showSodium" -> SettingRow("Show salt or sodium on the dashboard", "Adds one nutrient tile in Nutrition.") {
                    Segmented(SodiumDisplay.entries.map { it.label }, SodiumDisplay.entries.indexOf(s.showSodium), { viewModel.setShowSodium(SodiumDisplay.entries[it]) })
                }
                "eatOutRadius" -> {
                    val miles = listOf(0.5, 1.0, 2.0, 5.0)
                    SettingRow("Eat out nearby: search radius", "Used by Swap > Eat out nearby. Uses the weather location, never a live track.") {
                        Segmented(miles.map { "${if (it % 1.0 == 0.0) it.toInt() else it} mi" }, miles.indexOf(s.eatOutRadiusMi).coerceAtLeast(1), { viewModel.setEatOutRadius(miles[it]) })
                    }
                }
                "recalc" -> SettingRow("Recalculate from my data", "Runs the target maths again with today's weight trend.") {
                    OutlinedButton(onClick = viewModel::recalculateTargetFromTdee, shape = IntakeShape.Chip) { Text("Recalculate now") }
                }
                "allowBelowFloor" -> SwitchSetting("Allow calories below the floor", "Normally never below 1.1 x calories at rest.", s.profile.allowBelowFloor) { v -> viewModel.updateProfile { it.copy(allowBelowFloor = v) } }
                "carbCycling" -> SwitchSetting("Carb cycling", "More carbs on training days. The weekly total is unchanged.", s.profile.carbCycling) { v -> viewModel.updateProfile { it.copy(carbCycling = v) } }
                "carbCycleShiftPct" -> ChipSetting("Carb cycle shift", "Extra carbs on a training day.", listOf(15, 25, 35), s.profile.carbCycleShiftPct, { "+$it%" }) { v -> viewModel.updateProfile { it.copy(carbCycleShiftPct = v) } }
                "refeedDaysPerWeek" -> ChipSetting("Refeed days per week", "A higher-carb day while cutting.", listOf(0, 1, 2), s.profile.refeedDaysPerWeek, { if (it == 0) "None" else "$it" }) { v -> viewModel.updateProfile { it.copy(refeedDaysPerWeek = v) } }
                "dietBreakEveryWeeks" -> ChipSetting("Diet break every", "A maintenance week after this many weeks of dieting.", listOf<Int?>(null, 8, 10, 12), s.profile.dietBreakEveryWeeks, { if (it == null) "Off" else "$it weeks" }) { v -> viewModel.updateProfile { it.copy(dietBreakEveryWeeks = v) } }
                "contestWeeklyLossPct" -> if (s.profile.phase == Phase.CONTEST_PREP) {
                    ChipSetting("Contest prep: weekly loss", "Percent of body weight a week. Shown only in that phase.", listOf(0.5, 0.75, 1.0), s.profile.contestWeeklyLossPct, { "$it%" }) { v -> viewModel.updateProfile { it.copy(contestWeeklyLossPct = v) } }
                }
                "reverseStepKcal" -> if (s.profile.phase == Phase.REVERSE_DIET) {
                    ChipSetting("Reverse diet: calories added per week", "Shown only in that phase.", listOf(50, 75, 100), s.profile.reverseStepKcal, { "+$it kcal" }) { v -> viewModel.updateProfile { it.copy(reverseStepKcal = v) } }
                }

                // ---- Water and drinks ----
                "hydrationBase" -> HydrationBaseRow(s.hydrationBaseMl, s.volumeUnit, viewModel::setHydrationBaseMl)
                "hydrationDynamic" -> SwitchSetting("Adjust the target for the day", "Raises the target on hot days and after workouts.", s.hydrationDynamic, viewModel::setHydrationDynamic)
                "hydrationTrainingBonus" -> NumberSetting(
                    "Training-day increase", "Water added on a day you train (${s.volumeUnit.label}).",
                    UnitConverter.round(s.volumeUnit.fromMl(s.hydrationTrainingBonusMl), 1), s.volumeUnit.label, 0.0..5000.0,
                ) { viewModel.setHydrationTrainingBonusMl(s.volumeUnit.toMl(it)) }
                "weatherEnabled" -> WeatherSettingsSection()
                "locationMode", "place", "forgetLocation" -> Unit // part of the weather block above
                "quickSizes" -> QuickSizes(state.presets, s.volumeUnit, viewModel)
                "drinkOverrides" -> HydrationAdvancedBlock(s.weightUnit, s.volumeUnit)
                "sweatMeasure", "sweatUse" -> Unit // part of the drink values and sweat block

                // ---- Training ----
                "defaultRestSec" -> NumberSetting("Default rest", "Seconds between sets.", s.defaultRestSec.toDouble(), "sec", 10.0..900.0) { viewModel.setRestSeconds(it.toInt()) }
                "restVibrate" -> SwitchSetting("Vibrate when rest ends", null, s.restTimerVibrate, viewModel::setRestVibrate)
                "barWeight" -> NumberSetting("Bar weight", "Used for the plate maths.", UnitConverter.round(s.weightUnit.fromKg(s.barWeightKg), 1), s.weightUnit.label, 0.0..60.0) { viewModel.setBarWeightKg(s.weightUnit.toKg(it)) }
                "plates" -> PlatesRow(s.availablePlatesKg, s.weightUnit, viewModel::setPlates)
                "equipmentPreset" -> Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text("My equipment", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "One list for the generator, swaps and the exercise library. Pick a preset, then adjust the items.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    EquipmentPresetRow(s.profile.equipmentPreset) { p -> viewModel.updateProfile { it.copy(equipmentPreset = p, equipment = if (p == EquipmentPreset.CUSTOM) it.equipment else p.kinds) } }
                }
                "equipment" -> EquipmentItemsRow(s.profile.equipment) { kinds -> viewModel.updateProfile { it.copy(equipment = kinds, equipmentPreset = EquipmentPreset.CUSTOM) } }
                "jumpTests" -> SwitchSetting("Show jump tests in Progress", "Hidden until you switch it on.", s.jumpTests, viewModel::setJumpTests)
                "weeklySets" -> WeeklySetsRow(s.weeklySetsCsv, viewModel::setWeeklySets)

                // ---- Health Connect is drawn by HealthControl above (hc.*, hcOpen) ----
                "hcOpen" -> HealthControl(id)

                // ---- Food database ----
                "offSearch" -> SwitchSetting("Search Open Food Facts while typing", "Sends only the words you typed, and only when you are online.", state.foodData.offSearch, viewModel::setOffSearch)
                "offLanguage" -> ChipSetting(
                    "Food search language",
                    "Packaged foods are listed when they have a name in this language or in English.",
                    listOf("") + OffLanguage.CHOICES.map { it.first },
                    state.foodData.offLanguage,
                    { code -> OffLanguage.choiceLabel(code, java.util.Locale.getDefault()) },
                    viewModel::setOffLanguage,
                )
                "offOtherLanguages" -> SwitchSetting("Show products in other languages", "Off leaves out products with no name in your food search language or English.", state.foodData.offOtherLanguages, viewModel::setOffOtherLanguages)
                "offContribute" -> SwitchSetting("Allow contributing to Open Food Facts", "Adds a Contribute button to foods you made that have a barcode. You confirm each time.", state.foodData.offContribute, viewModel::setOffContribute)
                "offUsername" -> OffLogin(state.keys.offUsername, state.keys.hasOffCredentials, viewModel::saveOffCredentials)
                "offPassword" -> Unit // drawn with the username above

                // ---- Videos ----
                "onlyWorkouts" -> SwitchSetting("Show only workouts by default", "Hides results that do not look like a workout. A one-tap override shows everything for one search.", s.onlyWorkouts, viewModel::setOnlyWorkouts)
                "personalise" -> SwitchSetting("Personalise Recommended from this phone's data", "Uses your weekly muscle volume, equipment and saved videos. Strictly local. Videos use about 100 searches a day on the free quota.", s.personaliseVideos, viewModel::setPersonalise)

                // ---- Reminders ----
                "coachNotify" -> SwitchSetting("Weekly check-in notification", "\"Your weekly check-in is ready\".", s.coachNotify, viewModel::setCoachNotify)
                "coachWeekday" -> WeekdayRow(s.coachWeekday, viewModel::setCoachWeekday)
                "photoReminder" -> {
                    val options = listOf(0, 2, 3, 4)
                    SettingRow("Progress photo reminder", "A line on You when a photo is due. Off, or every 2, 3 or 4 weeks.") {
                        Segmented(options.map { if (it == 0) "Off" else "$it wk" }, options.indexOf(s.photoReminderWeeks).coerceAtLeast(0), { viewModel.setPhotoReminderWeeks(options[it]) })
                    }
                }
                "exportReminder" -> SettingRow(
                    "Export reminder",
                    if (s.sync.paired) "Off while sync is on: the server keeps a copy." else "Monthly. Without sync this phone holds the only copy.",
                ) { Switch(checked = s.exportReminder && !s.sync.paired, onCheckedChange = viewModel::setExportReminder, enabled = !s.sync.paired) }

                // ---- Sync and export ----
                "syncServer" -> SyncSection()
                "pairCode", "deviceName", "devices", "unpair" -> Unit // part of the sync block above
                "exportPass" -> ExportSection()
                "exportPhotos", "exportJson", "exportCsv", "import" -> Unit // part of the export block above

                // ---- API keys ----
                "key.openrouter" -> OpenRouterKeyControl()
                "key.usda" -> KeyField(
                    title = "USDA FoodData Central key", has = state.keys.hasUsdaKey,
                    hint = "Optional. Lets search reach foods beyond the offline database. Free from fdc.nal.usda.gov.",
                    onSave = viewModel::setUsdaKey, onTest = viewModel::testUsdaKey,
                    result = when (val t = state.usdaTest) {
                        is KeyTestResult.Ok -> t.detail
                        is KeyTestResult.Failed -> t.message
                        KeyTestResult.Testing -> "Testing…"
                        KeyTestResult.Untested -> null
                    },
                )
                "key.youtube" -> KeyField(
                    title = "YouTube Data API key", has = state.keys.hasYoutubeKey,
                    hint = "Videos need internet and your own free key. It is sent only to YouTube. The free quota allows about 100 searches a day; results are kept on this phone to save it.",
                    onSave = viewModel::setYoutubeKey, onTest = viewModel::testYoutubeKey,
                    result = when (val t = state.youtubeTest) {
                        is KeyTestResult.Ok -> t.detail
                        is KeyTestResult.Failed -> t.message
                        KeyTestResult.Testing -> "Testing…"
                        KeyTestResult.Untested -> null
                    },
                )

                // ---- About and help ----
                "tour" -> AboutAction("Replay the tour", "A quick look around Today, one step at a time. Nothing is changed or deleted.", "Replay") { flow -> flow.replayTour(then = nav.goToToday) }
                "welcome" -> AboutAction("Show the welcome again", "Run the setup questions again. Your answers are filled in and nothing is deleted.", "Show") { flow -> flow.showWelcomeAgain(then = nav.openWelcome) }
                "tips" -> AboutAction("Show screen tips again", "Bring back the short tip at the top of Nutrition, Train and Progress.", "Show") { flow -> flow.showTipsAgain(then = {}) }
                "scripts" -> SettingRow("Script language help", "Meal plan and program scripts, with the full reference. Works offline.") {
                    OutlinedButton(onClick = { nav.openScriptReference("README", null) }, shape = IntakeShape.Chip) { Text("Open") }
                }
                "privacy" -> SettingRow("What leaves this phone", "Exactly what is sent, to whom, and when.") {
                    OutlinedButton(onClick = nav.openPrivacy, shape = IntakeShape.Chip) { Text("Read") }
                }
                "credits" -> CreditsRow()
                "version" -> SettingRow("Version", null) {
                    Text("Intake ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> if (spec.kind == ControlKind.READ_ONLY) Text(spec.label, style = MaterialTheme.typography.bodySmall)
            }
        }
        state.message?.let { m ->
            if (id == "key.usda" || id == "key.youtube" || id == "offUsername") {
                SandNote(m)
                TextButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun SwitchSetting(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle) { Switch(checked = checked, onCheckedChange = onChange) }
}

/** A numeric field that saves as typed, but only a value inside [range]; a half-typed number never overwrites. */
@Composable
private fun NumberSetting(
    title: String,
    subtitle: String?,
    value: Double,
    suffix: String,
    range: ClosedFloatingPointRange<Double>,
    onCommit: (Double) -> Unit,
) {
    val saved = fmt(value)
    var text by remember { mutableStateOf(saved) }
    LaunchedEffect(saved) { if (text.toDoubleOrNull() != saved.toDoubleOrNull()) text = saved }
    SettingRow(title, subtitle) {
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                text = t.filter { it.isDigit() || it == '.' }.take(7)
                text.toDoubleOrNull()?.takeIf { it in range }?.let(onCommit)
            },
            singleLine = true,
            suffix = { Text(suffix, style = MaterialTheme.typography.labelSmall) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = IntakeShape.Field,
            modifier = Modifier.width(150.dp),
        )
    }
}

private fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')

@Composable
private fun <T> ChipSetting(title: String, subtitle: String?, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            options.forEach { o -> ChoiceChip(label(o), o == selected, { onSelect(o) }) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ManualCaloriesRow(currentKcal: Int?, onSet: (Int) -> Unit) {
    var text by remember(currentKcal) { mutableStateOf(currentKcal?.toString().orEmpty()) }
    val kcal = text.toIntOrNull()?.takeIf { it in 800..8000 }
    SettingRow("Calories by hand", "Used when the switch above is off, or as a fine-tune. Saving also turns the switch off.") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(4) },
                singleLine = true,
                suffix = { Text("kcal", style = MaterialTheme.typography.labelSmall) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = IntakeShape.Field,
                modifier = Modifier.width(130.dp),
            )
            Button(onClick = { kcal?.let(onSet) }, enabled = kcal != null && kcal != currentKcal, shape = IntakeShape.Chip) { Text("Save") }
        }
    }
}

@Composable
private fun HydrationBaseRow(baseMl: Double?, unit: VolumeUnit, onSet: (Double?) -> Unit) {
    val saved = baseMl?.let { fmt(UnitConverter.round(unit.fromMl(it), 0)) }.orEmpty()
    var text by remember(saved) { mutableStateOf(saved) }
    SettingRow("Daily water target", "Blank works it out from your weight. In ${unit.label}.") {
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                text = t.filter { it.isDigit() || it == '.' }.take(6)
                if (text.isBlank()) onSet(null) else text.toDoubleOrNull()?.takeIf { it > 0 }?.let { onSet(unit.toMl(it)) }
            },
            singleLine = true,
            placeholder = { Text("Auto") },
            suffix = { Text(unit.label, style = MaterialTheme.typography.labelSmall) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = IntakeShape.Field,
            modifier = Modifier.width(150.dp),
        )
    }
}

@Composable
private fun QuickSizes(
    presets: List<com.zeroindex.intake.data.db.entity.HydrationPresetEntity>,
    unit: VolumeUnit,
    viewModel: SettingsViewModel,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var drink by remember { mutableStateOf(DrinkType.WATER) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("Quick sizes", style = MaterialTheme.typography.titleSmall)
        Text(
            "The size buttons in Nutrition > Drinks. Remove one with the cross, or add your own.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            presets.forEach { p ->
                ChoiceChip("${p.name} ${unit.format(p.volumeMl)}  ✕", false, { viewModel.deletePreset(p) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            LabeledField("Name", name, { name = it.take(24) }, Modifier.weight(1f), placeholder = "Mug")
            LabeledField("Amount (${unit.label})", amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(DrinkType.WATER, DrinkType.TEA, DrinkType.COFFEE).forEach { d -> ChoiceChip(d.label, d == drink, { drink = d }) }
        }
        val ml = amount.toDoubleOrNull()?.takeIf { it > 0 }?.let(unit::toMl)
        Button(
            onClick = { ml?.let { viewModel.savePreset(name.trim(), it, drink); name = ""; amount = "" } },
            enabled = name.isNotBlank() && ml != null,
            shape = IntakeShape.Chip,
            modifier = Modifier.padding(top = 6.dp),
        ) { Text("Add custom size") }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun HydrationAdvancedBlock(weightUnit: WeightUnit, volumeUnit: VolumeUnit, extras: HydrationExtrasViewModel = hiltViewModel()) {
    val settings by extras.settings.collectAsStateWithLifecycle()
    val edits = remember(settings.drinkOverrides, settings.sweat, weightUnit, volumeUnit) { AdvancedEdits(settings, weightUnit, volumeUnit) }
    Column(Modifier.fillMaxWidth()) {
        HydrationAdvanced(edits, weightUnit, volumeUnit)
        Button(
            onClick = {
                DrinkType.entries.forEach { t -> extras.setOverride(t, edits.overrideFor(t).takeIf { !it.isEmpty }) }
                val m = edits.measurement(weightUnit, volumeUnit)
                extras.saveSweat(m, edits.useMeasured && m != null)
            },
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(48.dp),
        ) { Text("Save drink values and sweat rate") }
    }
}

@Composable
private fun PlatesRow(csvKg: String, unit: com.zeroindex.intake.domain.units.WeightUnit, onSet: (String) -> Unit) {
    val shown = com.zeroindex.intake.domain.training.PlateDefaults.toDisplayCsv(csvKg, unit)
    var text by remember(unit) { mutableStateOf(shown) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        LabeledField(
            "Plates available per side (${unit.label}, comma separated)", text,
            { t ->
                text = t.filter { it.isDigit() || it == '.' || it == ',' }
                onSet(com.zeroindex.intake.domain.training.PlateDefaults.fromDisplayCsv(text, unit))
            },
            supporting = "The plates on your rack. The plate maths and the smallest step up use them.",
        )
    }
}

@Composable
private fun EquipmentPresetRow(selected: EquipmentPreset, onSelect: (EquipmentPreset) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        EquipmentPreset.entries.forEach { p -> ChoiceChip(p.label, p == selected, { onSelect(p) }) }
    }
}

@Composable
private fun EquipmentItemsRow(selected: Set<String>, onChange: (Set<String>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        Equipment.ALL.forEach { kind ->
            ChoiceChip(Equipment.label(kind), kind in selected, { onChange(if (kind in selected) selected - kind else selected + kind) })
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun WeeklySetsRow(csv: String, onSet: (String) -> Unit) {
    val values = WeeklySets.parse(csv)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("Weekly sets target per muscle region", style = MaterialTheme.typography.titleSmall)
        Text(
            "Working sets a week for each region. Used by the Muscle volume view in Train.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MuscleRegion.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { region ->
                    var text by remember(csv) { mutableStateOf(values.getValue(region).toString()) }
                    LabeledField(
                        region.label, text,
                        { t ->
                            text = t.filter(Char::isDigit).take(2)
                            text.toIntOrNull()?.let { n -> onSet(WeeklySets.with(csv, region, n)) }
                        },
                        Modifier.weight(1f), keyboardType = KeyboardType.Number,
                    )
                }
            }
        }
        TextButton(onClick = { onSet("") }, enabled = !WeeklySets.isSuggested(csv)) { Text("Reset to suggested") }
    }
}

@Composable
private fun WeekdayRow(isoWeekday: Int?, onSet: (Int?) -> Unit) {
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("Check-in day", style = MaterialTheme.typography.titleSmall)
        Text(
            "The weekday the weekly check-in is made. None picks the day your baseline started, else Monday.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            names.forEachIndexed { i, n -> ChoiceChip(n, isoWeekday == i + 1, { onSet(if (isoWeekday == i + 1) null else i + 1) }) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun OffLogin(savedUser: String?, hasCredentials: Boolean, onSave: (String?, String?) -> Unit) {
    var username by remember(savedUser) { mutableStateOf(savedUser.orEmpty()) }
    var password by remember { mutableStateOf("") }
    val emailTyped = '@' in username
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("Open Food Facts account", style = MaterialTheme.typography.titleSmall)
        LabeledField(
            "Open Food Facts username", username, { username = it },
            placeholder = "Your username, not your email", isError = emailTyped,
            supporting = if (emailTyped) "Use your username. An email address will not work." else null,
        )
        LabeledField(
            "Password", password, { password = it },
            placeholder = if (hasCredentials) "Saved. Type to replace it" else "Your account password", secret = true,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onSave(username, password); password = "" },
                enabled = username.isNotBlank() && !emailTyped && password.isNotEmpty(),
                shape = IntakeShape.Chip,
            ) { Text("Save") }
            OutlinedButton(
                onClick = { onSave(null, null); username = ""; password = "" },
                enabled = hasCredentials, shape = IntakeShape.Chip,
            ) { Text("Remove") }
        }
        Text(
            "Stored encrypted on this phone. Sent only to openfoodfacts.org, in the request body over HTTPS, when you confirm a contribution.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

/** A key field with Save, Test (when there is a test) and Remove. The key is never shown back. */
@Composable
private fun KeyField(title: String, has: Boolean, hint: String, onSave: (String?) -> Unit, onTest: (() -> Unit)?, result: String?) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            StatusPill(if (has) "Key saved" else "No key", tone = if (has) PillTone.Ok else PillTone.Wait)
        }
        Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LabeledField(if (has) "Replace key" else "Key", text, { text = it.trim() }, secret = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            Button(onClick = { onSave(text); text = "" }, enabled = text.isNotBlank(), shape = IntakeShape.Chip) { Text("Save") }
            if (has && onTest != null) OutlinedButton(onClick = onTest, shape = IntakeShape.Chip) { Text("Test") }
            if (has) TextButton(onClick = { onSave(null) }) { Text("Remove") }
        }
        result?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp)) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun AboutAction(title: String, subtitle: String, button: String, flow: HelpFlowViewModel = hiltViewModel(), run: (HelpFlowViewModel) -> Unit) {
    SettingRow(title, subtitle) {
        OutlinedButton(onClick = { run(flow) }, shape = IntakeShape.Chip) { Text(button) }
    }
}

@Composable
private fun CreditsRow() {
    var open by remember { mutableStateOf(false) }
    SettingRow("Credits and licences", "Where the food, exercise and weather data come from.") {
        OutlinedButton(onClick = { open = true }, shape = IntakeShape.Chip) { Text("Read") }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Credits and licences") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Generic foods: ${FoodRefInfo.USDA_CREDIT}", style = MaterialTheme.typography.bodySmall)
                    Text(FoodRefInfo.OFF_CREDIT, style = MaterialTheme.typography.bodySmall)
                    Text(com.zeroindex.intake.domain.training.ExerciseGuideIndex.CREDIT, style = MaterialTheme.typography.bodySmall)
                    Text("Weather for the water target: data by Open-Meteo.com, licensed CC BY 4.0, only if you turn it on.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}
