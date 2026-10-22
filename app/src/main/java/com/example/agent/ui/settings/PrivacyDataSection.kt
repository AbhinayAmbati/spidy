package com.example.agent.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Timer
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
import androidx.compose.ui.res.stringResource
import com.example.agent.R
import com.example.agent.core.audit.AuditPolicy
import com.example.agent.core.audit.RequestTextMode
import com.example.agent.ui.components.Chevron
import com.example.agent.ui.components.ConfirmDialog
import com.example.agent.ui.components.IconBadge
import com.example.agent.ui.components.ListRow
import com.example.agent.ui.components.RowDivider
import com.example.agent.ui.components.SectionLabel
import com.example.agent.ui.components.SegmentedControl
import com.example.agent.ui.components.StatusPill
import com.example.agent.ui.components.SwitchRow
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

private const val EXPORT_FILE_NAME = "spidey-data-export.json"

@Composable
private fun retentionLabel(days: Int): String = when (days) {
    7 -> stringResource(R.string.privacy_retention_7)
    30 -> stringResource(R.string.privacy_retention_30)
    90 -> stringResource(R.string.privacy_retention_90)
    0 -> stringResource(R.string.privacy_retention_off)
    else -> "$days"
}

/**
 * Why each permission is asked for, in plain words, before the system dialog. The rows show
 * whether the permission is on and request it when tapped.
 */
@Composable
fun PermissionRationaleRows(state: PrivacyUiState, actions: PrivacyActions) {
    val colors = MaterialTheme.spideyColors
    val voiceOff = state.managed.disableVoice
    RationaleRow(
        title = stringResource(R.string.permission_contacts_title),
        rationale = stringResource(R.string.permission_contacts_rationale),
        on = state.contactsGranted,
        icon = { IconBadge(icon = Icons.Outlined.Contacts) },
        onClick = actions.onRequestContacts,
    )
    RowDivider(inset = true)
    RationaleRow(
        title = stringResource(R.string.permission_microphone_title),
        rationale = if (voiceOff) {
            stringResource(R.string.permission_voice_managed)
        } else {
            stringResource(R.string.permission_microphone_rationale)
        },
        on = state.microphoneGranted && !voiceOff,
        icon = { IconBadge(icon = Icons.Outlined.Mic) },
        onClick = if (voiceOff) null else actions.onRequestMicrophone,
        rationaleColor = if (voiceOff) colors.warning else colors.textSecondary,
    )
}

@Composable
private fun RationaleRow(
    title: String,
    rationale: String,
    on: Boolean,
    icon: @Composable () -> Unit,
    onClick: (() -> Unit)?,
    rationaleColor: Color = MaterialTheme.spideyColors.textSecondary,
) {
    val colors = MaterialTheme.spideyColors
    ListRow(
        title = title,
        subtitle = rationale,
        subtitleColor = rationaleColor,
        leading = icon,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(if (on) R.string.permission_on else R.string.permission_off),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) colors.success else colors.textSecondary,
                )
                if (onClick != null) Chevron()
            }
        },
    )
}

/** The "Privacy and data" group: retention, what is logged, export, delete, managed badge. */
@Composable
fun PrivacyDataSection(state: PrivacyUiState, actions: PrivacyActions) {
    val colors = MaterialTheme.spideyColors
    var confirmDelete by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) actions.onExport(uri) }

    SectionLabel(text = stringResource(R.string.privacy_section))

    if (state.managed.isManaged) {
        ListRow(
            title = stringResource(R.string.privacy_managed_badge),
            subtitle = stringResource(R.string.privacy_managed_summary),
            leading = { IconBadge(icon = Icons.Outlined.Lock, tint = colors.blue) },
            trailing = { StatusPill(label = "MDM", color = colors.blue) },
        )
        RowDivider(inset = true)
    }

    val managedRetention = state.managed.auditRetentionDays
    if (managedRetention != null) {
        ListRow(
            title = stringResource(R.string.privacy_retention_title),
            subtitle = stringResource(
                R.string.privacy_retention_managed_value,
                retentionLabel(managedRetention),
            ),
            leading = { IconBadge(icon = Icons.Outlined.Timer) },
        )
    } else {
        ListRow(
            title = stringResource(R.string.privacy_retention_title),
            subtitle = stringResource(R.string.privacy_retention_subtitle),
            leading = { IconBadge(icon = Icons.Outlined.Timer) },
        )
        val choices = AuditPolicy.RETENTION_CHOICES
        val labels = choices.map { retentionLabel(it) }
        SegmentedControl(
            options = labels,
            selectedIndex = choices.indexOf(state.retentionDays).coerceAtLeast(0),
            onSelect = { index -> actions.onRetentionChange(choices[index]) },
            modifier = Modifier.padding(horizontal = SpideySpacing.screenGutter, vertical = SpideySpacing.xs),
        )
    }

    if (state.effectiveRetentionDays > 0) {
        RowDivider(inset = true)
        ListRow(
            title = stringResource(R.string.privacy_request_title),
            subtitle = stringResource(R.string.privacy_request_subtitle),
            leading = { IconBadge(icon = Icons.Outlined.Lock) },
        )
        val modes = RequestTextMode.entries
        SegmentedControl(
            options = listOf(
                stringResource(R.string.privacy_request_hash),
                stringResource(R.string.privacy_request_truncated),
                stringResource(R.string.privacy_request_none),
            ),
            selectedIndex = modes.indexOf(state.requestTextMode).coerceAtLeast(0),
            onSelect = { index -> actions.onRequestTextModeChange(modes[index]) },
            modifier = Modifier.padding(horizontal = SpideySpacing.screenGutter, vertical = SpideySpacing.xs),
        )
        RowDivider(inset = true)
        SwitchRow(
            title = stringResource(R.string.privacy_contacts_title),
            subtitle = stringResource(R.string.privacy_contacts_subtitle),
            checked = state.keepContactNames,
            onCheckedChange = actions.onKeepContactNamesChange,
            icon = Icons.Outlined.Contacts,
        )
    }

    RowDivider(inset = true)
    ListRow(
        title = stringResource(R.string.privacy_export_title),
        subtitle = when (state.status) {
            PrivacyStatus.Exported -> stringResource(R.string.privacy_export_done)
            PrivacyStatus.ExportFailed -> stringResource(R.string.privacy_export_failed)
            else -> stringResource(R.string.privacy_export_subtitle)
        },
        leading = { IconBadge(icon = Icons.Outlined.FileDownload) },
        trailing = { Chevron() },
        onClick = { exportLauncher.launch(EXPORT_FILE_NAME) },
    )
    RowDivider(inset = true)
    ListRow(
        title = stringResource(R.string.privacy_delete_title),
        subtitle = if (state.status == PrivacyStatus.Deleted) {
            stringResource(R.string.privacy_delete_done)
        } else {
            stringResource(R.string.privacy_delete_subtitle)
        },
        titleColor = colors.error,
        leading = { IconBadge(icon = Icons.Outlined.Delete, tint = colors.error) },
        onClick = { confirmDelete = true },
    )

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.privacy_delete_confirm_title),
            message = stringResource(R.string.privacy_delete_confirm_message),
            confirmLabel = stringResource(R.string.privacy_delete_confirm_action),
            onConfirm = {
                confirmDelete = false
                actions.onDeleteAll()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
