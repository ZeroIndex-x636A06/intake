package com.zeroindex.intake.ui.screens.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.components.SubCopy
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.IconTile
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.ListCard
import com.zeroindex.intake.ui.components.ListDivider
import com.zeroindex.intake.ui.components.RowChevron
import com.zeroindex.intake.ui.motion.pressScale
import com.zeroindex.intake.ui.motion.rememberStaggerGate
import com.zeroindex.intake.ui.motion.staggerIn
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeSpacing

/**
 * One row of a hub: a big tap target with an icon, a plain title and a one-line description.
 * [tag] is a small word such as "Optional AI"; a row with [enabled] false is a placeholder for
 * something another part of the app will add, and says so rather than hiding.
 */
data class HubItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit = {},
    val enabled: Boolean = true,
    val tag: String? = null,
)

/** The standard hub: a heading, an optional intro slot, then one calm list of [items]. */
@Composable
fun HubScreen(
    subtitle: String,
    items: List<HubItem>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    before: LazyListScope.() -> Unit = {},
    after: LazyListScope.() -> Unit = {},
    /** A first-visit tip shown under the heading (see `TabTip`). */
    tip: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = hubPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        item(key = "heading") { SubCopy(subtitle) }
        if (tip != null) item(key = "tip") { tip() }
        before()
        item(key = "sections") { HubList(items) }
        after()
    }
}

internal fun hubPadding(contentPadding: PaddingValues) = PaddingValues(
    top = contentPadding.calculateTopPadding(),
    bottom = contentPadding.calculateBottomPadding() + 24.dp,
    start = IntakeSpacing.ScreenGutter,
    end = IntakeSpacing.ScreenGutter,
)

/** The rows of a hub on one surface (`.list`), separated by hairlines rather than stacked as separate cards. */
@Composable
fun HubList(items: List<HubItem>, modifier: Modifier = Modifier) {
    ListCard(modifier = modifier) {
        val gate = rememberStaggerGate()
        Column {
            items.forEachIndexed { index, item ->
                if (index != 0) ListDivider()
                HubRow(item, Modifier.staggerIn(gate, item.title, index))
            }
        }
    }
}

@Composable
private fun HubRow(item: HubItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale()
            .heightIn(min = IntakeSpacing.ListRowMinHeight)
            .alpha(if (item.enabled) 1f else 0.55f)
            .clickable(enabled = item.enabled, role = Role.Button, onClick = item.onClick)
            .padding(horizontal = IntakeSpacing.ListRowPadH, vertical = IntakeSpacing.ListRowPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(item.icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.tag != null) {
            Spacer(Modifier.width(8.dp))
            // Every AI label carries the `ai` chip with its sparkle (b.2); any other tag stays neutral.
            val isAi = item.tag.contains("AI")
            IntakeChip(item.tag, tone = if (isAi) ChipTone.Ai else ChipTone.Neutral, icon = if (isAi) IntakeIcons.Sparkle else null, small = isAi)
        }
        if (item.enabled) {
            Spacer(Modifier.width(4.dp))
            RowChevron()
        }
    }
}
