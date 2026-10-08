package com.example.agent.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.agent.R
import com.example.agent.core.permissions.PermissionUtils
import com.example.agent.ui.components.GlassCard
import com.example.agent.ui.components.PrimaryButton
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.StatusDot
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/**
 * Honest permission onboarding: it explains exactly what accessibility is used for,
 * what it cannot do, and leaves the final switch to the user in system settings.
 *
 * Both grants happen *outside* the app, so the state is re-read on resume and the
 * enable button disappears once the permission is already on.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current

    var status by remember { mutableStateOf(PermissionUtils.status(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        status = PermissionUtils.status(context)
    }

    val accessibilityReady = status.accessibilityReady
    val notificationsEnabled = status.notificationListenerEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SpideySpacing.screenGutter)
            .padding(top = SpideySpacing.xxl, bottom = SpideySpacing.xxl),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(SpideySpacing.lg))
        Text(
            text = stringResource(R.string.onboarding_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.spideyColors.textSecondary,
        )

        Spacer(Modifier.height(SpideySpacing.xl))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "What the agent can do",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(SpideySpacing.xs))
            Text(
                text = "Open apps, read on-screen elements, tap, type, scroll, go back, and follow links — " +
                    "only through the tools you can see in this app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.textSecondary,
            )
            Spacer(Modifier.height(SpideySpacing.md))
            Text(
                text = "What it cannot do",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(SpideySpacing.xs))
            Text(
                text = "It cannot run code or shell commands, read arbitrary files, or bypass Android's security. " +
                    "Sensitive actions such as sending, paying or deleting always wait for your approval.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.textSecondary,
            )
        }

        Spacer(Modifier.height(SpideySpacing.md))

        // ---- Accessibility (required) ----
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            PermissionHeader(
                title = "Accessibility",
                granted = accessibilityReady,
                required = true,
            )
            Spacer(Modifier.height(SpideySpacing.xs))
            Text(
                text = "Required so the agent can see and control the screen. You can stop it any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.textSecondary,
            )
            if (!accessibilityReady) {
                Spacer(Modifier.height(SpideySpacing.md))
                PrimaryButton(
                    text = stringResource(R.string.onboarding_enable),
                    onClick = { context.startActivity(PermissionUtils.accessibilitySettingsIntent()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(SpideySpacing.md))

        // ---- Notification access (optional) ----
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            PermissionHeader(
                title = "Notification access",
                granted = notificationsEnabled,
                required = false,
            )
            Spacer(Modifier.height(SpideySpacing.xs))
            Text(
                text = "Optional. Lets Spidey read your notifications when a task needs them, and show its " +
                    "progress while it works. Everything else works without it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.textSecondary,
            )
            if (!notificationsEnabled) {
                Spacer(Modifier.height(SpideySpacing.md))
                SecondaryButton(
                    text = "Enable notification access",
                    onClick = { context.startActivity(PermissionUtils.notificationListenerSettingsIntent()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(SpideySpacing.xl))

        SecondaryButton(
            text = if (accessibilityReady) "Done" else "Maybe later",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PermissionHeader(title: String, granted: Boolean, required: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (required) "Required" else "Optional",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.spideyColors.textSecondary,
            )
        }
        StatusDot(
            color = if (granted) MaterialTheme.spideyColors.success else MaterialTheme.spideyColors.warning,
            label = if (granted) "Granted" else "Not granted",
        )
        Spacer(Modifier.width(SpideySpacing.xs))
        Text(
            text = if (granted) "Enabled" else "Not enabled",
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) MaterialTheme.spideyColors.success else MaterialTheme.spideyColors.textSecondary,
        )
    }
}
