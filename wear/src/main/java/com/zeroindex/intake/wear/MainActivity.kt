package com.zeroindex.intake.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import com.zeroindex.intake.wearprotocol.ActiveWorkout
import com.zeroindex.intake.wearprotocol.RPE_CHOICES
import com.zeroindex.intake.wearprotocol.formatWeight
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var workout: WatchWorkout
    private lateinit var remote: WatchRemote

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workout = WatchWorkout(applicationContext)
        remote = WatchRemote(applicationContext)
        setContent { MaterialTheme(colorScheme = WatchColorScheme) { WatchApp(workout, remote, ::buzz) } }
        // Wear OS 4 and later: the "workout in progress" notification needs this. Asked once; a no is fine.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onStart() {
        super.onStart()
        WatchVisibility.visible = true
        workout.start()
        remote.start()
    }

    override fun onStop() {
        WatchVisibility.visible = false
        workout.stop()
        remote.stop()
        super.onStop()
    }

    private fun buzz() {
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 200), -1))
    }
}

@Composable
private fun WatchApp(source: WatchWorkout, remote: WatchRemote, onRestOver: () -> Unit) {
    val workout by source.workout.collectAsStateWithLifecycle()
    val connected by source.phoneConnected.collectAsStateWithLifecycle()
    val pending by source.pending.collectAsStateWithLifecycle()
    var restLeft by rememberSaveable { mutableIntStateOf(0) }
    var restStartedFor by rememberSaveable { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        val w = workout
        when {
            // No workout (or an empty one the phone has just started): the quick remote.
            w == null || w.exercises.isEmpty() -> RemoteHome(remote, connected, workoutRunning = w != null)
            restLeft > 0 -> {
                KeepScreenOn()
                Rest(
                    seconds = restLeft,
                    onAdd = { restLeft += 30; restStartedFor += 30 },
                    onSkip = { restLeft = 0 },
                )
                LaunchedEffect(restLeft) {
                    delay(1_000)
                    restLeft = (restLeft - 1).coerceAtLeast(0)
                    if (restLeft == 0) onRestOver()
                }
            }
            else -> ActiveSet(
                workout = w,
                connected = connected,
                pending = pending,
                onLog = { index, reps, rpe ->
                    val rest = w.exercises[index].restSec
                    source.logSet(index, reps, rpe, restTakenSec = restStartedFor.takeIf { it > 0 })
                    restStartedFor = rest
                    restLeft = rest
                },
            )
        }
    }
}

/** Keeps the display on while a rest timer is counting, then lets it sleep. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun ActiveSet(
    workout: ActiveWorkout,
    connected: Boolean,
    pending: Int,
    onLog: (exerciseIndex: Int, reps: Int, rpe: Double?) -> Unit,
) {
    val index = workout.currentExerciseIndex
    val exercise = workout.exercises[index]
    val set = exercise.nextSet
    var reps by remember(exercise.exerciseId, set?.setIndex) { mutableIntStateOf(set?.reps ?: 0) }
    var rpe by remember(exercise.exerciseId, set?.setIndex) { mutableStateOf<Double?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (set == null) {
            WText("All sets done", fontWeight = FontWeight.SemiBold)
            WText("Finish the session on your phone.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            return@Column
        }
        WText(
            "${exercise.name.uppercase()} · SET ${set.setIndex + 1}/${exercise.sets.size}",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        WText("Prescribed", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WText(formatWeight(set.weightKg, workout.weightUnit), fontWeight = FontWeight.Bold, fontSize = 26.sp, numeric = true)
        WText("× ${set.reps} reps", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            FilledTonalButton(onClick = { reps = (reps - 1).coerceAtLeast(0) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "One rep fewer" }) {
                WText("−", fontSize = 20.sp)
            }
            WText("$reps", fontWeight = FontWeight.Bold, fontSize = 26.sp, numeric = true, modifier = Modifier.semantics { contentDescription = "$reps reps" })
            FilledTonalButton(onClick = { reps += 1 }, modifier = Modifier.size(48.dp).semantics { contentDescription = "One rep more" }) {
                WText("＋", fontSize = 20.sp)
            }
        }

        // The lime primary: logs the set with the reps and RPE shown.
        Button(
            onClick = { onLog(index, reps, rpe) },
            shape = CircleShape,
            modifier = Modifier.size(64.dp).semantics { contentDescription = "Log set: $reps reps" + (rpe?.let { " at RPE $it" } ?: "") },
        ) { WText("✓", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        WText("LOG SET", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RPE_CHOICES.forEach { value ->
                val selected = rpe == value
                Button(
                    onClick = { rpe = if (selected) null else value },
                    colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.size(width = 44.dp, height = 40.dp).semantics { contentDescription = "RPE $value" + if (selected) ", selected" else "" },
                ) { WText(if (value % 1.0 == 0.0) value.toInt().toString() else value.toString(), fontSize = 11.sp, numeric = true, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            }
        }
        WText("RPE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!connected || pending > 0) {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                    .padding(10.dp),
            ) {
                WText(
                    if (!connected) "Phone not connected · Sets are saved on the watch and sync when reconnected." else "$pending set${if (pending == 1) "" else "s"} waiting for the phone",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Rest(seconds: Int, onAdd: () -> Unit, onSkip: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WText("RESTING", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WText(
            "%02d:%02d".format(seconds / 60, seconds % 60),
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            numeric = true,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { contentDescription = "Rest, ${seconds / 60} minutes ${seconds % 60} seconds left" },
        )
        FilledTonalButton(onClick = onAdd) { WText("+30 s", fontWeight = FontWeight.SemiBold) }
        FilledTonalButton(onClick = onSkip) { WText("Skip", fontWeight = FontWeight.SemiBold) }
    }
}
