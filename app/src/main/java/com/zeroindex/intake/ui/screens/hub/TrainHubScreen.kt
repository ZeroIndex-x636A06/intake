package com.zeroindex.intake.ui.screens.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import com.zeroindex.intake.ui.motion.MoreDetail
import com.zeroindex.intake.ui.motion.pressScale
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.screens.train.NoProgramChoices
import com.zeroindex.intake.ui.screens.train.TrainViewModel
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * The Train tab root: start or carry on with today's workout, then the sections. The workout
 * card works with no program at all, because an empty workout is always one tap away.
 */
@Composable
fun TrainHubScreen(
    contentPadding: PaddingValues,
    onOpenSession: (String) -> Unit,
    onOpenPrograms: () -> Unit,
    onOpenBuilder: () -> Unit,
    onEditWorkout: (programId: String) -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenExercises: () -> Unit,
    onOpenPerformance: () -> Unit,
    onOpenProgramScriptHelp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HubScreen(
        title = "Train",
        subtitle = "Start a workout, or build the plan behind it.",
        contentPadding = contentPadding,
        modifier = modifier,
        tip = {
            com.zeroindex.intake.ui.onboarding.TabTip(
                key = com.zeroindex.intake.domain.onboarding.TipKey.TRAIN,
                body = com.zeroindex.intake.ui.onboarding.TabTipCopy.TRAIN,
                actionLabel = "Make me a program",
                onAction = onOpenGenerator,
            )
        },
        before = {
            item(key = "workout") {
                val inProgress = state.inProgress
                val nextDay = state.nextDay
                if (inProgress == null && state.activeProgram == null) {
                    NoProgramChoices(
                        onBuild = onOpenBuilder,
                        onGenerate = onOpenGenerator,
                        onTemplate = onOpenTemplates,
                        onFreestyle = { viewModel.startFreestyle(onOpenSession) },
                    )
                } else {
                    IntakeCard {
                        Eyebrow(
                            when {
                                inProgress != null -> "Session in progress"
                                nextDay != null -> "Next workout"
                                else -> "Workout"
                            },
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when {
                                inProgress != null -> "Pick up where you left off"
                                nextDay != null -> nextDay.name
                                else -> "Your program has no days yet"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (inProgress != null) onOpenSession(inProgress.id)
                                    else viewModel.startSession(nextDay?.id, onOpenSession)
                                },
                                enabled = !state.isWorking && (inProgress != null || nextDay != null),
                                shape = IntakeShape.Button,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).pressScale(),
                            ) {
                                Text(
                                    when {
                                        inProgress != null -> "Carry on"
                                        nextDay != null -> "Start today's workout"
                                        else -> "Add a day first"
                                    },
                                )
                            }
                            if (inProgress == null) {
                                TextButton(
                                    onClick = { viewModel.startFreestyle(onOpenSession) },
                                    enabled = !state.isWorking,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) { Text("Or start an empty workout") }
                            }
                        }
                    }
                }
            }
        },
        items = listOf(
            HubItem(
                title = "Programs",
                description = "Your programs. Build, edit, import or export",
                icon = Icons.AutoMirrored.Filled.ListAlt,
                onClick = onOpenPrograms,
            ),
            HubItem(
                title = "Make one for me",
                description = "Answer a few questions and get a plan, with the reasons",
                icon = Icons.Filled.AutoAwesome,
                onClick = onOpenGenerator,
            ),
            HubItem(
                title = "Exercises",
                description = "Browse and search the library, add your own",
                icon = Icons.Filled.FitnessCenter,
                onClick = onOpenExercises,
            ),
        ),
        after = {
            item(key = "my-workouts") {
                com.zeroindex.intake.ui.screens.train.MyWorkoutsSection(onOpenSession = onOpenSession, onEditWorkout = onEditWorkout)
            }
            // Rarely needed, so folded away instead of lengthening the list.
            item(key = "more-train") {
                MoreDetail(title = "More training tools") {
                    HubList(
                        listOf(
                            HubItem(
                title = "Templates",
                description = "Copy a ready-made program and make it yours",
                icon = Icons.Filled.ContentCopy,
                onClick = onOpenTemplates,
            ),
                            HubItem(
                title = "Performance",
                description = "Jump tests: vertical, standing reach, total reach",
                icon = Icons.Filled.Height,
                onClick = onOpenPerformance,
            ),
                            HubItem(
                title = "Program script help",
                description = "Write a program and its progression rules by hand",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = onOpenProgramScriptHelp,
            ),
                        ),
                    )
                }
            }
        },
    )
}
