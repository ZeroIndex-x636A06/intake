package com.zeroindex.intake.ui.allergen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.domain.model.AllergenSeverity
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.intake
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
 * The strip at the top of Nutrition (R3-3): the person's allergens as chips and a status line ("Milk · strict"). It is the
 * shield chip of the design: the `ok` pair with a shield when allergens are set, the `warn` pair when nothing is set ("No
 * allergens set. Add yours"). Tapping either opens the one Allergens screen. It only reports what the person set; the local
 * engine does the checking, and this chip never says a food is safe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllergenStrip(onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false, viewModel: AllergenStripViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val empty = s.names.isEmpty()
    val c = MaterialTheme.intake
    val bg = if (empty) c.warnBg else c.okBg
    val tone = if (empty) c.warn else c.ok
    val icon = if (empty) IntakeIcons.ShieldAlert else IntakeIcons.Shield
    if (compact) {
        // "Avoiding: Milk and dairy ›": one line, the same destination and colours as the full strip.
        val label = if (empty) "No allergens set. Add yours" else "Avoiding: ${s.names.take(2).joinToString(" and ")}" + if (s.names.size > 2) " +${s.names.size - 2}" else ""
        Surface(
            onClick = onClick,
            color = bg,
            contentColor = tone,
            shape = IntakeShape.Pill,
            modifier = modifier.heightIn(min = 44.dp).semantics {
                contentDescription = if (empty) "No allergens set. Add yours" else "Allergens: ${s.title}. Open the allergens screen"
            },
        ) {
            Row(Modifier.padding(start = 12.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(IconSize.Small))
                Text(label, style = MaterialTheme.typography.labelMedium, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(IntakeIcons.ChevronRight, contentDescription = null, tint = tone, modifier = Modifier.size(IconSize.Small))
            }
        }
        return
    }
    Surface(
        onClick = onClick,
        color = bg,
        contentColor = tone,
        shape = IntakeShape.Card,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = if (empty) "No allergens set. Add yours" else "Allergens: ${s.title}. Open the allergens screen" },
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = tone, contentColor = bg, shape = IntakeShape.Tile, modifier = Modifier.size(44.dp)) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.Default))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (empty) "No allergens set" else s.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = tone,
                )
                if (empty) {
                    Text("Add yours so every food is checked.", style = MaterialTheme.typography.bodySmall, color = tone)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.names.take(4).forEach { name ->
                            Surface(color = MaterialTheme.colorScheme.surface, contentColor = tone, shape = IntakeShape.Pill) {
                                Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        if (s.names.size > 4) {
                            Text("+${s.names.size - 4}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
            Icon(IntakeIcons.ChevronRight, contentDescription = null, tint = tone, modifier = Modifier.size(IconSize.Default))
        }
    }
}
