package com.zeroindex.intake.ui.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.cost.AiMoney
import com.zeroindex.intake.domain.ai.cost.BudgetLevel
import com.zeroindex.intake.domain.ai.cost.PeriodTotals
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SettingRow
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.theme.IntakeShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Use AI features": the one switch above every AI entry point. Off means nothing is offered and nothing is sent. */
@Composable
internal fun AiMasterCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    IntakeCard {
        SettingRow(
            title = "Use AI features",
            subtitle = if (enabled) {
                "AI only runs when you tap something, or on a schedule you switch on below. Everything else in the app works without it."
            } else {
                "AI is off. Nothing is sent to any AI service, and AI buttons are hidden or say so."
            },
            showDivider = false,
        ) {
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

/** The monthly cap and where this month's spend stands against it. */
@Composable
internal fun AiBudgetCard(meter: AiMeterState, onSetCap: (String) -> Boolean) {
    val budget = meter.budget
    var text by remember(budget.capUsd) { mutableStateOf(trimMoney(budget.capUsd)) }
    var invalid by remember { mutableStateOf(false) }
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Monthly budget", modifier = Modifier.weight(1f))
            when (budget.level) {
                BudgetLevel.OK -> StatusPill("Within budget")
                BudgetLevel.NOTICE -> StatusPill("80% used", tone = PillTone.Wait)
                BudgetLevel.BLOCKED -> StatusPill("Paused", tone = PillTone.Bad)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${AiMoney.format(budget.spentUsd)} of ${AiMoney.format(budget.capUsd)} this month",
            style = MaterialTheme.typography.titleSmall,
        )
        LinearProgressIndicator(
            progress = { budget.fraction.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .semantics { contentDescription = "${(budget.fraction * 100).toInt()} percent of the monthly AI budget used" },
        )
        when (budget.level) {
            BudgetLevel.NOTICE -> SandNote(
                "You have used ${(budget.fraction * 100).toInt()}% of this month's AI budget. " +
                    "At 100% AI calls pause until next month, unless you raise the cap.",
            )
            BudgetLevel.BLOCKED -> SandNote(
                "The budget is used up, so AI calls are paused and nothing is sent. They start again on " +
                    "${dayLabel(meter.nextMonthStart)}, or raise the cap below.",
                title = "Budget reached",
            )
            BudgetLevel.OK -> Unit
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; invalid = false },
                label = { Text("Cap (USD a month)") },
                prefix = { Text("$") },
                singleLine = true,
                isError = invalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = IntakeShape.Field,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { invalid = !onSetCap(text) },
                shape = IntakeShape.Chip,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Set cap") }
        }
        if (invalid) {
            Text("Enter an amount above zero, like 2 or 5.50.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        Text(
            "Spend is the cost OpenRouter reports for each call, or the model's listed price when it reports none. " +
                "Resetting the counters below never changes this total.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Calls and dollars per feature for today, this week and this month. */
@Composable
internal fun AiUsageMeterCard(meter: AiMeterState, onReset: () -> Unit) {
    val usage = meter.usage
    IntakeCard {
        Eyebrow("AI usage")
        Spacer(Modifier.height(6.dp))
        if (usage.features.isEmpty()) {
            Text(
                if (meter.resetAt > 0) "No AI calls since you reset the counters." else "No AI calls yet this month.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1.2f))
                listOf("Today", "Week", "Month").forEach { Eyebrow(it, modifier = Modifier.weight(1f)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            usage.features.forEach { f ->
                MeterRow(f.feature.label, f.today, f.week, f.month)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MeterRow("Total", usage.today, usage.week, usage.month, bold = true)
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onReset,
                enabled = usage.features.isNotEmpty(),
                shape = IntakeShape.Chip,
                modifier = Modifier.heightIn(min = 44.dp),
            ) { Text("Reset counters") }
            Spacer(Modifier.width(10.dp))
            Text(
                "Starts the counts again from now. Your history stays on this phone" +
                    (if (meter.resetAt > 0) " · last reset ${dayLabel(meter.resetAt)}" else "") + ".",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MeterRow(label: String, today: PeriodTotals, week: PeriodTotals, month: PeriodTotals, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal,
            modifier = Modifier.weight(1.2f),
        )
        listOf(today, week, month).forEach { t ->
            Column(Modifier.weight(1f)) {
                Text(AiMoney.format(t.usd), style = MaterialTheme.typography.labelMedium, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold)
                Text(
                    if (t.calls == 1) "1 call" else "${t.calls} calls",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** What the chosen models would cost per call, per day and per month at the uses per day the person sets. */
@Composable
internal fun AiCostCard(state: AiSettingsUiState, onUsesChange: (AiFeature, String) -> Unit) {
    IntakeCard {
        Eyebrow("Estimated cost")
        Text(
            "Per call = tokens × the model's price. Per day = per call × uses a day. Per month = 30 days. " +
                "Change a uses-a-day number to match how you really use it.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
        state.costRows.forEachIndexed { i, row ->
            CostRow(row, onUsesChange)
            if (i != state.costRows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Total", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("${AiMoney.format(state.costSummary.perDayUsd)} a day", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                Text("${AiMoney.format(state.costSummary.perMonthUsd)} a month", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (state.costSummary.unpriced.isNotEmpty()) {
            Text(
                "No price yet for ${state.costSummary.unpriced.joinToString { it.label }}: refresh the model list so a model can be chosen.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            "An estimate, not a bill. Prices come from the model list saved on this phone and change over time. " +
                "Once a feature has a few real calls, its own average size replaces the built-in guess.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun CostRow(row: CostRowUi, onUsesChange: (AiFeature, String) -> Unit) {
    val c = row.cost
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.feature.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                c.model?.let(::displayName) ?: "No model yet",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        row.recommendation?.let {
            Text(
                it.text,
                style = MaterialTheme.typography.labelSmall,
                color = if (it.benchmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
        }
        Text(
            "${tokens(c.inputTokens)} in · ${tokens(c.outputTokens)} out · ${c.source.label}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${c.perCallUsd?.let(AiMoney::format) ?: "—"} a call",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${AiMoney.format(c.perDayUsd)} a day · ${AiMoney.format(c.perMonthUsd)} a month",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                row.offNote?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            UsesField(c.feature, row.usesPerDay, onUsesChange)
        }
    }
}

@Composable
private fun UsesField(feature: AiFeature, value: Double, onChange: (AiFeature, String) -> Unit) {
    var text by remember(feature, value) { mutableStateOf(trimUses(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(feature, it)
        },
        label = { Text("Uses a day") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = IntakeShape.Field,
        modifier = Modifier.width(116.dp),
    )
}

/** Everything that can run without a tap, all off until switched on here. */
@Composable
internal fun AutomaticAiCard(
    settings: AppSettings,
    onInsight: (Boolean) -> Unit,
    onReword: (Boolean) -> Unit,
    onMenuRefresh: (Boolean) -> Unit,
) {
    IntakeCard {
        Eyebrow("Scheduled AI · all off by default")
        Spacer(Modifier.height(4.dp))
        SettingRow(
            title = "Daily insight",
            subtitle = "One suggestion a day on Today. Sends a summary of the last two weeks of " +
                "food, training and body data to the chosen model.",
        ) {
            Switch(checked = settings.dailyInsightEnabled, onCheckedChange = onInsight, enabled = settings.aiEnabled)
        }
        SettingRow(
            title = "Reword my coach check-in",
            subtitle = "Rewrites the weekly check-in's wording. Only the suggestion texts go out; the numbers and actions never change.",
        ) {
            Switch(checked = settings.coachAiReword, onCheckedChange = onReword, enabled = settings.aiEnabled)
        }
        SettingRow(
            title = "Refresh saved menus weekly",
            subtitle = "Re-reads your saved menu sources once a week. Pages with structured menu data need no AI; " +
                "only the rest use the model.",
            showDivider = false,
        ) {
            Switch(checked = settings.menuAutoRefresh, onCheckedChange = onMenuRefresh, enabled = settings.aiEnabled)
        }
    }
}

private fun trimMoney(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

private fun trimUses(v: Double): String = when {
    v == v.toLong().toDouble() -> v.toLong().toString()
    else -> String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
}

private fun tokens(n: Int): String = if (n >= 1000) String.format(Locale.US, "%.1fk", n / 1000.0).replace(".0k", "k") else n.toString()

private fun dayLabel(millis: Long): String =
    DateTimeFormatter.ofPattern("MMM d").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
