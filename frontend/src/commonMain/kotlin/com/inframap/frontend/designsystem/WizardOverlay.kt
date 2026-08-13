package com.inframap.frontend.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Suppress("LongParameterList")
@Composable
fun InfraMapWizardOverlay(
    currentStep: Int,
    totalSteps: Int,
    title: String,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    nextLabel: String = "Proximo",
    nextEnabled: Boolean = true,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                WizardHeader(
                    title = title,
                    currentStep = currentStep,
                    totalSteps = totalSteps,
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                content()

                Spacer(modifier = Modifier.height(24.dp))

                WizardFooter(
                    onDismiss = onDismiss,
                    onBack = onBack,
                    onNext = onNext,
                    nextLabel = nextLabel,
                    nextEnabled = nextEnabled && !isLoading,
                )
            }
        }
    }
}

@Composable
private fun WizardHeader(
    title: String,
    currentStep: Int,
    totalSteps: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "$currentStep/$totalSteps",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WizardFooter(
    onDismiss: () -> Unit,
    onBack: (() -> Unit)?,
    onNext: (() -> Unit)?,
    nextLabel: String,
    nextEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismiss) {
            Text(
                text = "Fazer depois",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row {
            if (onBack != null) {
                InfraMapOutlinedButton(
                    text = "Voltar",
                    onClick = onBack,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (onNext != null) {
                InfraMapButton(
                    text = nextLabel,
                    onClick = onNext,
                    enabled = nextEnabled,
                )
            }
        }
    }
}
