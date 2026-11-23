package com.zeroindex.intake.ui.allergen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.zeroindex.intake.domain.allergen.CheckLabelTerm
import com.zeroindex.intake.domain.allergen.HiddenCarriers
import com.zeroindex.intake.ui.theme.intake

/**
 * The terms a name is checked against for the soft "Check the label" tier (the
 * built-in hidden-dairy carriers plus the plan's watch-for phrases), provided
 * once near the top of a screen so list rows can ask without any plumbing.
 */
val LocalCheckLabelTerms = compositionLocalOf<List<CheckLabelTerm>> { emptyList() }

@Composable
fun ProvideCheckLabelTerms(terms: List<CheckLabelTerm>, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCheckLabelTerms provides terms, content = content)
}

/** The reason to show beside [name], or null when nothing in it commonly hides an allergen. */
@Composable
fun checkLabelReasonFor(name: String?): String? {
    val terms = LocalCheckLabelTerms.current
    if (name.isNullOrBlank() || terms.isEmpty()) return null
    return HiddenCarriers.firstHit(terms, name)?.reason
}

/**
 * SPEC §7 soft tier: a calm outlined chip, "Check the label", with the reason.
 * It is not the red sheet and not the amber review sheet - nothing is blocked
 * and no extra tap is needed.
 */
@Composable
fun CheckLabelChip(reason: String?, modifier: Modifier = Modifier, showReason: Boolean = true) {
    Column(modifier) {
        Surface(
            color = MaterialTheme.intake.warnBg,
            contentColor = MaterialTheme.intake.warn,
            shape = CircleShape,
            modifier = Modifier.semantics {
                contentDescription = "Check the label." + (reason?.let { " $it." }.orEmpty())
            },
        ) {
            Text(
                text = "Check the label",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
        if (showReason && !reason.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
