package com.example.agent.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.agent.core.ai.local.ModelStatus
import com.example.agent.core.permissions.PermissionStatus
import com.example.agent.core.permissions.PermissionUtils
import com.example.agent.domain.model.AppearanceMode
import com.example.agent.ui.components.Chevron
import com.example.agent.ui.components.ConfirmDialog
import com.example.agent.ui.components.IconBadge
import com.example.agent.ui.components.ListRow
import com.example.agent.ui.components.RingAvatar
import com.example.agent.ui.components.RowDivider
import com.example.agent.ui.components.SectionLabel
import com.example.agent.ui.components.SegmentedControl
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
    val spidey: ModelStatus,
    val whistle: ModelStatus,
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
    val spidey by viewModel.spideyStatus.collectAsStateWithLifecycle()
    val whistle by viewModel.whistleStatus.collectAsStateWithLifecycle()

    // Overlay permission, accessibility and notification access are all granted on *system*
    // screens, so the state has to be re-read on the way back or a row would still say
    // "Off" after the user had already enabled it. A one-shot LaunchedEffect(Unit) never
    // re-runs when the user comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshOverlayPermission()
        viewModel.refreshPermissions()
    }

    // Granting notifications is a real dialog, so the row is re-read when it closes.
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
            spidey = spidey,
            whistle = whistle,
        ),
        versionName = BuildConfig.VERSION_NAME,
        onEnableAccessibility = {
            context.startActivity(PermissionUtils.accessibilitySettingsIntent())
        },
        onEnableNotifications = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onOpenNotificationAccess = {
            PermissionUtils.openNotificationAccess(context)
        },
        onSelectAppearance = viewModel::setAppearance,
        onTouchToStopChange = viewModel::setTouchToStop,
        onGrantOverlay = viewModel::openOverlaySettings,
        onMemoryChange = viewModel::setMemory,
        onClearHistory = viewModel::clearHistory,
        onOpenMemory = onOpenMemory,
        onOpenTerms = onOpenTerms,
        onOpenOnboarding = onOpenOnboarding,
        onDownloadSpidey = viewModel::downloadSpidey,
        onDownloadWhistle = viewModel::downloadWhistle,
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
    onDownloadSpidey: () -> Unit,
    onDownloadWhistle: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val colors = MaterialTheme.spideyColors

    Column(Modifier.fillMaxSize()) {
        TabTitle(title = stringResource(R.string.settings_title))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = SpideySpacing.xl),
        ) {
            ProfileHeader(versionName = versionName)

            OnDeviceModelSection(
                spideyStatus = state.spidey,
                whistleStatus = state.whistle,
                onDownloadSpidey = onDownloadSpidey,
                onDownloadWhistle = onDownloadWhistle,
            )

            SectionLabel(text = "Permissions")
            PermissionRow(
                title = "Accessibility",
                subtitle = if (state.accessibilityStuck) {
                    "On but not connected. Turn it off and on again."
                } else {
                    "See and control the screen"
                },
                on = state.permissions.accessibilityReady,
                warning = state.accessibilityStuck,
                icon = { IconBadge(icon = Icons.Outlined.Accessibility) },
                onClick = onEnableAccessibility,
            )
            RowDivider(inset = true)
            PermissionRow(
                title = "Notifications",
                subtitle = "Show progress while a task runs",
                on = state.permissions.notificationsGranted,
                icon = { IconBadge(icon = Icons.Outlined.Notifications) },
                onClick = onEnableNotifications,
            )
            RowDivider(inset = true)
            PermissionRow(
                title = "Notification access",
                subtitle = "Optional. Read notifications when a task asks",
                on = state.permissions.notificationListenerEnabled,
                icon = { IconBadge(icon = Icons.Outlined.Visibility) },
                onClick = onOpenNotificationAccess,
            )
            RowDivider(inset = true)
            PermissionRow(
                title = "Show over other apps",
                subtitle = "A small status pill while the agent works elsewhere",
                on = state.overlayAllowed,
                icon = { IconBadge(icon = Icons.Outlined.Layers) },
                onClick = onGrantOverlay,
            )

            SectionLabel(text = stringResource(R.string.settings_appearance))
            SegmentedControl(
                options = AppearanceMode.entries.map { it.name },
                selectedIndex = state.appearance.ordinal,
                onSelect = { index -> onSelectAppearance(AppearanceMode.entries[index]) },
                modifier = Modifier.padding(horizontal = SpideySpacing.screenGutter, vertical = SpideySpacing.xs),
            )

            SectionLabel(text = "Behaviour")
            SwitchRow(
                title = "Stop when I touch the screen",
                subtitle = "Touching the screen during a task stops it.",
                checked = state.touchToStop,
                onCheckedChange = onTouchToStopChange,
                icon = Icons.Outlined.TouchApp,
            )
            RowDivider(inset = true)
            SwitchRow(
                title = "Remember preferences",
                subtitle = "Learn app shortcuts. Stored only on this device.",
                checked = state.memoryEnabled,
                onCheckedChange = onMemoryChange,
                icon = Icons.Outlined.Memory,
            )
            RowDivider(inset = true)
            ListRow(
                title = "Manage memory",
                leading = { IconBadge(icon = Icons.Outlined.Folder) },
                trailing = { Chevron() },
                onClick = onOpenMemory,
            )

            SectionLabel(text = stringResource(R.string.settings_privacy))
            ListRow(
                title = "Clear task history",
                subtitle = "Screens are summarised before the model sees them. Raw screen data is never logged.",
                titleColor = colors.error,
                leading = { IconBadge(icon = Icons.Outlined.Delete, tint = colors.error) },
                onClick = { confirmClear = true },
            )

            SectionLabel(text = stringResource(R.string.settings_about))
            ListRow(
                title = "Terms of Service",
                leading = { IconBadge(icon = Icons.Outlined.Description) },
                trailing = { Chevron() },
                onClick = onOpenTerms,
            )
            RowDivider(inset = true)
            ListRow(
                title = "How permissions are used",
                leading = { IconBadge(icon = Icons.Outlined.Info) },
                trailing = { Chevron() },
                onClick = onOpenOnboarding,
            )
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear task history?",
            message = "Every task in your history will be removed. Saved routines stay.",
            confirmLabel = "Clear",
            onConfirm = {
                confirmClear = false
                onClearHistory()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun ProfileHeader(versionName: String) {
    Row(
        modifier = Modifier.padding(
            horizontal = SpideySpacing.screenGutter,
            vertical = SpideySpacing.md,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RingAvatar(text = "S", size = 64.dp)
        Column(modifier = Modifier.padding(start = SpideySpacing.md)) {
            Text(
                text = "Spidey",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Version $versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.textSecondary,
            )
        }
    }
}

/** A permission as a row: what it does, whether it is on, and a chevron to change it. */
@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    on: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    warning: Boolean = false,
) {
    val colors = MaterialTheme.spideyColors
    val statusColor: Color = if (on) colors.success else colors.textSecondary
    ListRow(
        title = title,
        subtitle = subtitle,
        subtitleColor = if (warning) colors.error else colors.textSecondary,
        leading = icon,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (on) "On" else "Off",
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                )
                Chevron()
            }
        },
    )
}

@Preview(name = "Settings", showBackground = true, widthDp = 360, heightDp = 1500)
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
                spidey = ModelStatus.Ready,
                whistle = ModelStatus.NotDownloaded,
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
            onDownloadSpidey = {},
            onDownloadWhistle = {},
        )
    }
}
