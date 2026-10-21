package com.example.agent.ui.onboarding

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.agent.R
import com.example.agent.core.permissions.PermissionUtils
import com.example.agent.ui.components.IconBadge
import com.example.agent.ui.components.ListRow
import com.example.agent.ui.components.PrimaryButton
import com.example.agent.ui.components.RowDivider
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.SectionLabel
import com.example.agent.ui.components.SpideyMark
import com.example.agent.ui.components.StatusPill
import com.example.agent.ui.components.StickyActionBar
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.SpideyTheme
import com.example.agent.ui.theme.spideyColors

/**
 * Honest permission onboarding: it explains exactly what accessibility is used for,
 * what it cannot do, and leaves the final switch to the user in system settings.
 *
 * Both grants happen *outside* the app, so the state is re-read on resume and the
 * enable action disappears once the permission is already on.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current

    var status by remember { mutableStateOf(PermissionUtils.status(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        status = PermissionUtils.status(context)
    }

    OnboardingContent(
        accessibilityReady = status.accessibilityReady,
        notificationAccessEnabled = status.notificationListenerEnabled,
        onEnableAccessibility = {
            context.startActivity(PermissionUtils.accessibilitySettingsIntent())
        },
        onEnableNotificationAccess = {
            context.startActivity(PermissionUtils.notificationListenerSettingsIntent())
        },
        onDone = onDone,
    )
}

@Composable
private fun OnboardingContent(
    accessibilityReady: Boolean,
    notificationAccessEnabled: Boolean,
    onEnableAccessibility: () -> Unit,
    onEnableNotificationAccess: () -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = SpideySpacing.lg),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = SpideySpacing.screenGutter,
                    end = SpideySpacing.screenGutter,
                    top = SpideySpacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(SpideySpacing.sm),
            ) {
                SpideyMark(color = MaterialTheme.colorScheme.primary, size = 48.dp)
                Spacer(Modifier.height(SpideySpacing.xs))
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.onboarding_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.spideyColors.textSecondary,
                )
            }

            SectionLabel(text = "What it can do")
            Text(
                text = "Open apps, read on-screen elements, tap, type, scroll, go back, and follow links, " +
                    "only through the tools you can see in this app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = SpideySpacing.screenGutter),
            )

            SectionLabel(text = "What it cannot do")
            Text(
                text = "It cannot run code or shell commands, read arbitrary files, or bypass Android's security. " +
                    "Sensitive actions such as sending, paying or deleting always wait for your approval.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = SpideySpacing.screenGutter),
            )

            SectionLabel(text = "Permissions")
            PermissionStep(
                title = "Accessibility",
                subtitle = "Required. See and control the screen. You can stop it any time.",
                enabled = accessibilityReady,
                icon = { IconBadge(icon = Icons.Outlined.Accessibility) },
                onClick = onEnableAccessibility,
            )
            RowDivider(inset = true)
            PermissionStep(
                title = "Notification access",
                subtitle = "Optional. Read notifications when a task needs them.",
                enabled = notificationAccessEnabled,
                icon = { IconBadge(icon = Icons.Outlined.Visibility) },
                onClick = onEnableNotificationAccess,
            )
        }

        StickyActionBar {
            if (accessibilityReady) {
                PrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_enable),
                    onClick = onEnableAccessibility,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(text = "Maybe later", onClick = onDone, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PermissionStep(
    title: String,
    subtitle: String,
    enabled: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.spideyColors
    ListRow(
        title = title,
        subtitle = subtitle,
        leading = icon,
        onClick = if (enabled) null else onClick,
        trailing = {
            Row {
                StatusPill(
                    label = if (enabled) "Enabled" else "Not enabled",
                    color = if (enabled) colors.success else colors.warning,
                )
            }
        },
    )
}

@Preview(name = "Onboarding", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun OnboardingPreview() {
    SpideyTheme {
        OnboardingContent(
            accessibilityReady = false,
            notificationAccessEnabled = false,
            onEnableAccessibility = {},
            onEnableNotificationAccess = {},
            onDone = {},
        )
    }
}
