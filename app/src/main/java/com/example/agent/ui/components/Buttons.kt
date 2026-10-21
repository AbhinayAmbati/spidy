package com.example.agent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.agent.ui.theme.PillShape
import com.example.agent.ui.theme.SpideyInk
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

private val ButtonPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)

/** The one filled blue action on a screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = SpideySpacing.controlHeight),
        shape = PillShape,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.spideyColors.border,
            disabledContentColor = MaterialTheme.spideyColors.textSecondary,
        ),
    ) {
        ButtonLabel(text)
    }
}

/** A white pill with a blue outline, for the second action beside a [PrimaryButton]. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val blue = MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = SpideySpacing.controlHeight),
        shape = PillShape,
        contentPadding = ButtonPadding,
        border = BorderStroke(2.dp, if (enabled) blue else MaterialTheme.spideyColors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = blue,
            disabledContentColor = MaterialTheme.spideyColors.textSecondary,
        ),
    ) {
        ButtonLabel(text)
    }
}

/** The black "SHOP NOW" style pill used on hero banners. */
@Composable
fun DarkPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
        shape = PillShape,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = SpideyInk,
            contentColor = Color.White,
        ),
    ) {
        ButtonLabel(text)
    }
}

@Composable
private fun ButtonLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
