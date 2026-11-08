package com.zeroindex.intake.ui.screens.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.BuildConfig
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.navigation.Route
import com.zeroindex.intake.ui.screens.hub.HubItem
import com.zeroindex.intake.ui.screens.hub.HubScreen

/**
 * The More tab: a handful of things, no more (SPEC-v3 IA). Everything that belongs to food,
 * training or progress lives in that tab's hub instead.
 */
@Composable
fun MoreScreen(
    contentPadding: PaddingValues,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    HubScreen(
        title = "More",
        subtitle = "Your profile and preferences, all on this phone.",
        contentPadding = contentPadding,
        modifier = modifier,
        items = listOf(
            HubItem(
                title = "Profile & goals",
                description = "Goal weight, calorie and protein targets",
                icon = Icons.Filled.Person,
                onClick = { onNavigate(Route.Settings(section = "targets")) },
            ),
            // The baseline week, body type and phase arrive later; the row holds their place.
            HubItem(
                title = "Body type, phase and baseline",
                description = "Coming soon",
                icon = Icons.Filled.Spa,
                enabled = false,
            ),
            HubItem(
                title = "Settings",
                description = "Units, theme, training and API keys",
                icon = Icons.Filled.Settings,
                onClick = { onNavigate(Route.Settings()) },
            ),
            HubItem(
                title = "Backup & sync",
                description = "Encrypted nightly backup to your server, export and import",
                icon = Icons.Filled.CloudUpload,
                onClick = { onNavigate(Route.Backup) },
            ),
            HubItem(
                title = "AI",
                description = "Photo estimates and Recipe Hunter. Off until you add a key",
                icon = Icons.Filled.AutoAwesome,
                onClick = { onNavigate(Route.AiSettings) },
                tag = "Optional",
            ),
            HubItem(
                title = "Privacy",
                description = "Exactly what leaves this phone, and when",
                icon = Icons.Filled.Lock,
                onClick = { onNavigate(Route.Privacy) },
            ),
            HubItem(
                title = "Help",
                description = "Script docs and a replay of the tour",
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                onClick = { onNavigate(Route.Help) },
            ),
        ),
        after = {
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
                Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp)) {
                    Text(
                        text = "Intake ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
