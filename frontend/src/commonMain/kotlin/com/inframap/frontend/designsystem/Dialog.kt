package com.inframap.frontend.designsystem

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 Confirmation Dialog for InfraMap.
 *
 * Adheres to M3 Dialog specification:
 * - Surface Container High background (`surfaceContainerHigh`).
 * - Shape Extra Large (28.dp) rounded corners.
 * - Tonal Elevation of 6.dp.
 * - High contrast titles (`onSurface`) and readable body text (`onSurfaceVariant`).
 */
@Composable
fun InfraMapConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            InfraMapButton(text = confirmText, onClick = onConfirm)
        },
        dismissButton = {
            InfraMapOutlinedButton(text = dismissText, onClick = onDismiss)
        },
        shape = InfraMapShapeExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    )
}
