package com.example.agent.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.agent.ui.theme.spideyColors

/** One destination in the bottom navigation bar. */
data class BottomBarItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** The white bottom bar with a solid blue pill behind the selected tab, as on Instamart. */
@Composable
fun SpideyBottomBar(
    items: List<BottomBarItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = selectedRoute == item.route,
                onClick = { onSelect(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.spideyColors.textSecondary,
                    unselectedTextColor = MaterialTheme.spideyColors.textSecondary,
                ),
            )
        }
    }
}
