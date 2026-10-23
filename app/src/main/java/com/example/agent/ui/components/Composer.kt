package com.example.agent.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 * The message composer, docked at the bottom like ChatGPT's and Claude's: a rounded field
 * that grows to a few lines, a microphone while it is empty, and a send button once there
 * is something to send. The two buttons crossfade.
 *
 * Voice: when [onMicClick] is given the app records and transcribes on the device itself;
 * otherwise the platform's recognizer is used via ACTION_RECOGNIZE_SPEECH.
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Ask Spidey to do something",
    enabled: Boolean = true,
    onMicClick: (() -> Unit)? = null,
    listening: Boolean = false,
) {
    val colors = MaterialTheme.spideyColors
    val context = LocalContext.current
    val tick = rememberTick()
    val canSend = enabled && value.isNotBlank()

    // With onMicClick the app records and transcribes on the device itself; without it the
    // platform's recognizer is used, as before.
    val ownVoice = onMicClick != null
    val systemRecognizer = remember {
        context.packageManager.resolveActivity(speechIntent(), 0) != null
    }
    val recognizerAvailable = ownVoice || systemRecognizer

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colors.muted)
            .padding(start = SpideySpacing.md + 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
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
                maxLines = 5,
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
                    .heightIn(min = 24.dp)
                    .semantics { contentDescription = "Request field" },
            )
        }

        Crossfade(targetState = canSend, label = "composerAction") { sending ->
            if (sending) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .pressClickable(
                            pressedScale = 0.9f,
                            onClick = {
                                tick()
                                onSubmit()
                            },
                        )
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Start task",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .pressClickable(
                            onClick = {
                                if (onMicClick != null) {
                                    if (enabled) onMicClick()
                                } else if (enabled && recognizerAvailable) {
                                    try {
                                        voiceLauncher.launch(speechIntent())
                                    } catch (_: ActivityNotFoundException) {
                                        // No recognizer on this device; nothing to start.
                                    }
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = when {
                            listening -> "Stop listening"
                            recognizerAvailable -> "Speak your request"
                            else -> "Voice input is not available on this device"
                        },
                        tint = if (listening) {
                            MaterialTheme.colorScheme.primary
                        } else if (enabled && recognizerAvailable) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            colors.textSecondary
                        },
                    )
                }
            }
        }
    }
}

private fun speechIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_PROMPT, "What should I do?")
}
