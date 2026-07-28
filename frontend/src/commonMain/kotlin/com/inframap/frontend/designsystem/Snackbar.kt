@file:Suppress("MatchingDeclarationName")

package com.inframap.frontend.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class SnackbarType {
    SUCCESS,
    ERROR,
}

@Composable
fun InfraMapSnackbarHost(
    hostState: SnackbarHostState,
    type: SnackbarType = SnackbarType.SUCCESS,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        when (type) {
            SnackbarType.SUCCESS -> InfraMapGreen.copy(alpha = 0.9f)
            SnackbarType.ERROR -> InfraMapRed.copy(alpha = 0.9f)
        }
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(16.dp),
    ) { data ->
        Snackbar(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.background,
            dismissAction =
                data.visuals.actionLabel?.let {
                    {
                        TextButton(onClick = { data.performAction() }) {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.background,
                            )
                        }
                    }
                },
        ) {
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
