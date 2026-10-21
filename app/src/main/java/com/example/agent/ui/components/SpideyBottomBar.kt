package com.example.agent.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.agent.ui.theme.LocalMotionEnabled
import com.example.agent.ui.theme.spideyColors

/** One destination in the bottom bar: an outlined icon at rest, a filled one when selected. */
data class BottomBarItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/**
 * An icon-only bottom bar in the Instagram manner: no labels, no indicator pill, the
 * selected tab simply turns solid and springs once. A hairline separates it from content.
 */
@Composable
fun SpideyBottomBar(
    items: List<BottomBarItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tick = rememberTick()
    Box(modifier = modifier.fillMaxWidth().navigationBarsPadding()) {
        HorizontalDivider(
            modifier = Modifier.align(Alignment.TopCenter),
            color = MaterialTheme.spideyColors.border,
        )
        Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            items.forEach { item ->
                val selected = selectedRoute == item.route
                BottomBarTab(
                    item = item,
                    selected = selected,
                    onClick = {
                        if (!selected) tick()
                        onSelect(item.route)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarTab(
    item: BottomBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotionEnabled.current
    val scale by animateFloatAsState(
        targetValue = if (selected && motion) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tabScale",
    )
    Box(
        modifier = modifier
            .height(56.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.icon,
            contentDescription = item.label,
            tint = if (selected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.spideyColors.textSecondary
            },
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}
