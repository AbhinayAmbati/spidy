package com.example.agent.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.R
import com.example.agent.domain.model.ProviderType
import com.example.agent.ui.components.GlassCard
import com.example.agent.ui.components.PrimaryButton
import com.example.agent.ui.components.ProviderCard
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

@Composable
fun ProviderScreen(
    onBack: () -> Unit,
    viewModel: ProviderViewModel = hiltViewModel(),
) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val customModel by viewModel.customModel.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val keyInput by viewModel.keyInput.collectAsStateWithLifecycle()
    val maskedKey by viewModel.maskedKey.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()
    val storageError = viewModel.credentialStorageError

    // This screen holds an API key, so it is excluded from screenshots and from the recents
    // thumbnail. Without this the key - or the masked key and the endpoint it belongs to - is
    // captured by any screenshot, by the task switcher, and by a screen recorder.
    KeepOffScreenshots()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SpideySpacing.screenGutter)
            .padding(top = SpideySpacing.xl, bottom = SpideySpacing.xxl),
    ) {
        Text(
            text = stringResource(R.string.provider_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(SpideySpacing.lg))

        Text(
            text = stringResource(R.string.provider_key_local_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.spideyColors.textSecondary,
        )

        // If the Android Keystore is unusable on this device, every key save will fail.
        // Say so up front rather than letting the user wonder why nothing sticks.
        storageError?.let { reason ->
            Spacer(Modifier.height(SpideySpacing.md))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Encrypted storage unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.spideyColors.error,
                )
                Spacer(Modifier.height(SpideySpacing.xs))
                Text(
                    text = "API keys cannot be saved on this device: $reason",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.spideyColors.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(SpideySpacing.xl))

        Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.sm)) {
            ProviderType.entries.forEach { type ->
                ProviderCard(
                    name = type.displayName,
                    iconRes = type.iconRes(),
                    subtitle = type.defaultBaseUrl.ifBlank { "Custom endpoint" },
                    selected = type == selected,
                    onClick = { viewModel.select(type) },
                )
            }
        }

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        SectionHeader(
            title = "Model",
            subtitle = "Pick a suggestion, or choose Custom to type any model id.",
        )
        Spacer(Modifier.height(SpideySpacing.sm))

        ModelPicker(
            selected = selected,
            model = model,
            customModel = customModel,
            onPick = viewModel::selectModel,
            onPickCustom = viewModel::chooseCustomModel,
        )

        if (customModel) {
            Spacer(Modifier.height(SpideySpacing.md))
            OutlinedTextField(
                value = model,
                onValueChange = viewModel::setModel,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. gpt-4o-2024-11-20") },
                shape = MaterialTheme.shapes.medium,
            )
        }

        if (selected == ProviderType.CUSTOM) {
            Spacer(Modifier.height(SpideySpacing.md))
            SectionHeader(title = "Base URL")
            Spacer(Modifier.height(SpideySpacing.sm))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = viewModel::setBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://your-endpoint/v1") },
                shape = MaterialTheme.shapes.medium,
            )
        }

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        SectionHeader(title = "API key")
        Spacer(Modifier.height(SpideySpacing.sm))
        OutlinedTextField(
            value = keyInput,
            onValueChange = viewModel::setKeyInput,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            placeholder = { Text(maskedKey ?: "Paste your key") },
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(SpideySpacing.xs))
        Text(
            text = if (maskedKey != null) "A key is saved. Paste a new one to replace it." else "No key saved yet.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.spideyColors.textSecondary,
        )

        Spacer(Modifier.height(SpideySpacing.lg))

        Row(horizontalArrangement = Arrangement.spacedBy(SpideySpacing.md), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(
                text = stringResource(R.string.provider_test),
                onClick = viewModel::testConnection,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.provider_save),
                onClick = viewModel::save,
                modifier = Modifier.weight(1f),
            )
        }

        if (maskedKey != null) {
            Spacer(Modifier.height(SpideySpacing.md))
            SecondaryButton(
                text = "Delete saved key",
                onClick = viewModel::deleteKey,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(SpideySpacing.lg))

        val (statusText, statusColor) = when (val s = testState) {
            ProviderViewModel.TestState.Idle -> null to MaterialTheme.spideyColors.textSecondary
            ProviderViewModel.TestState.Testing -> "Testing…" to MaterialTheme.spideyColors.textSecondary
            ProviderViewModel.TestState.Saved -> "Saved." to MaterialTheme.spideyColors.success
            is ProviderViewModel.TestState.Success -> s.message to MaterialTheme.spideyColors.success
            is ProviderViewModel.TestState.Failure -> s.message to MaterialTheme.spideyColors.error
        }
        statusText?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = statusColor)
        }
    }
}

/**
 * A read-only select field with a dropdown. Uses the stable DropdownMenu rather than
 * the experimental exposed-dropdown container.
 */
@Composable
private fun ModelPicker(
    selected: ProviderType,
    model: String,
    customModel: Boolean,
    onPick: (String) -> Unit,
    onPickCustom: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val suggestions = selected.suggestedModels

    Box {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = true },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = SpideySpacing.md,
                vertical = SpideySpacing.sm,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        customModel -> "Custom model"
                        model.isBlank() -> "Choose a model"
                        else -> model
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Choose a model",
                    tint = MaterialTheme.spideyColors.textSecondary,
                )
            }
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion, style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        onPick(suggestion)
                        open = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Custom model…", style = MaterialTheme.typography.bodyMedium) },
                onClick = {
                    onPickCustom()
                    open = false
                },
            )
        }
    }
}

/**
 * Marks the current window as not safe for screenshots.
 *
 * Set while the key screen is composed and cleared when it leaves, so the rest of the app is
 * unaffected. Walking the context wrappers is necessary because a Compose `LocalContext` is
 * usually a `ContextThemeWrapper` rather than the Activity itself.
 */
@Composable
private fun KeepOffScreenshots() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
