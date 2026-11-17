package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.training.ExerciseRecords
import com.zeroindex.intake.domain.training.RecordText
import com.zeroindex.intake.domain.units.WeightUnit

/**
 * The records of one exercise: heaviest weight, best estimated 1RM, best session volume and the most reps at each weight.
 * Nothing is drawn when the exercise has no logged sets.
 */
@Composable
fun RecordsBlock(records: ExerciseRecords, unit: WeightUnit, modifier: Modifier = Modifier) {
    if (records.isEmpty) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RecordText.lines(records, unit).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(line.title, style = MaterialTheme.typography.bodyMedium)
                    line.detail?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(line.value, style = MaterialTheme.typography.titleSmall)
            }
        }
        val reps = RecordText.repsAtWeight(records, unit)
        if (reps.isNotEmpty()) {
            Text("Most reps at a weight", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reps.take(12).forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
