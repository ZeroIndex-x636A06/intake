package com.zeroindex.intake.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.components.ScreenHeading

/** SPEC §13: states exactly what leaves the device and when. */
@Composable
fun PrivacyScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeading("What leaves this phone")

        Para(
            "Everything you log lives in one database in this app's private storage. There is " +
                "no account, no server and no analytics. Cloud backup and device transfer are " +
                "both switched off, so nothing syncs anywhere on its own.",
        )

        Heading("Three things go out, and only when you ask")
        Bullet(
            "Food search",
            "The words you type go to the USDA food database, with your own API key. Nothing " +
                "about you goes with it.",
        )
        Bullet(
            "Barcode lookup",
            "The barcode number goes to Open Food Facts. No key, no account, just the number.",
        )
        Bullet(
            "AI photo estimate",
            "The photo you took, resized, goes to the Anthropic API with your own API key, " +
                "along with the allergens you avoid so the answer can speak to them. The " +
                "photo stays on this phone as well, attached to the entry.",
        )

        Heading("Nothing else")
        Para(
            "Your weight, measurements, workouts, meal plan and photos are never uploaded. " +
                "Health Connect, if you turn it on, writes to the copy of Health Connect on " +
                "this phone - that is a local hand-off, not an upload.",
        )

        Heading("Your keys")
        Para(
            "API keys are yours, entered by you, and stored encrypted on this device. They " +
                "are never in the app's source and never sent anywhere but the service they " +
                "belong to. Without any key the app still works - you lose remote search and " +
                "the photo estimate, nothing else.",
        )

        Heading("Your backups")
        Para(
            "There are none but the ones you make. Settings has a full JSON export; that file " +
                "is the backup, and where you put it is up to you. Nothing is ever hard " +
                "deleted inside the app, so a mis-tap is recoverable from an export.",
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun Para(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Bullet(title: String, body: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
