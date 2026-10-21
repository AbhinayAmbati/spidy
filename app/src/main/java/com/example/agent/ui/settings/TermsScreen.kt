package com.example.agent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.agent.ui.components.ScreenTopBar
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.SpideyTheme
import com.example.agent.ui.theme.spideyColors

/** Terms of Service, linked from Settings. Plain reading text on a white page. */
@Composable
fun TermsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Terms of Service", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpideySpacing.screenGutter, vertical = SpideySpacing.md),
            verticalArrangement = Arrangement.spacedBy(SpideySpacing.lg),
        ) {
            Text(
                text = "Last updated ${TermsContent.LAST_UPDATED}. Please read these Terms carefully. " +
                    "They limit our liability and place responsibility for the agent's actions on you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.textSecondary,
            )

            TermsContent.SECTIONS.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.xxs)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = section.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.spideyColors.textSecondary,
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
