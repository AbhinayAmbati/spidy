package com.example.agent.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.agent.R
import com.example.agent.core.permissions.PermissionUtils
import com.example.agent.ui.components.GlassCard
import com.example.agent.ui.components.PrimaryButton
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/**
 * Honest permission onboarding: it explains exactly what accessibility is used for,
 * what it cannot do, and leaves the final switch to the user in system settings.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current

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

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Optional: notification access",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(SpideySpacing.xs))
            Text(
                text = "Lets Spidey read your notifications when a task needs them, and show its " +
                    "progress while it works. Everything else works without it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.textSecondary,
            )
            Spacer(Modifier.height(SpideySpacing.md))
            SecondaryButton(
                text = "Enable notification access",
                onClick = { context.startActivity(PermissionUtils.notificationListenerSettingsIntent()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(SpideySpacing.xl))

        PrimaryButton(
            text = stringResource(R.string.onboarding_enable),
            onClick = { context.startActivity(PermissionUtils.accessibilitySettingsIntent()) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SpideySpacing.md))
        SecondaryButton(
            text = "Maybe later",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
