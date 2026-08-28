package com.inframap.frontend.designsystem

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.common_cancel
import com.inframap.frontend.generated.resources.common_confirm
import org.jetbrains.compose.resources.stringResource

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
    confirmText: String = stringResource(Res.string.common_confirm),
    dismissText: String = stringResource(Res.string.common_cancel),
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
