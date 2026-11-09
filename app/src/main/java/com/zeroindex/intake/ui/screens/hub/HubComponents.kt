package com.zeroindex.intake.ui.screens.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.components.Tag
import com.zeroindex.intake.ui.motion.pressScale
import com.zeroindex.intake.ui.motion.rememberStaggerGate
import com.zeroindex.intake.ui.motion.staggerIn
import com.zeroindex.intake.ui.theme.IntakeShape

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
    title: String,
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "heading") { SubHead(title, subtitle) }
        if (tip != null) item(key = "tip") { tip() }
        before()
        item(key = "sections") { HubList(items) }
        after()
    }
}

internal fun hubPadding(contentPadding: PaddingValues) = PaddingValues(
    top = contentPadding.calculateTopPadding(),
    bottom = contentPadding.calculateBottomPadding() + 24.dp,
    start = 16.dp,
    end = 16.dp,
)

/** The rows of a hub on one surface, separated by hairlines rather than stacked as separate cards. */
@Composable
fun HubList(items: List<HubItem>, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = IntakeShape.Card,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        val gate = rememberStaggerGate()
        Column {
            items.forEachIndexed { index, item ->
                HubRow(item, Modifier.staggerIn(gate, item.title, index))
                if (index != items.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 72.dp),
                    )
                }
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
            .heightIn(min = 76.dp)
            .alpha(if (item.enabled) 1f else 0.55f)
            .clickable(enabled = item.enabled, role = Role.Button, onClick = item.onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = IntakeShape.Tile,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.tag != null) {
            Spacer(Modifier.width(8.dp))
            Tag(item.tag)
        }
        if (item.enabled) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
