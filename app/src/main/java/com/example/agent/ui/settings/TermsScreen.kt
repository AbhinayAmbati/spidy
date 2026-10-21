package com.example.agent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.agent.ui.components.ScreenTopBar
import com.example.agent.ui.components.SpideyCard
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.SpideyTheme
import com.example.agent.ui.theme.spideyColors

/** Terms of Service, linked from Settings. */
@Composable
fun TermsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Terms of Service", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpideySpacing.screenGutter)
                .padding(bottom = SpideySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SpideySpacing.md),
        ) {
            Text(
                text = "Last updated ${TermsContent.LAST_UPDATED}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.spideyColors.textSecondary,
            )

            SpideyCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Please read these Terms carefully. They limit our liability and place " +
                        "responsibility for the agent's actions on you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            TermsContent.SECTIONS.forEach { section ->
                SpideyCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = section.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.spideyColors.textSecondary,
                        modifier = Modifier.padding(top = SpideySpacing.xs),
                    )
                }
            }
        }
    }
}

@Preview(name = "Terms", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun TermsPreview() {
    SpideyTheme {
        TermsScreen(onBack = {})
    }
}
