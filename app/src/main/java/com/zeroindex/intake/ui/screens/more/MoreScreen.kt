package com.zeroindex.intake.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.BuildConfig
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.MenuItem
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SubHead
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
            title = "Meal prep tags",
            subtitle = "Saved meals that log in one tap, or from an NFC tag",
            icon = Icons.Filled.Nfc,
            route = Route.MealTemplates,
        ),
        MoreEntry(
            title = "Menu import",
            subtitle = "Restaurant menus for Plan B, checked for allergens",
            icon = Icons.Filled.RestaurantMenu,
            route = Route.MenuSources,
        ),
        MoreEntry(
            title = "Jump test",
            subtitle = "Vertical jump, standing and total reach",
            icon = Icons.Filled.Height,
            route = Route.Performance,
        ),
        MoreEntry(
            title = "Health Connect",
            subtitle = "Watch and scale data, and how workout calories count",
            icon = Icons.Filled.MonitorHeart,
            route = Route.HealthConnect,
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
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "heading") {
            SubHead("More", "Your tools and preferences, all on this phone.")
        }

        item(key = "list") {
            SectionLine("Your Intake")
            IntakeCard {
                entries.forEachIndexed { index, entry ->
                    MenuItem(
                        icon = entry.icon,
                        title = entry.title,
                        subtitle = entry.subtitle,
                        trailing = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        showDivider = index != entries.lastIndex,
                        onClick = { onNavigate(entry.route) },
                    )
                }
            }
        }

        // SPEC §13: the privacy position is a feature, so it is stated on the
        // screen rather than buried one tap away.
        item(key = "privacy") {
            AccentNote(
                lead = "Private by design",
                body = "Your meals, training and weight stay on this device. No accounts, " +
                    "analytics or subscriptions. Only a barcode, a search term or a photo " +
                    "you choose to send ever leaves the phone.",
            )
        }

        item(key = "version") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Intake ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
