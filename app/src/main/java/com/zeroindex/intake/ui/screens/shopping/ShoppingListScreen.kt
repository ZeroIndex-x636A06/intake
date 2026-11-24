package com.zeroindex.intake.ui.screens.shopping

import com.zeroindex.intake.ui.theme.IntakeSpacing
import androidx.compose.material3.Surface
import com.zeroindex.intake.ui.theme.intake
import com.zeroindex.intake.ui.components.IconCircleButton
import com.zeroindex.intake.ui.components.ListCard
import com.zeroindex.intake.ui.components.ListDivider
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.NumberSmall
import com.zeroindex.intake.ui.components.LineButton
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.plan.GeneratedShoppingLine
import com.zeroindex.intake.domain.plan.ShoppingListBuilder
import com.zeroindex.intake.domain.plan.StandaloneList
import com.zeroindex.intake.domain.plan.StandaloneShopping
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SubHead

/**
 * A shopping list with no meal plan behind it (feedback round 4, item 11). Pick recipes and meal templates, say how
 * many times each is made, and the amounts are added up into one list grouped by store section. Ticks and picks are
 * kept while the screen is open and across rotation; nothing is stored in the database and nothing leaves the phone
 * unless "Share" is tapped. A plan user still has the plan's own list on the Plan screen.
 */
