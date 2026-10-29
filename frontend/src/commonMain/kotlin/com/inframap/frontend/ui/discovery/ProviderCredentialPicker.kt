package com.inframap.frontend.ui.discovery

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.ChipOption
import com.inframap.frontend.designsystem.InfraMapChoiceChipGroup
import com.inframap.frontend.domain.model.CredentialSummary
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.provider_credential_label
import com.inframap.frontend.generated.resources.provider_credential_none
import org.jetbrains.compose.resources.stringResource

/**
 * Lets the operator point a provider at a stored credential instead of typing its secrets,
 * on the create and the edit screens. Hidden when no credential is stored.
 */
@Composable
internal fun ProviderCredentialPicker(
    providerId: String,
    credentials: List<CredentialSummary>,
    selectedCredential: String,
    onSelected: (String) -> Unit,
) {
    if (credentials.isEmpty()) return

    val noneLabel = stringResource(Res.string.provider_credential_none)
    InfraMapChoiceChipGroup(
        options =
            listOf(ChipOption(value = "", label = noneLabel)) +
                credentials.map { ChipOption(value = it.id, label = it.name) },
        selected = selectedCredential,
        onSelected = onSelected,
        label = stringResource(Res.string.provider_credential_label),
        modifier = Modifier.fillMaxWidth().testTag("provider_credential_$providerId"),
    )
    Spacer(modifier = Modifier.height(8.dp))
}
