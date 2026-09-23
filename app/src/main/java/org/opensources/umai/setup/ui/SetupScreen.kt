package org.opensources.umai.setup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.network.LocalNetworkAccess
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title

/**
 * First-run screen. It is also shown when a stored session is refused, with the
 * instance pre-filled, so the user never lands on an empty Home.
 */
@Composable
fun SetupScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: SetupViewModel = viewModel(factory = SetupViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val localNetworkPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        viewModel::onLocalNetworkPermissionResult,
    )
    LaunchedEffect(state.requestLocalNetworkPermission) {
        if (state.requestLocalNetworkPermission) {
            localNetworkPermission.launch(LocalNetworkAccess.PERMISSION)
        }
    }

    SetupScreen(
        state = state,
        onUrlChange = viewModel::onUrlChange,
        onAuthMethodChange = viewModel::onAuthMethodChange,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onApiTokenChange = viewModel::onApiTokenChange,
        onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
        onConnect = viewModel::connect,
        onUseAnotherInstance = viewModel::useAnotherInstance,
        modifier = modifier,
    )
}

/** Stateless form, so the validation and error paths can be driven by tests. */
@Composable
fun SetupScreen(
    state: SetupUiState,
    onUrlChange: (String) -> Unit,
    onAuthMethodChange: (SetupAuthMethod) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onApiTokenChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onConnect: () -> Unit,
    onUseAnotherInstance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(expiredForUrl = state.expiredForUrl)

                OutlinedTextField(
                    value = state.url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.setup_url_label)) },
                    placeholder = { Text(stringResource(R.string.setup_url_placeholder)) },
                    supportingText = { Text(stringResource(R.string.setup_url_helper)) },
                    singleLine = true,
                    enabled = !state.connecting,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )

                if (state.cleartextWarning) CleartextWarning()

                Text(
                    text = stringResource(R.string.setup_auth_method),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Both labels stay on a single line: a label that wraps makes
                // its segment taller than the other one and the row lopsided.
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SetupAuthMethod.entries.forEachIndexed { index, method ->
                        SegmentedButton(
                            selected = state.authMethod == method,
                            onClick = { onAuthMethodChange(method) },
                            enabled = !state.connecting,
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = SetupAuthMethod.entries.size,
                            ),
                            label = {
                                Text(
                                    text = when (method) {
                                        SetupAuthMethod.PASSWORD ->
                                            stringResource(R.string.setup_auth_password)
                                        SetupAuthMethod.API_TOKEN ->
                                            stringResource(R.string.setup_auth_token)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }

                when (state.authMethod) {
                    SetupAuthMethod.PASSWORD -> PasswordFields(
                        state = state,
                        onUsernameChange = onUsernameChange,
                        onPasswordChange = onPasswordChange,
                        onTogglePasswordVisibility = onTogglePasswordVisibility,
                    )
                    SetupAuthMethod.API_TOKEN -> TokenField(state, onApiTokenChange)
                }

                state.formError?.let { ErrorBlock(stringResource(it)) }
                state.networkError?.let { error ->
                    ErrorBlock("${error.title()}\n${error.message()}")
                }

                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canSubmit,
                ) {
                    if (state.connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.setup_connecting))
                    } else {
                        Text(stringResource(R.string.action_connect))
                    }
                }

                if (state.expiredForUrl != null) {
                    TextButton(
                        onClick = onUseAnotherInstance,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(stringResource(R.string.setup_use_other_instance))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(expiredForUrl: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_monochrome),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(
                if (expiredForUrl != null) R.string.setup_expired_title else R.string.setup_title,
            ),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (expiredForUrl != null) {
                stringResource(R.string.setup_expired_message, expiredForUrl)
            } else {
                stringResource(R.string.setup_intro)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PasswordFields(
    state: SetupUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
) {
    OutlinedTextField(
        value = state.username,
        onValueChange = onUsernameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.setup_username_label)) },
        singleLine = true,
        enabled = !state.connecting,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        ),
    )
    OutlinedTextField(
        value = state.password,
        onValueChange = onPasswordChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.setup_password_label)) },
        singleLine = true,
        enabled = !state.connecting,
        visualTransformation = if (state.passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        trailingIcon = {
            IconButton(onClick = onTogglePasswordVisibility) {
                Icon(
                    imageVector = if (state.passwordVisible) {
                        Icons.Outlined.VisibilityOff
                    } else {
                        Icons.Outlined.Visibility
                    },
                    contentDescription = stringResource(
                        if (state.passwordVisible) {
                            R.string.setup_hide_password
                        } else {
                            R.string.setup_show_password
                        },
                    ),
                )
            }
        },
    )
}

@Composable
private fun TokenField(state: SetupUiState, onApiTokenChange: (String) -> Unit) {
    OutlinedTextField(
        value = state.apiToken,
        onValueChange = onApiTokenChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.setup_token_label)) },
        supportingText = { Text(stringResource(R.string.setup_token_helper)) },
        enabled = !state.connecting,
        minLines = 2,
        maxLines = 4,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
    )
}

@Composable
private fun CleartextWarning() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(R.string.setup_cleartext_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ErrorBlock(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