@Composable
fun ShoppingListScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ShoppingListViewModel = hiltViewModel(),
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val unit by viewModel.weightUnit.collectAsStateWithLifecycle()
    var recipePicks by rememberSaveable { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var templatePicks by rememberSaveable { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var ticked by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val context = LocalContext.current

    // Picks for something since deleted are ignored by the repository; the list is rebuilt when any pick changes.
    val list by produceState(StandaloneList(emptyList(), emptyList()), recipePicks, templatePicks, recipes, templates) {
        value = viewModel.build(recipePicks, templatePicks)
    }

    // Re-read when the list changes (the person's allergen settings are read each time).
    val marks by produceState(emptyMap<String, com.zeroindex.intake.ui.addfood.RowAllergen>(), list) { value = viewModel.marks(list) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = IntakeSpacing.ScreenGutter,
            end = IntakeSpacing.ScreenGutter,
        ),
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        item(key = "head") {
            // The top bar already says "Shopping list" (QA Q7-25): the heading here says what to do.
            SubHead("What are you making?", "Pick what you are making. The amounts are added up across everything you pick. No meal plan needed.")
        }

        if (recipes.isEmpty() && templates.isEmpty()) {
            item(key = "none") {
                EmptyState(
                    title = "Nothing to shop for yet",
                    body = "Save a recipe or make a meal template, then pick them here.",
                )
            }
        }

        if (recipes.isNotEmpty()) {
            item(key = "h-recipes") { SectionLine("Recipes", note = "${recipePicks.size} picked") }
            item(key = "recipes-list") {
                ListCard {
                    recipes.forEachIndexed { i, r ->
                        if (i != 0) ListDivider()
                        PickRow(
                            name = r.name,
                            detail = "${r.servings} servings",
                            batches = recipePicks[r.id],
                            onToggle = { recipePicks = if (r.id in recipePicks) recipePicks - r.id else recipePicks + (r.id to 1) },
                            onBatches = { recipePicks = recipePicks + (r.id to it) },
                        )
                    }
                }
            }
        }

        if (templates.isNotEmpty()) {
            item(key = "h-templates") { SectionLine("Meal templates", note = "${templatePicks.size} picked") }
            item(key = "templates-list") {
                ListCard {
                    templates.forEachIndexed { i, t ->
                        if (i != 0) ListDivider()
                        PickRow(
                            name = t.template.name,
                            detail = "${t.items.size} ${if (t.items.size == 1) "item" else "items"}",
                            batches = templatePicks[t.template.id],
                            onToggle = {
                                val id = t.template.id
                                templatePicks = if (id in templatePicks) templatePicks - id else templatePicks + (id to 1)
                            },
                            onBatches = { templatePicks = templatePicks + (t.template.id to it) },
                        )
                    }
                }
            }
        }

        if (!list.isEmpty) {
            item(key = "h-list") {
                SectionLine(
                    "Your list",
                    note = "${list.count} ${if (list.count == 1) "item" else "items"} · ${ticked.count { k -> (list.sections.flatMap { it.second } + list.pantry).any { it.key == k } }} ticked",
                )
            }
            item(key = "actions") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineButton(
                        onClick = { shareList(context, StandaloneShopping.shareText("Shopping list", list, ticked, unit)) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Share") }
                    LineButton(
                        onClick = {
                            val oldRecipes = recipePicks
                            val oldTemplates = templatePicks
                            val oldTicked = ticked
                            recipePicks = emptyMap(); templatePicks = emptyMap(); ticked = emptySet()
                            viewModel.cleared { recipePicks = oldRecipes; templatePicks = oldTemplates; ticked = oldTicked }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Clear") }
                }
            }
            item(key = "note") {
                SandNote("Amounts come from the recipe text and the template amounts. Where a line has no amount, none is claimed. Check labels for allergens as you shop.")
            }
            list.sections.forEach { (category, lines) ->
                item(key = "cat-$category") { Eyebrow(ShoppingListBuilder.sectionTitle(category), Modifier.padding(top = 4.dp)) }
                item(key = "l-$category") {
                    ListCard {
                        lines.forEachIndexed { i, line ->
                            if (i != 0) ListDivider()
                            ListRow(line, unit, marks[line.key], line.key in ticked) { ticked = if (line.key in ticked) ticked - line.key else ticked + line.key }
                        }
                    }
                }
            }
            if (list.pantry.isNotEmpty()) {
                item(key = "cat-pantry") { Eyebrow("Pantry staples", Modifier.padding(top = 4.dp)) }
                item(key = "pantry-list") {
                    ListCard {
                        list.pantry.forEachIndexed { i, line ->
                            if (i != 0) ListDivider()
                            ListRow(line, unit, marks[line.key], line.key in ticked) { ticked = if (line.key in ticked) ticked - line.key else ticked + line.key }
                        }
                    }
                }
            }
        } else if (recipePicks.isNotEmpty() || templatePicks.isNotEmpty()) {
            item(key = "empty-list") {
                SandNote("Nothing to buy from what is picked: it has no ingredient lines.")
            }
        }
    }
}

@Composable
private fun PickRow(name: String, detail: String, batches: Int?, onToggle: () -> Unit, onBatches: (Int) -> Unit) {
    val picked = batches != null
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = IntakeSpacing.ListRowPadH - 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 56.dp).toggleable(value = picked, role = Role.Checkbox, onValueChange = { onToggle() }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = picked, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (batches != null) {
            // `.stepper`: a soft pill with a round minus and plus around the count.
            Surface(color = MaterialTheme.intake.soft, shape = IntakeShape.Pill) {
                Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconCircleButton(
                        IntakeIcons.Minus, "Make $name fewer times",
                        onClick = { onBatches((batches - 1).coerceAtLeast(1)) }, enabled = batches > 1,
                    )
                    Text("×$batches", style = NumberSmall, modifier = Modifier.padding(horizontal = 6.dp))
                    IconCircleButton(
                        IntakeIcons.Plus, "Make $name more times",
                        onClick = { onBatches((batches + 1).coerceAtMost(StandaloneShopping.MAX_BATCHES)) },
                        enabled = batches < StandaloneShopping.MAX_BATCHES,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListRow(line: GeneratedShoppingLine, unit: WeightUnit, allergen: com.zeroindex.intake.ui.addfood.RowAllergen?, done: Boolean, onToggle: () -> Unit) {
    val qty = ShoppingListBuilder.displayQuantity(line.quantity, unit)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = IntakeSpacing.ListRowPadH - 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = line.name })
        Column(Modifier.weight(1f)) {
            Text(
                listOf(line.name, qty).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            line.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            allergen?.let { Box(Modifier.padding(top = 2.dp)) { com.zeroindex.intake.ui.addfood.RowAllergenChip(it) } }
        }
    }
}

private fun shareList(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share shopping list"))
}
