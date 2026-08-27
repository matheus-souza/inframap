package com.inframap.frontend.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val DefaultCardPadding = 16.dp
private val CardElevationValue = 1.dp
private val ElevatedCardElevationValue = 2.dp
private val OutlinedCardBorderWidth = 1.dp

@Composable
fun InfraMapCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    val elevation = CardDefaults.cardElevation(defaultElevation = CardElevationValue)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            colors = colors,
            elevation = elevation,
        ) {
            Box(modifier = Modifier.padding(DefaultCardPadding)) {
                content()
            }
        }
    } else {
        Card(
            modifier = modifier,
            colors = colors,
            elevation = elevation,
        ) {
            Box(modifier = Modifier.padding(DefaultCardPadding)) {
                content()
            }
        }
    }
}

@Composable
fun InfraMapElevatedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors =
        CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    val elevation = CardDefaults.elevatedCardElevation(defaultElevation = ElevatedCardElevationValue)
    if (onClick != null) {
        ElevatedCard(
            onClick = onClick,
            modifier = modifier,
            colors = colors,
            elevation = elevation,
        ) {
            Box(modifier = Modifier.padding(DefaultCardPadding)) {
                content()
            }
        }
    } else {
        ElevatedCard(
            modifier = modifier,
            colors = colors,
            elevation = elevation,
        ) {
            Box(modifier = Modifier.padding(DefaultCardPadding)) {
                content()
            }
        }
    }
}

@Composable
fun InfraMapOutlinedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors =
        CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    val border =
        BorderStroke(
            width = OutlinedCardBorderWidth,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    if (onClick != null) {
        OutlinedCard(
            onClick = onClick,
            modifier = modifier,
            colors = colors,
            border = border,
        ) {
            Box(modifier = Modifier.padding(DefaultCardPadding)) {
                content()
            }
        }
    } else {
        OutlinedCard(
            modifier = modifier,
            colors = colors,
            border = border,
        ) {
            Box(modifier = Modifier.padding(DefaultCardPadding)) {
                content()
            }
        }
    }
}
