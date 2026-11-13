package com.zeroindex.intake.domain.videos

/**
 * How to get a YouTube Data API key and how to restrict it, step by step. One text for the Videos tab and for
 * Settings > API keys, so the two never drift apart. The wording follows the Google Cloud console's own labels.
 */
object YoutubeKeyGuide {
    data class Section(val title: String, val steps: List<String>, val note: String? = null)

    val SECTIONS = listOf(
        Section(
            "Make the key (about 3 minutes, free)",
            listOf(
                "In a browser, open console.cloud.google.com and sign in with any Google account. No card is needed for this.",
                "At the top, tap the project name (or \"Select a project\"), then \"New project\". Name it anything, such as Intake, tap Create, and make sure that project is the one selected.",
                "Open the menu (three lines, top left) > APIs & Services > Library. Search for \"YouTube Data API v3\", open it and tap Enable.",
                "Go to APIs & Services > Credentials. Tap \"Create credentials\", then \"API key\". If it asks which API and what data, pick \"YouTube Data API v3\" and \"Public data\".",
                "Copy the key it shows. It is a long line of letters and numbers starting with \"AIza\".",
            ),
        ),
        Section(
            "Restrict the key (recommended)",
            listOf(
                "Still under Credentials, tap the key's name to edit it.",
                "Under \"API restrictions\", choose \"Restrict key\" and tick only \"YouTube Data API v3\". The key then works for YouTube and nothing else.",
                "Leave \"Application restrictions\" on \"None\". The \"Android apps\" choice ties the key to one exact build of an app, so it would stop working after an Intake update.",
                "Tap Save. Google says a change can take up to five minutes to apply.",
            ),
            "Restricted like this, the worst a leaked key can do is use up your free daily YouTube allowance (about 100 searches). " +
                "The YouTube Data API has no paid tier, so it cannot run up a bill, and the key gives no access to your Google account.",
        ),
        Section(
            "Put it in Intake",
            listOf(
                "Open Settings > API keys > YouTube Data API key, paste the key and tap Save. It is stored encrypted on this phone.",
                "Tap Test. \"Works\" means Videos can search; an error says what to fix.",
            ),
        ),
    )
}
