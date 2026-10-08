package com.example.agent.ui.settings

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
import com.example.agent.ui.components.GlassCard
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/** Terms of Service, linked from Settings. */
@Composable
fun TermsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SpideySpacing.screenGutter)
            .padding(top = SpideySpacing.xl, bottom = SpideySpacing.xxl),
    ) {
        Text(
            text = "Terms of Service",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(SpideySpacing.xs))
        Text(
            text = "Last updated ${TermsContent.LAST_UPDATED}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.spideyColors.textSecondary,
        )

        Spacer(Modifier.height(SpideySpacing.lg))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Please read these Terms carefully. They limit our liability and place " +
                    "responsibility for the agent's actions on you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.lg)) {
            TermsContent.SECTIONS.forEach { section ->
                Column {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(SpideySpacing.xs))
                    Text(
                        text = section.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.spideyColors.textSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        SecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
