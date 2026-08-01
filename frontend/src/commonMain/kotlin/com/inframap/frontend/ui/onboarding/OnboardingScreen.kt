package com.inframap.frontend.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapTextField

@Suppress("LongParameterList")
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onUsernameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onFullNameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onSetupClick: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            OnboardingFormContent(
                state,
                onUsernameChanged,
                onEmailChanged,
                onFullNameChanged,
                onPasswordChanged,
                onConfirmPasswordChanged,
                onSetupClick,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun OnboardingFormContent(
    state: OnboardingUiState,
    onUsernameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onFullNameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onSetupClick: () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 400.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "InfraMap",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create your admin account",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))

        OnboardingFields(state, onUsernameChanged, onEmailChanged, onFullNameChanged, !state.isLoading)
        Spacer(modifier = Modifier.height(16.dp))
        OnboardingPasswordFields(state, onPasswordChanged, onConfirmPasswordChanged, !state.isLoading)

        if (state.errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.errorMessage.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        InfraMapButton(
            text = if (state.isLoading) "Setting up..." else "Complete Setup",
            onClick = onSetupClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
        )
    }
}

@Composable
private fun OnboardingFields(
    state: OnboardingUiState,
    onUsernameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onFullNameChanged: (String) -> Unit,
    enabled: Boolean,
) {
    InfraMapTextField(
        value = state.fullName,
        onValueChange = onFullNameChanged,
        label = "Full Name",
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))
    InfraMapTextField(
        value = state.email,
        onValueChange = onEmailChanged,
        label = "Email",
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))
    InfraMapTextField(
        value = state.username,
        onValueChange = onUsernameChanged,
        label = "Username",
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OnboardingPasswordFields(
    state: OnboardingUiState,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    enabled: Boolean,
) {
    InfraMapTextField(
        value = state.password,
        onValueChange = onPasswordChanged,
        label = "Password",
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Must be at least 12 characters",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))
    InfraMapTextField(
        value = state.confirmPassword,
        onValueChange = onConfirmPasswordChanged,
        label = "Confirm Password",
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}
