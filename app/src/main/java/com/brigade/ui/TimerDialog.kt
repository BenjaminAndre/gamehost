package com.brigade.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.brigade.R
import com.brigade.presentation.TimerOverlay

/**
 * Lighting, lengthening or putting out the incense.
 *
 * Two shapes, because the useful actions differ: an unlit timer offers durations, a burning
 * one offers *another minute* or *stop*. Restarting a burning stick would reset it visibly on
 * the players' screen, which says something quite different from being given more time.
 */
@Composable
fun TimerDialog(
    isRunning: Boolean,
    onStart: (minutes: Int) -> Unit,
    onExtend: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.timer_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                if (isRunning) {
                    Button(
                        onClick = onExtend,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.timer_extend))
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.timer_stop))
                    }
                } else {
                    TimerOverlay.DURATIONS_MINUTES.forEach { minutes ->
                        Button(
                            onClick = { onStart(minutes) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.timer_minutes, minutes))
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    }
}
