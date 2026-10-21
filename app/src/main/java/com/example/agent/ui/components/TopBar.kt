package com.example.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/** The header for every screen reached from another one: a round back button and a bold title. */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = SpideySpacing.screenGutter,
                vertical = SpideySpacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(SpideySpacing.minTouchTarget)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.width(SpideySpacing.sm))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** The title row for a top-level tab, without a back button. */
@Composable
fun TabTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = SpideySpacing.screenGutter,
                vertical = SpideySpacing.md,
            ),
    )
}

/**
 * The Home header, laid out like Instamart's location row: a blue icon tile, a bold blue
 * title with a grey line under it, and a round profile button on the right.
 */
@Composable
fun HomeHeader(
    title: String,
    subtitle: String,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = SpideySpacing.screenGutter,
                vertical = SpideySpacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.width(SpideySpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.textSecondary,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF3D4152))
                .clickable(role = Role.Button, onClick = onProfileClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "Settings",
                tint = Color.White,
            )
        }
    }
}
