package com.inframap.frontend.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Suppress("LongParameterList")
@Composable
fun InfraMapEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    primaryActionText: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    extraActions: (@Composable () -> Unit)? = null,
) {
    InfraMapCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            if (primaryActionText != null || secondaryActionText != null || extraActions != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (primaryActionText != null && onPrimaryAction != null) {
                        InfraMapButton(
                            text = primaryActionText,
                            onClick = onPrimaryAction,
                        )
                    }
                    if (secondaryActionText != null && onSecondaryAction != null) {
                        if (primaryActionText != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        InfraMapOutlinedButton(
                            text = secondaryActionText,
                            onClick = onSecondaryAction,
                        )
                    }
                    extraActions?.let {
                        if (primaryActionText != null || secondaryActionText != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        it()
                    }
                }
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
fun InfraMapEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    ctaLabel: String? = null,
    onCtaClick: (() -> Unit)? = null,
    extraActions: (@Composable () -> Unit)? = null,
) {
    InfraMapEmptyState(
        icon = icon,
        title = title,
        description = subtitle,
        modifier = modifier,
        primaryActionText = ctaLabel,
        onPrimaryAction = onCtaClick,
        extraActions = extraActions,
    )
}
