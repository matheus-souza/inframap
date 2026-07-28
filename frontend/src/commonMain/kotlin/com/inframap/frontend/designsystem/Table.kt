@file:Suppress("MatchingDeclarationName")

package com.inframap.frontend.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class TableColumn(
    val header: String,
    val weight: Float = 1f,
) {
    init {
        require(weight > 0f) { "weight must be positive" }
    }
}

@Composable
fun <T> InfraMapTable(
    columns: List<TableColumn>,
    items: List<T>,
    onRowClick: ((T) -> Unit)? = null,
    modifier: Modifier = Modifier,
    cellContent: @Composable (columnIndex: Int, item: T) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                columns.forEach { column ->
                    Text(
                        text = column.header,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.weight(column.weight),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        }
        itemsIndexed(items) { index, item ->
            val rowModifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (onRowClick != null) {
                            Modifier.clickable { onRowClick(item) }
                        } else {
                            Modifier
                        },
                    ).padding(horizontal = 16.dp, vertical = 12.dp)
            Row(
                modifier = rowModifier,
            ) {
                columns.forEachIndexed { colIndex, column ->
                    Box(modifier = Modifier.weight(column.weight)) {
                        cellContent(colIndex, item)
                    }
                }
            }
            if (index < items.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }
        }
    }
}

@Composable
fun InfraMapTablePagination(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeTotalPages = maxOf(totalPages, 1)
    val safeCurrentPage = currentPage.coerceIn(1, safeTotalPages)
    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfraMapOutlinedButton(
            text = "Previous",
            onClick = { onPageChange(safeCurrentPage - 1) },
            enabled = safeCurrentPage > 1,
        )
        Text(
            text = "$safeCurrentPage / $safeTotalPages",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = TextAlign.Center,
        )
        InfraMapOutlinedButton(
            text = "Next",
            onClick = { onPageChange(safeCurrentPage + 1) },
            enabled = safeCurrentPage < safeTotalPages,
        )
    }
}
