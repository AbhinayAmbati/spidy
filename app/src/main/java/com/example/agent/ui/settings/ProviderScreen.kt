package com.example.agent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.R
import com.example.agent.domain.model.ProviderType
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
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val keyInput by viewModel.keyInput.collectAsStateWithLifecycle()
    val maskedKey by viewModel.maskedKey.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()

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

        Spacer(Modifier.height(SpideySpacing.xl))

        Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.sm)) {
            ProviderType.entries.forEach { type ->
                ProviderCard(
                    name = type.displayName,
                    subtitle = type.defaultBaseUrl.ifBlank { "Custom endpoint" },
                    selected = type == selected,
                    onClick = { viewModel.select(type) },
                )
            }
        }

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        SectionHeader(title = "Model")
        Spacer(Modifier.height(SpideySpacing.sm))
        OutlinedTextField(
            value = model,
            onValueChange = viewModel::setModel,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(selected.defaultModel) },
            shape = MaterialTheme.shapes.medium,
        )

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
