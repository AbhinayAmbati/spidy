package com.example.agent.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.agent.R
import com.example.agent.ui.history.HistoryScreen
import com.example.agent.ui.history.TaskDetailScreen
import com.example.agent.ui.home.HomeScreen
import com.example.agent.ui.onboarding.OnboardingScreen
import com.example.agent.ui.routines.RoutinesScreen
import com.example.agent.ui.settings.MemoryScreen
import com.example.agent.ui.settings.ProviderScreen
import com.example.agent.ui.settings.SettingsScreen
import com.example.agent.ui.settings.TermsScreen
import com.example.agent.ui.splash.SplashScreen
import com.example.agent.ui.task.TaskScreen
import com.example.agent.ui.theme.spideyColors

private data class BottomTab(val route: String, val labelRes: Int, val icon: ImageVector)

private val TABS = listOf(
    BottomTab(Destinations.HOME, R.string.nav_home, Icons.Filled.Home),
    BottomTab(Destinations.HISTORY, R.string.nav_tasks, Icons.Filled.List),
    BottomTab(Destinations.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun SpideyApp(startDestination: String = Destinations.SPLASH) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TABS.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                            label = { Text(stringResource(tab.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.spideyColors.brownAccent,
                                selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                indicatorColor = MaterialTheme.colorScheme.surface,
                                unselectedIconColor = MaterialTheme.spideyColors.textSecondary,
                                unselectedTextColor = MaterialTheme.spideyColors.textSecondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destinations.SPLASH) {
                SplashScreen(
                    onFinished = {
                        navController.navigate(Destinations.HOME) {
                            popUpTo(Destinations.SPLASH) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Destinations.HOME) {
                HomeScreen(
                    onOpenTask = {
                        navController.navigate(Destinations.TASKS) { launchSingleTop = true }
                    },
                    onOpenHistory = { navController.navigate(Destinations.HISTORY) },
                    onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                )
            }
            composable(Destinations.TASKS) {
                TaskScreen(onBack = { navController.popBackStack() })
            }
            composable(Destinations.HISTORY) {
                HistoryScreen(
                    onOpenTask = { id -> navController.navigate(Destinations.taskDetail(id)) },
                )
            }
            composable(Destinations.SETTINGS) {
                SettingsScreen(
                    onOpenProvider = { navController.navigate(Destinations.PROVIDER) },
                    onOpenOnboarding = { navController.navigate(Destinations.ONBOARDING) },
                    onOpenMemory = { navController.navigate(Destinations.MEMORY) },
                    onOpenRoutines = { navController.navigate(Destinations.ROUTINES) },
                    onOpenTerms = { navController.navigate(Destinations.TERMS) },
                )
            }
            composable(
                route = Destinations.TASK_DETAIL_ROUTE,
                arguments = listOf(
                    navArgument(Destinations.TASK_DETAIL_ARG) { type = NavType.StringType },
                ),
            ) {
                TaskDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Destinations.MEMORY) {
                MemoryScreen(onBack = { navController.popBackStack() })
            }
            composable(Destinations.ROUTINES) {
                RoutinesScreen(onBack = { navController.popBackStack() })
            }
            composable(Destinations.TERMS) {
                TermsScreen(onBack = { navController.popBackStack() })
            }
            composable(Destinations.PROVIDER) {
                ProviderScreen(onBack = { navController.popBackStack() })
            }
            composable(Destinations.ONBOARDING) {
                OnboardingScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
