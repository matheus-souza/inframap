package com.inframap.frontend.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.motion.m3Clickable
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.row_overflow_menu_content_description
import com.inframap.frontend.generated.resources.row_overflow_menu_delete
import com.inframap.frontend.generated.resources.row_overflow_menu_edit
import org.jetbrains.compose.resources.stringResource

/**
 * Row overflow action menu ('⋮') for data tables and list items.
 *
 * Provides quick access to Edit and Delete actions while isolating clicks
 * from parent clickable table rows. Wraps menu contents in DisableSelection
 * to prevent hierarchy coordinate registration issues inside SelectionContainer.
 */
@Composable
fun RowOverflowMenu(
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    if (onEdit == null && onDelete == null) return

    var expanded by remember { mutableStateOf(false) }
    val buttonInteractionSource = remember { MutableInteractionSource() }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.m3Clickable(buttonInteractionSource),
            interactionSource = buttonInteractionSource,
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.row_overflow_menu_content_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DisableSelection {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                if (onEdit != null) {
                    EditMenuItem(onClick = {
                        expanded = false
                        onEdit()
                    })
                }
                if (onDelete != null) {
                    DeleteMenuItem(onClick = {
                        expanded = false
                        onDelete()
                    })
                }
            }
        }
    }
}

@Composable
private fun EditMenuItem(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(Res.string.row_overflow_menu_edit),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onClick,
        modifier = Modifier.m3Clickable(interactionSource),
        interactionSource = interactionSource,
    )
}

@Composable
private fun DeleteMenuItem(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(Res.string.row_overflow_menu_delete),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onClick,
        modifier = Modifier.m3Clickable(interactionSource),
        interactionSource = interactionSource,
    )
}
