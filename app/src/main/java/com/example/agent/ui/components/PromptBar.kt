package com.example.agent.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/**
 * The primary affordance of the app: a big rounded search-style bar that asks
 * "what do you want me to do?".
 *
 * Voice uses the platform's own speech recognizer via ACTION_RECOGNIZE_SPEECH, so
 * there is no audio permission to request and no audio ever leaves the device
 * through us — the system recognizer owns that.
 */
@Composable
fun PromptBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Ask your agent…",
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.spideyColors
    val context = LocalContext.current
    val canSend = enabled && value.isNotBlank()

    val recognizerAvailable = remember {
        context.packageManager.resolveActivity(speechIntent(), 0) != null
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) onValueChange(spoken)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, colors.border),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 60.dp)
                .padding(start = SpideySpacing.md, end = SpideySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(SpideySpacing.sm))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 28.dp)
                        .semantics { contentDescription = "Agent request field" },
                )
            }

            IconButton(
                onClick = {
                    if (recognizerAvailable) {
                        try {
                            voiceLauncher.launch(speechIntent())
                        } catch (_: ActivityNotFoundException) {
                            // No recognizer on this device; the button stays disabled next time.
                        }
                    }
                },
                enabled = enabled && recognizerAvailable,
                modifier = Modifier.size(SpideySpacing.minTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = if (recognizerAvailable) {
                        "Speak your request"
                    } else {
                        "Voice input is not available on this device"
                    },
                    tint = if (enabled && recognizerAvailable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        colors.textSecondary
                    },
                )
            }

            IconButton(
                onClick = onSubmit,
                enabled = canSend,
                modifier = Modifier
                    .size(SpideySpacing.minTouchTarget)
                    .clip(CircleShape)
                    .background(if (canSend) MaterialTheme.colorScheme.primary else colors.border),
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Start task",
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary else colors.textSecondary,
                )
            }
        }
    }
}

private fun speechIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_PROMPT, "What should I do?")
}
