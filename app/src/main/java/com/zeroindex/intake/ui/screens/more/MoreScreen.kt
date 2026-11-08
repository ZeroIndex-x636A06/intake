package com.zeroindex.intake.ui.screens.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.BuildConfig
import com.zeroindex.intake.ui.components.ScreenHeading
import com.zeroindex.intake.ui.navigation.Route

private data class MoreEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: Route,
)

@Composable
fun MoreScreen(
    contentPadding: PaddingValues,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = listOf(
        // SPEC §7.6: the reference screen is two taps from anywhere, so it sits
        // first in this list.
        MoreEntry(
            title = "Allergen reference",
            subtitle = "The hidden-dairy checklist and eat-out notes, offline",
            icon = Icons.Filled.Warning,
            route = Route.AllergenReference,
        ),
        MoreEntry(
            title = "Add a weigh-in",
            subtitle = "Weight, body fat and tape measurements",
            icon = Icons.Filled.MonitorWeight,
            route = Route.BodyMetrics,
        ),
        MoreEntry(
            title = "Settings",
            subtitle = "Units, targets, API keys, allergens, export",
            icon = Icons.Filled.Settings,
            route = Route.Settings,
        ),
        MoreEntry(
            title = "Privacy",
            subtitle = "Exactly what leaves this phone, and when",
            icon = Icons.Filled.Lock,
            route = Route.Privacy,
        ),
    )

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item(key = "heading") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ScreenHeading("More")
                Spacer(Modifier.height(8.dp))
            }
        }

        items(entries.size, key = { entries[it].title }) { index ->
            val entry = entries[index]
            ListItem(
                headlineContent = { Text(entry.title) },
                supportingContent = { Text(entry.subtitle) },
                leadingContent = { Icon(entry.icon, contentDescription = null) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable { onNavigate(entry.route) },
            )
            HorizontalDivider()
        }

        item(key = "version") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Intake ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Build ${BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
