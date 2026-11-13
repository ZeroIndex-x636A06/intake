package com.zeroindex.intake.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.goals.DayKind
import com.zeroindex.intake.domain.goals.GoalPlan
import com.zeroindex.intake.domain.goals.PlanCard
import com.zeroindex.intake.domain.goals.TdeeSource
import com.zeroindex.intake.domain.goals.WarningLevel
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.InfoTip
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.LoadingState
import com.zeroindex.intake.ui.components.MacroTiles
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.SubCopy
import com.zeroindex.intake.ui.components.SwitchRow
import java.util.Locale

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * "Your plan": calories, macros, water and training from the profile. Each card is the number, a
 * one-line why, an (i) with the long version, and an Edit. Nothing is saved until Apply.
 */
@Composable
fun YourPlanScreen(
    contentPadding: PaddingValues,
    onOpenProfile: () -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenProgram: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YourPlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "head") { SubCopy("Every number says why. Change any of them before you apply.") }

        val plan = state.plan
        when {
            state.loading -> item(key = "loading") {
                LoadingState(listOf("Reading your profile", "Working out your daily burn", "Building your numbers"))
            }
            plan == null -> item(key = "cannot") {
                Column {
                    AccentNote(
                        lead = "Not quite enough to build a plan",
                        body = state.cannotBuildBecause ?: "Fill in Profile & goals first.",
                    )
                    Button(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp)) {
                        Text("Open Profile & goals")
                    }
                }
            }
            else -> {
                item(key = "source") { SourceRow(plan) }
                plan.warnings.forEachIndexed { i, w ->
                    item(key = "warn$i") {
                        if (w.level == WarningLevel.WARN) {
                            AccentNote(
                                lead = "Heads up",
                                body = w.text,
                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        } else {
                            SandNote(w.text)
                        }
                    }
                }

                item(key = "calories") {
                    PlanCardView(plan.calorieCard) {
                        plan.calories.reverseSchedule.takeIf { it.size > 1 }?.let { weeks ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Reverse plan, kcal by week: " + weeks.take(12).joinToString(" → ") { it.toString() } +
                                    if (weeks.size > 12) " → ..." else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        plan.dietBreak?.let { b ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Diet break: after every ${b.everyWeeks} weeks, spend a week at ${"%,d".format(Locale.US, b.maintenanceKcal)} kcal " +
                                    "(weeks ${b.nextBreakWeek(1)}, ${b.nextBreakWeek(b.nextBreakWeek(1))} ...). " +
                                    "Intake will not change your target by itself, so switch it when the week comes.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item(key = "macros") {
                    PlanCardView(
                        card = plan.macroCard,
                        headline = {
                            MacroTiles(
                                listOf(
                                    Triple("${plan.macros.proteinG} g", "Protein", MaterialTheme.colorScheme.primary),
                                    Triple("${plan.macros.carbsG} g", "Carbs", MaterialTheme.colorScheme.secondary),
                                    Triple("${plan.macros.fatG} g", "Fat", MaterialTheme.colorScheme.tertiary),
                                ),
                            )
                        },
                    ) {
                        plan.week?.let { week ->
                            Spacer(Modifier.height(8.dp))
                            Text("Your week", style = MaterialTheme.typography.labelMedium)
                            week.pattern.days.forEach { d ->
                                Text(
                                    "${WEEKDAYS[d.weekday - 1]}  ${d.kind.label.padEnd(12)}  ${d.kcal} kcal · C ${d.carbsG} g",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (d.kind == DayKind.REFEED) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item(key = "water") {
                    PlanCardView(plan.waterCard)
                }
                state.howBuilt?.let { hb ->
                    item(key = "how-built") { com.zeroindex.intake.ui.screens.train.HowBuiltCard(hb) }
                }
                item(key = "training") {
                    val training = state.training
                    IntakeCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Training", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = onOpenProfile) { Text("Edit") }
                        }
                        if (training != null) {
                            Text(training.headline, style = MaterialTheme.typography.titleLarge)
                            Text(training.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            training.lines.take(6).forEach {
                                Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 3.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onOpenGenerator, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                Text("See the full program")
                            }
                            SwitchRow(
                                title = "Create this program when I apply",
                                subtitle = if (state.hasActiveProgram) "It will become your active program. Your current one is kept." else null,
                                on = state.includeProgram,
                                onToggle = { viewModel.setIncludeProgram(!state.includeProgram) },
                                onLabel = "Yes",
                                offLabel = "No",
                            )
                        }
                    }
                }

                item(key = "apply") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.willPauseAutoTargets) {
                            SandNote(
                                "Applying this plan turns off automatic target updates, because carb cycling, refeeds, " +
                                    "contest prep and reverse diets are not a single steady rate. You can turn them back " +
                                    "on in Settings.",
                            )
                        }
                        if (state.applied) {
                            AccentNote(
                                lead = "Plan applied",
                                body = "Your targets and water goal are set" +
                                    (if (state.appliedProgramId != null) " and your program is ready." else ".") +
                                    " Past days keep the targets they had.",
                            )
                            state.appliedProgramId?.let {
                                OutlinedButton(onClick = { onOpenProgram(it) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                    Text("Open my program")
                                }
                            }
                        } else {
                            Button(
                                onClick = viewModel::apply,
                                enabled = !state.applying,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) { Text(if (state.applying) "Applying..." else "Apply this plan") }
                            Text(
                                "Nothing is saved until you apply. The calculation uses only the numbers on this phone, with no AI.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                                        }
                    }
                }
            }
        }
    }

}


@Composable
private fun SourceRow(plan: GoalPlan) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusPill(
            text = when (plan.tdeeSource) {
                TdeeSource.MEASURED -> "Built from your measured burn"
                TdeeSource.BLENDED -> "Built from your baseline"
                TdeeSource.ESTIMATE -> "Built from a formula"
            },
            tone = if (plan.tdeeSource == TdeeSource.ESTIMATE) PillTone.Wait else PillTone.Ok,
        )
        Text(plan.phase.label, style = MaterialTheme.typography.labelMedium)
        InfoTip(
            text = "Daily calorie burn is about ${"%,d".format(Locale.US, Math.round(plan.tdee))} kcal. " +
                "${plan.tdeeWhy} Calories at rest are ${"%,d".format(Locale.US, Math.round(plan.bmr.kcal))} kcal.",
            label = "Daily calorie burn",
        )
    }
}

@Composable
private fun PlanCardView(
    card: PlanCard,
    headline: (@Composable () -> Unit)? = null,
    extra: @Composable () -> Unit = {},
) {
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
            InfoTip(text = card.detail, label = card.title)
            Spacer(Modifier.weight(1f))
        }
        if (headline != null) headline() else Text(card.headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(card.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        extra()
    }
}
