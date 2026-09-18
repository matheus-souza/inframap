package com.inframap.frontend.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.motion.m3Clickable
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.impact_dialog_cancel_button
import com.inframap.frontend.generated.resources.impact_dialog_confirm_button
import com.inframap.frontend.generated.resources.impact_dialog_loading
import com.inframap.frontend.generated.resources.impact_dialog_title
import org.jetbrains.compose.resources.stringResource

/**
 * Impact confirmation modal dialog for destructive actions with impact summaries.
 *
 * Prevents accidental destructive clicks by requiring deliberate operator confirmation,
 * disabling confirmation while impact is being calculated, and supporting ESC / outside-click
 * dismissal.
 */
@Suppress("LongParameterList")
@Composable
fun ImpactConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.impact_dialog_title),
    description: String? = null,
    impactLines: List<String> = emptyList(),
    isLoadingImpact: Boolean = false,
    confirmButtonText: String = stringResource(Res.string.impact_dialog_confirm_button),
    cancelButtonText: String = stringResource(Res.string.impact_dialog_cancel_button),
) {
    val confirmInteractionSource = remember { MutableInteractionSource() }
    val dismissInteractionSource = remember { MutableInteractionSource() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            ImpactDialogBody(
                description = description,
                isLoadingImpact = isLoadingImpact,
                impactLines = impactLines,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.m3Clickable(confirmInteractionSource),
                enabled = !isLoadingImpact,
                interactionSource = confirmInteractionSource,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(
                    text = confirmButtonText,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.m3Clickable(dismissInteractionSource),
                interactionSource = dismissInteractionSource,
            ) {
                Text(
                    text = cancelButtonText,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        shape = InfraMapShapeExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    )
}

@Composable
private fun ImpactDialogBody(
    description: String?,
    isLoadingImpact: Boolean,
    impactLines: List<String>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (isLoadingImpact) {
            ImpactLoadingIndicator()
        } else if (impactLines.isNotEmpty()) {
            ImpactLinesList(impactLines = impactLines)
        }
    }
}

@Composable
private fun ImpactLoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(Res.string.impact_dialog_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImpactLinesList(impactLines: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        impactLines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "• ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
