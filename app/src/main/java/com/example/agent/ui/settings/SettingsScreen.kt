package com.example.agent.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.BuildConfig
import com.example.agent.R
import com.example.agent.core.permissions.PermissionUtils
import com.example.agent.domain.model.AppearanceMode
import com.example.agent.ui.components.GlassCard
import com.example.agent.ui.components.LabelValueRow
import com.example.agent.ui.components.PermissionCard
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

@Composable
fun SettingsScreen(
    onOpenProvider: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTerms: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val provider by viewModel.providerConfig.collectAsStateWithLifecycle()
    val memory by viewModel.memoryEnabled.collectAsStateWithLifecycle()
    val touchToStop by viewModel.touchToStop.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()

    // Accessibility and notification access are granted in *system* settings, so
    // re-read on resume. A one-shot LaunchedEffect(Unit) never re-runs when the
    // user comes back, which left "Enable" on screen after it was already on.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SpideySpacing.screenGutter)
            .padding(top = SpideySpacing.xl, bottom = SpideySpacing.xxl),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(SpideySpacing.xl))

        SectionHeader(title = stringResource(R.string.settings_ai_provider))
        Spacer(Modifier.height(SpideySpacing.md))
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpenProvider),
        ) {
            LabelValueRow(label = "Provider", value = provider.type.displayName)
            Spacer(Modifier.height(SpideySpacing.xs))
            LabelValueRow(label = "Model", value = provider.model)
            Spacer(Modifier.height(SpideySpacing.xs))
            LabelValueRow(label = "API key", value = if (provider.hasApiKey) "Saved" else "Not set")
        }

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        SectionHeader(title = "Agent permissions")
        Spacer(Modifier.height(SpideySpacing.md))
        PermissionCard(
            title = "Accessibility",
            body = "Required so the agent can see and control the screen. You can stop it any time.",
            granted = permissions.accessibilityReady,
            warning = if (permissions.accessibilityStuck) {
                "Accessibility is switched on but the service is not connected. Turn it off " +
                    "and on again in system settings."
            } else {
                null
            },
            actionLabel = stringResource(R.string.onboarding_enable),
            onAction = { context.startActivity(PermissionUtils.accessibilitySettingsIntent()) },
        )
        Spacer(Modifier.height(SpideySpacing.md))
        PermissionCard(
            title = "Notifications",
            body = "Used to show the agent's progress while it works and to read notifications if you allow it.",
            granted = permissions.notificationListenerEnabled,
            actionLabel = "Open notification settings",
            onAction = { context.startActivity(PermissionUtils.notificationListenerSettingsIntent()) },
        )

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        SectionHeader(title = stringResource(R.string.settings_appearance))
        Spacer(Modifier.height(SpideySpacing.md))
        AppearanceSelector(selected = appearance, onSelect = viewModel::setAppearance)

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        SectionHeader(title = "Safety")
        Spacer(Modifier.height(SpideySpacing.md))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Stop when I touch the screen",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Touching the screen while a task is running stops it. Turn this " +
                            "off if the agent stops when you did not mean it to.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.spideyColors.textSecondary,
                    )
                }
                Switch(checked = touchToStop, onCheckedChange = viewModel::setTouchToStop)
            }
        }

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        SectionHeader(title = "Memory")
        Spacer(Modifier.height(SpideySpacing.md))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Remember preferences",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Lets Spidey learn app shortcuts it can reuse. Stored only on this device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.spideyColors.textSecondary,
                    )
                }
                Switch(checked = memory, onCheckedChange = viewModel::setMemory)
            }
            Spacer(Modifier.height(SpideySpacing.md))
            SecondaryButton(
                text = "Manage memory",
                onClick = onOpenMemory,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        SectionHeader(title = stringResource(R.string.settings_privacy))
        Spacer(Modifier.height(SpideySpacing.md))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Your API key is encrypted on this device and is sent only to the provider you chose. " +
                    "Screens are summarised into a small list of elements before being shared; raw screen data " +
                    "and credentials are never logged.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.textSecondary,
            )
            Spacer(Modifier.height(SpideySpacing.md))
            SecondaryButton(text = "Clear task history", onClick = viewModel::clearHistory, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        SectionHeader(title = "Legal")
        Spacer(Modifier.height(SpideySpacing.md))
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpenTerms),
        ) {
            Column {
                Text(
                    text = "Terms of Service",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Liability, acceptable use and your responsibilities.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.spideyColors.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        SectionHeader(title = stringResource(R.string.settings_about))
        Spacer(Modifier.height(SpideySpacing.md))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Spidey ${BuildConfig.VERSION_NAME} — an on-device agent that acts through Android's official APIs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.textSecondary,
            )
            Spacer(Modifier.height(SpideySpacing.md))
            SecondaryButton(text = "How permissions are used", onClick = onOpenOnboarding, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AppearanceSelector(selected: AppearanceMode, onSelect: (AppearanceMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpideySpacing.sm),
    ) {
        AppearanceMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val modifier = Modifier.weight(1f)
            if (isSelected) {
                com.example.agent.ui.components.PrimaryButton(
                    text = mode.name, onClick = { onSelect(mode) }, modifier = modifier,
                )
            } else {
                SecondaryButton(text = mode.name, onClick = { onSelect(mode) }, modifier = modifier)
            }
        }
    }
}
