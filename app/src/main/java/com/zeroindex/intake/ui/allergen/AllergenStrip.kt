package com.zeroindex.intake.ui.allergen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddModerator
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.domain.model.AllergenSeverity
import com.zeroindex.intake.ui.theme.AllergenAmber
import com.zeroindex.intake.ui.theme.AllergenRed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the strip shows: the names the person avoids and how strictly. Null while the first read is pending. */
data class AllergenStripState(val names: List<String>, val strict: Boolean) {
    /** "Milk · strict", "Milk and peanuts · avoid", "4 allergens · strict". */
    val title: String
        get() {
            val label = when {
                names.size <= 2 -> names.joinToString(" and ")
                else -> "${names.size} allergens"
            }
            return "$label · ${if (strict) "strict" else "avoid"}"
        }
}

@HiltViewModel
class AllergenStripViewModel @Inject constructor(allergens: AllergenRepository) : ViewModel() {
    val state: StateFlow<AllergenStripState?> = allergens.observeOverview()
        .map { o -> AllergenStripState(o.items.filter { it.avoided }.map { it.displayName }, o.severity == AllergenSeverity.STRICT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * The strip at the top of Nutrition (R3-3): the person's allergens as chips and a status line ("Milk · strict"), in the
 * red allergen colour. With nothing set it turns amber: "No allergens set. Add yours". Tapping either opens the one
 * Allergens screen. It only reports what the person set; the local engine does the checking.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllergenStrip(onClick: () -> Unit, modifier: Modifier = Modifier, viewModel: AllergenStripViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val empty = s.names.isEmpty()
    val tone = if (empty) AllergenAmber else AllergenRed
    Surface(
        onClick = onClick,
        color = tone.copy(alpha = 0.14f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = if (empty) "No allergens set. Add yours" else "Allergens: ${s.title}. Open the allergens screen" },
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = tone, contentColor = AllergenColors.onSolid, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (empty) Icons.Filled.AddModerator else Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (empty) "No allergens set" else s.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (empty) {
                    Text("Add yours so every food is checked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.names.take(4).forEachIndexed { i, name ->
                            Surface(
                                color = if (i == 0) AllergenRed else tone.copy(alpha = 0.10f),
                                contentColor = if (i == 0) AllergenColors.onSolid else MaterialTheme.colorScheme.onSurface,
                                border = if (i == 0) null else BorderStroke(1.dp, tone.copy(alpha = 0.6f)),
                                shape = CircleShape,
                            ) {
                                Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        if (s.names.size > 4) {
                            Text("+${s.names.size - 4}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
