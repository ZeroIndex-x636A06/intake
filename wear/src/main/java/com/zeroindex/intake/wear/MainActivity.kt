package com.zeroindex.intake.wear

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
import androidx.compose.ui.graphics.Color
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
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.zeroindex.intake.wearprotocol.ActiveWorkout
import com.zeroindex.intake.wearprotocol.RPE_CHOICES
import com.zeroindex.intake.wearprotocol.formatWeight
import kotlinx.coroutines.delay

/** Mockup v2's watch colours: the phone's green on black, sand for the rest timer. */
private val WatchColors = ColorScheme(
    primary = Color(0xFF8FD6C4),
    onPrimary = Color(0xFF00201A),
    primaryContainer = Color(0xFF263D37),
    onPrimaryContainer = Color(0xFF8FD6C4),
    secondaryContainer = Color(0xFF363222),
    onSecondaryContainer = Color(0xFFD9C78E),
    errorContainer = Color(0xFF472B27),
    onErrorContainer = Color(0xFFFFB5A0),
    background = Color.Black,
    onBackground = Color(0xFFF1F0F5),
    onSurfaceVariant = Color(0xFFAAAAAA),
)

class MainActivity : ComponentActivity() {
    private lateinit var workout: WatchWorkout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workout = WatchWorkout(applicationContext)
        setContent { MaterialTheme(colorScheme = WatchColors) { WatchApp(workout, ::buzz) } }
    }

    override fun onStart() {
        super.onStart()
        workout.start()
    }

    override fun onStop() {
        workout.stop()
        super.onStop()
    }

    private fun buzz() {
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 200), -1))
    }
}

@Composable
private fun WatchApp(source: WatchWorkout, onRestOver: () -> Unit) {
    val workout by source.workout.collectAsStateWithLifecycle()
    val connected by source.phoneConnected.collectAsStateWithLifecycle()
    val pending by source.pending.collectAsStateWithLifecycle()
    var restLeft by rememberSaveable { mutableIntStateOf(0) }
    var restStartedFor by rememberSaveable { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        val w = workout
        when {
            w == null || w.exercises.isEmpty() -> Waiting(connected)
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
private fun Waiting(connected: Boolean) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("INTAKE", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text(
            if (connected) "Start a workout on your phone and it appears here." else "Phone not connected. Open Intake on your phone to start a workout.",
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
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
            Text("All sets done", fontWeight = FontWeight.Bold)
            Text("Finish the session on your phone.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            return@Column
        }
        Text(
            "${exercise.name.uppercase()} · SET ${set.setIndex + 1}/${exercise.sets.size}",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text("Prescribed", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(formatWeight(set.weightKg, workout.weightUnit), fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
        Text("× ${set.reps} reps", fontSize = 13.sp)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            FilledTonalButton(onClick = { reps = (reps - 1).coerceAtLeast(0) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "One rep fewer" }) {
                Text("−", fontSize = 20.sp)
            }
            Text("$reps", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.semantics { contentDescription = "$reps reps" })
            FilledTonalButton(onClick = { reps += 1 }, modifier = Modifier.size(48.dp).semantics { contentDescription = "One rep more" }) {
                Text("＋", fontSize = 20.sp)
            }
        }

        Button(
            onClick = { onLog(index, reps, rpe) },
            shape = CircleShape,
            modifier = Modifier.size(64.dp).semantics { contentDescription = "Log set: $reps reps" + (rpe?.let { " at RPE $it" } ?: "") },
        ) { Text("✓", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        Text("LOG SET", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RPE_CHOICES.forEach { value ->
                val selected = rpe == value
                Button(
                    onClick = { rpe = if (selected) null else value },
                    colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.size(width = 44.dp, height = 40.dp).semantics { contentDescription = "RPE $value" + if (selected) ", selected" else "" },
                ) { Text(if (value % 1.0 == 0.0) value.toInt().toString() else value.toString(), fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            }
        }
        Text("RPE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!connected || pending > 0) {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                    .padding(10.dp),
            ) {
                Text(
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
        Text("RESTING", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "%02d:%02d".format(seconds / 60, seconds % 60),
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.semantics { contentDescription = "Rest, ${seconds / 60} minutes ${seconds % 60} seconds left" },
        )
        FilledTonalButton(onClick = onAdd) { Text("+30 s") }
        FilledTonalButton(onClick = onSkip) { Text("Skip") }
    }
}
