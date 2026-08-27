package com.inframap.frontend.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.motion.m3InteractiveScale

@Composable
fun InfraMapButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.m3InteractiveScale(interactionSource),
        enabled = enabled,
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun InfraMapOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.m3InteractiveScale(interactionSource),
        enabled = enabled,
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
