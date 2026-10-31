package com.inframap.frontend.ui.dns

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.domain.model.DnsRecord
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.dns_records_empty
import com.inframap.frontend.generated.resources.dns_records_loading
import com.inframap.frontend.generated.resources.dns_records_subtitle
import com.inframap.frontend.generated.resources.dns_records_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun DnsRecordsScreen(
    state: DnsRecordsUiState,
    actions: DnsRecordsActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = stringResource(Res.string.dns_records_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.dns_records_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(24.dp))

            when {
                state.errorMessage != null -> {
                    Text(
                        text = state.errorMessage?.asString() ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                state.isLoading -> {
                    Text(
                        text = stringResource(Res.string.dns_records_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
                else -> {
                    val records = state.records
                    if (records.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.dns_records_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    } else {
                        DnsRecordsList(
                            records = records,
                            onRecordClicked = actions.onRecordClicked,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DnsRecordsList(
    records: List<DnsRecord>,
    onRecordClicked: (DnsRecord) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = records,
            key = { it.id },
        ) { record ->
            DnsRecordRow(
                record = record,
                onClick = { onRecordClicked(record) },
            )
        }
    }
}

@Composable
private fun DnsRecordRow(
    record: DnsRecord,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable(onClick = onClick),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${record.recordType} - ${record.address ?: record.target ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
