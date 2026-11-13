package com.zeroindex.intake.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.PrivacyTag
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.SubCopy

/**
 * SPEC §13 and the amendments: every way data can leave this phone, grouped by
 * who receives it. docs/PRIVACY.md is the same list with code references.
 */
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SubCopy(
            "The app works fully offline and without any key. Nothing is sent unless a " +
                "feature you switched on needs it. No account, no analytics, no crash reports, no ads.",
        )

        SectionLine("Never leaves")
        IntakeCard {
            Para(
                "Your food log, weight, measurements, workouts, meal plan, allergen profile and " +
                    "photos stay in this app's private storage. Allergen decisions are made on " +
                    "the phone by its own matcher; an AI answer can add a warning but never " +
                    "clear one. Your API keys are stored encrypted on this device and sent only " +
                    "to the service they belong to. Android's own cloud backup is off.",
            )
        }

        SectionLine("Only when you use it")
        PathRow(
            title = "Food search",
            tag = "Sends your search words to USDA",
            pill = "Needs your USDA key",
            sent = "The words you type and your own USDA key. Nothing else about you.",
            where = "api.nal.usda.gov (FoodData Central)",
            whenSent = "When you search for a food, or press Test on the key.",
        )
        PathRow(
            title = "Barcode lookup",
            tag = "Sends a barcode to Open Food Facts",
            sent = "The barcode number. No key, no account. A barcode scanned before is found on the phone and sends nothing.",
            where = "world.openfoodfacts.org",
            whenSent = "When you scan a barcode this phone has not seen.",
        )
        PathRow(
            title = "Packaged food search",
            tag = "Sends your search words to Open Food Facts",
            pill = "On, can be switched off",
            sent = "The words you type, and the app name and version. No key, no account. The offline USDA food search sends nothing at all.",
            where = "search.openfoodfacts.org",
            whenSent = "While you type in Log food and the phone is online. Switch it off in Food, Food database & credits.",
        )
        PathRow(
            title = "Contribute to Open Food Facts",
            tag = "Sends a product you made, with your own account",
            pill = "Off until you turn it on",
            sent = "The barcode, name, brand, quantity, nutrition per 100 g and ingredients if you entered them, plus your own Open Food Facts username and password in the request body over HTTPS. It becomes public under the ODbL licence.",
            where = "world.openfoodfacts.org",
            whenSent = "Only when you press Send in the confirmation that lists exactly what goes. Never automatically.",
        )
        PathRow(
            title = "Weather for your water target",
            tag = "Sends a rounded location or a city to Open-Meteo",
            pill = "Off until you turn it on",
            sent = "Either coordinates rounded to about 10 km, or the city or ZIP code you typed. No key, no account, nothing about your log.",
            where = "api.open-meteo.com and geocoding-api.open-meteo.com",
            whenSent = "Once a day when Today opens, and when you press Find for a typed place. Turn it off in Settings, Water.",
        )
        PathRow(
            title = "Model list",
            tag = "Public list, nothing of yours",
            pill = "On",
            sent = "An empty request: no key, no personal data.",
            where = "openrouter.ai",
            whenSent = "When you open Settings, AI, or run an AI task, if the saved list is over a day old. Not at all while Use AI features is off.",
        )
        PathRow(
            title = "AI photo estimate",
            tag = "Sends photo to OpenRouter",
            pill = "Needs your OpenRouter key",
            sent = "The resized photo, plus the names of the allergens you avoid so the answer can speak to them. The photo also stays on the phone.",
            where = "openrouter.ai, then the model provider you chose",
            whenSent = "When you take or pick a photo and ask for an estimate.",
        )
        PathRow(
            title = "Recipe Hunter",
            tag = "Web search by OpenRouter",
            pill = "Needs your OpenRouter key",
            sent = "Your macro and time limits, cuisines, notes and the allergens you avoid. OpenRouter searches and reads the recipe pages for you; this phone does not visit them.",
            where = "openrouter.ai, which fetches the recipe sites",
            whenSent = "When you press Find recipes.",
        )
        PathRow(
            title = "Nearby restaurants and their menus",
            tag = "Sends a rounded location to OpenStreetMap's Overpass service",
            pill = "No key needed",
            sent = "Your location rounded to about 1 km and the search radius (your 10 or 15 minute walk or drive), in one request. Nothing about you, your allergens or your food. Then each restaurant's own nutrition page or website is fetched like any link you import, with no AI.",
            where = "overpass-api.de, then the restaurants' own sites",
            whenSent = "Only when you tap Search under \"Find healthier options nearby\" (Eating out) or in Swap. One map request per search; location is read only then. If you switch on \"Use AI to find menus I can't\" for that search, the restaurant name and website go to OpenRouter for places where nothing else worked.",
        )
        PathRow(
            title = "Swap: eat out nearby (AI search)",
            tag = "Sends coarse location to OpenRouter",
            pill = "Needs your OpenRouter key",
            sent = "Your location rounded to about 1 km, or the zip code you typed; the search radius from Settings; what is left of today's calories and protein; the allergens you avoid. OpenRouter searches for nearby places and reads their menus.",
            where = "openrouter.ai, which fetches restaurant sites",
            whenSent = "Only when you tap Search under \"Find healthy options nearby\" in Swap. Location needs the approximate-location permission and is read only then.",
        )
        PathRow(
            title = "Menu import",
            tag = "Sends menu text, URL or PDF to OpenRouter",
            pill = "Needs your OpenRouter key",
            sent = "The pasted text, the PDF file, or the page text this phone fetched from the web address you gave (for pages that need scripts, just the address, which OpenRouter reads itself), plus the allergens you avoid.",
            where = "The menu address you entered (an ordinary page request from this phone), then openrouter.ai",
            whenSent = "When you import a menu. Pages that publish their menu as structured data are read on this phone with no AI. Once a week on Wi-Fi for each web address or PDF you left enabled, but only if you switched on Refresh saved menus weekly in Settings, AI. Pasted text is not re-sent.",
        )
        PathRow(
            title = "Daily insight",
            tag = "Sends a numbers-only digest to OpenRouter",
            pill = "Off by default",
            tone = PillTone.Wait,
            sent = "About two weeks of averages and trends: calories and protein against targets, weight trend, training counts, sleep, steps, water. No food names, notes, dates or places. The allergens you avoid are added. The card's Why shows the digest lines.",
            where = "openrouter.ai",
            whenSent = "At most once a day, when you open Today, if you switched it on in Settings, AI and have a key.",
        )
        PathRow(
            title = "Key check",
            tag = "Sends your key to OpenRouter",
            sent = "Your OpenRouter key, in the Authorization header only, to read your remaining credit.",
            where = "openrouter.ai",
            whenSent = "When you save or test the key.",
        )
        IntakeCard {
            Para(
                "Every AI request asks providers not to store or train on it (data_collection: " +
                    "deny). Settings, AI can tighten that to zero retention or loosen it. If no " +
                    "provider for a model meets the setting, the request fails rather than " +
                    "falling back.",
            )
        }

        SectionLine("To servers you run")
        PathRow(
            title = "Nightly backup",
            tag = "Sends an encrypted copy to your server",
            pill = "Off by default",
            tone = PillTone.Wait,
            sent = "Every table in the database, encrypted on the phone with your passphrase before it leaves. Photo files are not included. Never sent unencrypted.",
            where = "The WebDAV, SFTP or SMB server you set up",
            whenSent = "Around 2 AM, once a day on a network, only after you set a server and a passphrase.",
        )
        PathRow(
            title = "Device sync",
            tag = "Sends readable rows to your sync server",
            pill = "Off by default",
            tone = PillTone.Wait,
            sent = "Every table except backup history, as plain rows over HTTPS (plain HTTP only to an address on your home network). Your pairing token goes in the Authorization header. Photo files and API keys are not sent. The server keeps the rows unencrypted.",
            where = "The Intake sync server you run and paired with",
            whenSent = "About 30 seconds after a change, every 30 minutes, and on Sync now, only while paired.",
        )

        SectionLine("Between your own devices")
        PathRow(
            title = "Wear OS watch",
            tag = "Phone and watch via Google Play services",
            sent = "The workout in progress: its name, exercises, planned and logged sets, and weight unit. The watch sends back the sets you log on it. Intake itself sends nothing to Google.",
            where = "Your paired watch, through the Data Layer of Google Play services",
            whenSent = "While a workout is in progress and a watch is paired.",
        )
        PathRow(
            title = "Health Connect",
            tag = "On-device hand-off",
            pill = "Off by default",
            tone = PillTone.Wait,
            sent = "Only the types you grant. Reads: exercise, calories, steps, sleep, heart rate, weight, body fat, lean mass. Writes: nutrition, weight, water, workouts. It goes to the Health Connect store on this phone, not to a server.",
            where = "Health Connect on this phone",
            whenSent = "When you open the app or log something, for permissions you granted.",
        )
        PathRow(
            title = "NFC meal tags",
            tag = "Local only",
            sent = "A tag holds only a link to a meal template and this app's name. Tapping one logs that meal on the phone. No food data is written to the tag.",
            where = "The tag you tap",
            whenSent = "When you write a tag from Meal prep tags, or tap one.",
        )

        SectionLine("Files and links you choose")
        PathRow(
            title = "Exports and reports",
            tag = "Only where you save them",
            sent = "The JSON export (encrypted if you set a passphrase), the CSV and the weekly PDF go to the location you pick, or to the print service you choose. Intake does not upload them.",
            where = "The folder or printer you choose",
            whenSent = "When you press Export, Save PDF or Print.",
        )
        PathRow(
            title = "Links that open in a browser",
            tag = "Opens another app",
            sent = "Open the original page on a recipe opens that address in your browser, and an absent Health Connect opens its Play Store page. Those apps then see what you open.",
            where = "Your browser or the Play Store",
            whenSent = "Only when you tap the button.",
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PathRow(
    title: String,
    tag: String,
    sent: String,
    where: String,
    whenSent: String,
    pill: String? = null,
    tone: PillTone = PillTone.Ok,
) {
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (pill != null) StatusPill(pill, tone = tone)
        }
        PrivacyTag(tag, Modifier.padding(top = 6.dp))
        Detail("Sent", sent)
        Detail("To", where)
        Detail("When", whenSent)
    }
}

@Composable
private fun Detail(label: String, text: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Para(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
