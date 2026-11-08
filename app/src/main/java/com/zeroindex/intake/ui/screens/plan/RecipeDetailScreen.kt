package com.zeroindex.intake.ui.screens.plan

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.MathGrid
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.screens.today.SlotPickerSheet
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.roundToInt

@Composable
fun RecipeDetailScreen(
    recipeId: String,
    contentPadding: PaddingValues,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeLibraryViewModel = hiltViewModel(),
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val recipe = recipes.firstOrNull { it.id == recipeId } ?: return
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var logged by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SubHead(recipe.name, "${recipe.servings} servings${if (recipe.totalTimeMinutes > 0) " · ${recipe.totalTimeMinutes} min" else ""}")
        if (recipe.allergenStatus == AllergenStatus.MAY_CONTAIN) {
            AccentNote(
                lead = "Check allergens before cooking",
                body = "When this was saved, an ingredient looked like it might contain something you avoid.",
                container = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        if (recipe.kcalPerServing != null) {
            MathGrid(
                listOf(
                    "${recipe.kcalPerServing.roundToInt()}" to "kcal",
                    "${recipe.proteinPerServing?.roundToInt() ?: 0}" to "protein g",
                    "${recipe.carbsPerServing?.roundToInt() ?: 0}" to "carbs g",
                    "${recipe.fatPerServing?.roundToInt() ?: 0}" to "fat g",
                ),
            )
        }
        if (recipe.ingredientsText.isNotBlank()) {
            SectionLine("Ingredients")
            recipe.ingredientsText.lines().filter(String::isNotBlank).forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall)
            }
        }
        SectionLine("Steps")
        Text(recipe.instructions, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = { picking = true },
            enabled = recipe.kcalPerServing != null,
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (logged) "Logged · log another serving" else "Log a serving") }
        recipe.sourceUrl?.let { url ->
            OutlinedButton(
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Open the original page") }
        }
        TextButton(onClick = { viewModel.delete(recipe); onDeleted() }) { Text("Delete recipe") }
        Spacer(Modifier.height(24.dp))
    }

    if (picking) {
        SlotPickerSheet(
            title = "Log a serving to",
            onDismiss = { picking = false },
            onPick = { slot -> viewModel.logServing(recipe, slot) { logged = true; picking = false } },
        )
    }
}
