package com.inframap.frontend.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapTextField
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.app_name
import com.inframap.frontend.generated.resources.login_button
import com.inframap.frontend.generated.resources.login_password
import com.inframap.frontend.generated.resources.login_signing_in
import com.inframap.frontend.generated.resources.login_subtitle
import com.inframap.frontend.generated.resources.login_username
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoginScreen(
    state: LoginUiState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Enter && !state.isLoading) {
                        onLoginClick()
                        true
                    } else {
                        false
                    }
                },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LoginForm(state, onUsernameChanged, onPasswordChanged, onLoginClick)
        }
    }
}

@Composable
private fun LoginForm(
    state: LoginUiState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClick: () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 400.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoginHeader()
        Spacer(modifier = Modifier.height(32.dp))

        LoginFields(
            state = state,
            onUsernameChanged = onUsernameChanged,
            onPasswordChanged = onPasswordChanged,
            onLoginClick = onLoginClick,
        )

        Spacer(modifier = Modifier.height(24.dp))

        InfraMapButton(
            text =
                if (state.isLoading) {
                    stringResource(Res.string.login_signing_in)
                } else {
                    stringResource(Res.string.login_button)
                },
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
        )
    }
}

@Composable
private fun LoginHeader() {
    Text(
        text = stringResource(Res.string.app_name),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(Res.string.login_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LoginFields(
    state: LoginUiState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClick: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    InfraMapTextField(
        value = state.username,
        onValueChange = onUsernameChanged,
        label = stringResource(Res.string.login_username),
        error =
            state.errorMessage
                ?.takeIf { state.username.isBlank() }
                ?.asString(),
        enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions =
            KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            ),
    )
    Spacer(modifier = Modifier.height(16.dp))

    InfraMapTextField(
        value = state.password,
        onValueChange = onPasswordChanged,
        label = stringResource(Res.string.login_password),
        error = state.errorMessage?.asString(),
        enabled = !state.isLoading,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions =
            KeyboardActions(
                onDone = { onLoginClick() },
            ),
    )
}
