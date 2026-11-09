package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.goals.HowBuilt
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard

/** "How this plan was built": the answers that shaped the training plan, and the ones that did not. */
@Composable
fun HowBuiltCard(howBuilt: HowBuilt, modifier: Modifier = Modifier) {
    IntakeCard(modifier = modifier) {
        Eyebrow("How this plan was built")
        Spacer(Modifier.height(6.dp))
        Text("Used", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        howBuilt.used.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("Not used", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        howBuilt.notUsed.forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
