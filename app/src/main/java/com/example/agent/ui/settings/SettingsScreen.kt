package com.example.agent.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.BuildConfig
import com.example.agent.R
import com.example.agent.core.permissions.PermissionStatus
import com.example.agent.core.permissions.PermissionUtils
import com.example.agent.domain.model.AppearanceMode
import com.example.agent.ui.components.ChipRow
import com.example.agent.ui.components.ClickableSpideyCard
import com.example.agent.ui.components.PermissionCard
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.components.SpideyCard
import com.example.agent.ui.components.SwitchRow
import com.example.agent.ui.components.TabTitle
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.SpideyTheme
import com.example.agent.ui.theme.spideyColors

/** Everything [SettingsContent] draws, gathered so the screen's signature stays readable. */
private data class SettingsUiState(
    val appearance: AppearanceMode,
    val memoryEnabled: Boolean,
    val touchToStop: Boolean,
    val overlayAllowed: Boolean,
    val accessibilityStuck: Boolean,
    val permissions: PermissionStatus,
)

@Composable
fun SettingsScreen(
    onOpenOnboarding: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTerms: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val memory by viewModel.memoryEnabled.collectAsStateWithLifecycle()
    val touchToStop by viewModel.touchToStop.collectAsStateWithLifecycle()
    val overlayAllowed by viewModel.overlayAllowed.collectAsStateWithLifecycle()
    val accessibilityStuck by viewModel.accessibilityStuck.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()

    // Overlay permission, accessibility and notification access are all granted on *system*
    // screens, so the state has to be re-read on the way back or a card would still say
    // "Enable" after the user had already enabled it. A one-shot LaunchedEffect(Unit) never
    // re-runs when the user comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshOverlayPermission()
        viewModel.refreshPermissions()
    }

    // Granting notifications is a real dialog, so the card is re-read when it closes.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshPermissions() }

    SettingsContent(
        state = SettingsUiState(
            appearance = appearance,
            memoryEnabled = memory,
            touchToStop = touchToStop,
            overlayAllowed = overlayAllowed,
            accessibilityStuck = accessibilityStuck,
            permissions = permissions,
        ),
        versionName = BuildConfig.VERSION_NAME,
        onEnableAccessibility = {
            context.startActivity(PermissionUtils.accessibilitySettingsIntent())
        },
        onEnableNotifications = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onOpenNotificationAccess = {
            context.startActivity(PermissionUtils.notificationListenerSettingsIntent())
        },
        onSelectAppearance = viewModel::setAppearance,
        onTouchToStopChange = viewModel::setTouchToStop,
        onGrantOverlay = viewModel::openOverlaySettings,
        onMemoryChange = viewModel::setMemory,
        onClearHistory = viewModel::clearHistory,
        onOpenMemory = onOpenMemory,
        onOpenTerms = onOpenTerms,
        onOpenOnboarding = onOpenOnboarding,
    )
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    versionName: String,
    onEnableAccessibility: () -> Unit,
    onEnableNotifications: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onSelectAppearance: (AppearanceMode) -> Unit,
    onTouchToStopChange: (Boolean) -> Unit,
    onGrantOverlay: () -> Unit,
    onMemoryChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenOnboarding: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TabTitle(title = stringResource(R.string.settings_title))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpideySpacing.screenGutter)
                .padding(bottom = SpideySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SpideySpacing.md),
        ) {
            OnDeviceModelCard(
                needleStatus = ModelStatus.NotDownloaded,
                whistleStatus = ModelStatus.NotDownloaded,
            )

            SectionHeader(title = "Agent permissions", modifier = Modifier.padding(top = SpideySpacing.xs))
            PermissionCard(
                title = "Accessibility",
                body = "Required so the agent can see and control the screen. You can stop it any time.",
                granted = state.permissions.accessibilityReady,
                warning = if (state.accessibilityStuck) {
                    "Accessibility is switched on but the service has not connected, even " +
                        "after waiting. Turn it off and on again in system settings."
                } else {
                    null
                },
                actionLabel = stringResource(R.string.onboarding_enable),
                onAction = onEnableAccessibility,
            )
            // Two genuinely different things, and conflating them is what made this card claim
            // notifications were off while they were on. "Notifications" is the POST_NOTIFICATIONS
            // runtime permission - it is what lets the agent's progress be seen at all.
            // "Notification access" is a separate system switch that lets the agent *read* other
            // apps' notifications, and is optional.
            PermissionCard(
                title = "Notifications",
                body = "Lets Spidey show its progress while it works. Without it you will not see " +
                    "the agent's status once it leaves this app.",
                granted = state.permissions.notificationsGranted,
                actionLabel = "Enable notifications",
                onAction = onEnableNotifications,
            )
            PermissionCard(
                title = "Notification access",
                body = "Optional, and separate from notifications above. Lets Spidey read your " +
                    "notifications when a task asks it to. Everything else works without it.",
                granted = state.permissions.notificationListenerEnabled,
                actionLabel = "Open notification access",
                onAction = onOpenNotificationAccess,
            )

            SectionHeader(
                title = stringResource(R.string.settings_appearance),
                modifier = Modifier.padding(top = SpideySpacing.xs),
            )
            ChipRow(
                labels = AppearanceMode.entries.map { it.name },
                selected = state.appearance.name,
                onSelect = { name -> onSelectAppearance(AppearanceMode.fromKey(name)) },
                contentPadding = PaddingValues(0.dp),
            )

            SectionHeader(title = "Safety", modifier = Modifier.padding(top = SpideySpacing.xs))
            SpideyCard(modifier = Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = "Stop when I touch the screen",
                    description = "Touching the screen while a task is running stops it. Turn this " +
                        "off if the agent stops when you did not mean it to.",
                    checked = state.touchToStop,
                    onCheckedChange = onTouchToStopChange,
                )
            }
            SpideyCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Show status over other apps",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (state.overlayAllowed) {
                                "A small pill shows what the agent is doing while you are in " +
                                    "another app."
                            } else {
                                "Needs \"Display over other apps\". Without it you cannot see the " +
                                    "agent's status once it leaves this app."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.spideyColors.textSecondary,
                        )
                    }
                    if (!state.overlayAllowed) {
                        SecondaryButton(text = "Grant", onClick = onGrantOverlay)
                    }
                }
            }

            SectionHeader(title = "Memory", modifier = Modifier.padding(top = SpideySpacing.xs))
            SpideyCard(modifier = Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = "Remember preferences",
                    description = "Lets Spidey learn app shortcuts it can reuse. Stored only on this device.",
                    checked = state.memoryEnabled,
                    onCheckedChange = onMemoryChange,
                )
                SecondaryButton(
                    text = "Manage memory",
                    onClick = onOpenMemory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SpideySpacing.md),
                )
            }

            SectionHeader(
                title = stringResource(R.string.settings_privacy),
                modifier = Modifier.padding(top = SpideySpacing.xs),
            )
            SpideyCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Screens are summarised into a small list of elements before the model " +
                        "sees them; raw screen data and credentials are never logged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.spideyColors.textSecondary,
                )
                SecondaryButton(
                    text = "Clear task history",
                    onClick = onClearHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SpideySpacing.md),
                )
            }

            SectionHeader(title = "Legal", modifier = Modifier.padding(top = SpideySpacing.xs))
            ClickableSpideyCard(onClick = onOpenTerms, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Terms of Service",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Liability, acceptable use and your responsibilities.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.spideyColors.textSecondary,
                )
            }

            SectionHeader(
                title = stringResource(R.string.settings_about),
                modifier = Modifier.padding(top = SpideySpacing.xs),
            )
            SpideyCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Spidey $versionName — an agent that acts through Android's official APIs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.spideyColors.textSecondary,
                )
                SecondaryButton(
                    text = "How permissions are used",
                    onClick = onOpenOnboarding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SpideySpacing.md),
                )
            }
        }
    }
}

@Preview(name = "Settings", showBackground = true, widthDp = 360, heightDp = 1400)
@Composable
private fun SettingsPreview() {
    SpideyTheme {
        SettingsContent(
            state = SettingsUiState(
                appearance = AppearanceMode.System,
                memoryEnabled = true,
                touchToStop = true,
                overlayAllowed = false,
                accessibilityStuck = false,
                permissions = PermissionStatus(
                    accessibilityEnabled = true,
                    accessibilityConnected = true,
                    notificationListenerEnabled = false,
                    notificationsGranted = true,
                ),
            ),
            versionName = "1.0",
            onEnableAccessibility = {},
            onEnableNotifications = {},
            onOpenNotificationAccess = {},
            onSelectAppearance = {},
            onTouchToStopChange = {},
            onGrantOverlay = {},
            onMemoryChange = {},
            onClearHistory = {},
            onOpenMemory = {},
            onOpenTerms = {},
            onOpenOnboarding = {},
        )
    }
}
