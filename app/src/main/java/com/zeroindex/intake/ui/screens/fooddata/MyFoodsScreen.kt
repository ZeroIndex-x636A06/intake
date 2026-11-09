package com.zeroindex.intake.ui.screens.fooddata

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.repository.FoodRepository
import com.zeroindex.intake.ui.components.SubHead
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MyFoodsViewModel @Inject constructor(foods: FoodRepository) : ViewModel() {
    val foods: StateFlow<List<FoodEntity>> = foods.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Every food on this phone, newest first. Tap one to edit it or contribute it. */
@Composable
fun MyFoodsScreen(
    contentPadding: PaddingValues,
    onOpenFood: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyFoodsViewModel = hiltViewModel(),
) {
    val foods by viewModel.foods.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("") }
    val shown = remember(foods, filter) {
        val needle = filter.trim().lowercase()
        if (needle.isEmpty()) foods else foods.filter { (it.name + " " + it.brand.orEmpty()).lowercase().contains(needle) }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(
            top = contentPadding.calculateTopPadding() + 12.dp,
            start = 16.dp,
            end = 16.dp,
        ),
    ) {
        SubHead("My foods", "Foods saved on this phone. Tap one to edit it.")
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Filter") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (shown.isEmpty()) {
            com.zeroindex.intake.ui.components.EmptyState(
                title = "No foods saved yet",
                body = "Open the Food tab and tap Log food. Every food you search for, scan or create is kept here.",
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
        ) {
            items(shown, key = { it.id }) { food ->
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpenFood(food.id) }.padding(vertical = 10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                com.zeroindex.intake.ui.images.FoodThumb(food)
                androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(food.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(
                        buildString {
                            food.brand?.let { append("$it · ") }
                            append("${(food.kcalPer100g * food.servingSizeGrams / 100.0).roundToInt()} kcal per ${food.servingLabel}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
