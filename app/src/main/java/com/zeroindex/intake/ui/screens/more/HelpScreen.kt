package com.zeroindex.intake.ui.screens.more

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Rule
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.zeroindex.intake.ui.onboarding.HelpFlowViewModel
import androidx.compose.ui.Modifier
import com.zeroindex.intake.ui.screens.hub.HubItem
import com.zeroindex.intake.ui.screens.hub.HubScreen

/** More, Help: replay the tour, the welcome and the tips, and every script doc, each page one tap from here. */
@Composable
fun HelpScreen(
    contentPadding: PaddingValues,
    onOpenScriptDocs: (page: String) -> Unit,
    onGoToToday: () -> Unit,
    onOpenWelcome: () -> Unit,
    modifier: Modifier = Modifier,
    flow: HelpFlowViewModel = hiltViewModel(),
) {
    HubScreen(
        title = "Help",
        subtitle = "Everything works offline, including these docs.",
        contentPadding = contentPadding,
        modifier = modifier,
        items = listOf(
            HubItem(
                title = "Replay the tour",
                description = "A quick look around Today, one step at a time",
                icon = Icons.Filled.Replay,
                onClick = { flow.replayTour(then = onGoToToday) },
            ),
            HubItem(
                title = "Show the welcome again",
                description = "Run the setup questions again. Nothing is deleted and your answers are filled in",
                icon = Icons.Filled.WavingHand,
                onClick = { flow.showWelcomeAgain(then = onOpenWelcome) },
            ),
            HubItem(
                title = "Show screen tips again",
                description = "Bring back the short tip at the top of Food, Train, Progress and More",
                icon = Icons.Filled.Lightbulb,
                onClick = { flow.showTipsAgain(then = {}) },
            ),
            HubItem(
                title = "All script docs",
                description = "How Intake scripts work, start to finish",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = { onOpenScriptDocs("README") },
            ),
            HubItem(
                title = "Meal plan scripts",
                description = "Write a meal plan, recipes and eat-out options by hand",
                icon = Icons.Filled.Restaurant,
                onClick = { onOpenScriptDocs(ScriptTopic.FOOD.firstPage) },
            ),
            HubItem(
                title = "Program scripts",
                description = "Write a program, its days and exercises by hand",
                icon = Icons.Filled.FitnessCenter,
                onClick = { onOpenScriptDocs(ScriptTopic.TRAIN.firstPage) },
            ),
            HubItem(
                title = "Progression rules",
                description = "Add weight each session, work up the rep range, % of max, or go by effort",
                icon = Icons.Filled.Rule,
                onClick = { onOpenScriptDocs("progress-rules") },
            ),
            HubItem(
                title = "Fixing import errors",
                description = "What each error message means",
                icon = Icons.Filled.Rule,
                onClick = { onOpenScriptDocs("errors") },
            ),
            HubItem(
                title = "Write scripts with an AI",
                description = "A prompt to paste into any chat assistant",
                icon = Icons.Filled.AutoAwesome,
                onClick = { onOpenScriptDocs("ai-prompt") },
                tag = "Optional",
            ),
        ),
    )
}
