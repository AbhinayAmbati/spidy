package com.example.agent.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.agent.R
import com.example.agent.ui.components.BottomBarItem
import com.example.agent.ui.components.SpideyBottomBar
import com.example.agent.ui.history.HistoryScreen
import com.example.agent.ui.history.TaskDetailScreen
import com.example.agent.ui.home.HomeScreen
import com.example.agent.ui.onboarding.OnboardingScreen
import com.example.agent.ui.routines.RoutinesScreen
import com.example.agent.ui.settings.MemoryScreen
import com.example.agent.ui.settings.SettingsScreen
import com.example.agent.ui.settings.TermsScreen
import com.example.agent.ui.splash.SplashScreen
import com.example.agent.ui.task.TaskScreen

private val TAB_ROUTES = listOf(
    Destinations.HOME,
    Destinations.HISTORY,
    Destinations.ROUTINES,
    Destinations.SETTINGS,
)

@Composable
fun SpideyApp(startDestination: String = Destinations.SPLASH) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TAB_ROUTES

    val tabs = listOf(
        BottomBarItem(Destinations.HOME, stringResource(R.string.nav_home), Icons.Filled.Home),
        BottomBarItem(Destinations.HISTORY, stringResource(R.string.nav_tasks), Icons.Filled.History),
        BottomBarItem(Destinations.ROUTINES, stringResource(R.string.nav_routines), Icons.Filled.Replay),
        BottomBarItem(Destinations.SETTINGS, stringResource(R.string.nav_settings), Icons.Filled.Settings),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                SpideyBottomBar(
                    items = tabs,
                    selectedRoute = currentRoute,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
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
            composable(Destinations.ROUTINES) {
                RoutinesScreen()
            }
            composable(Destinations.SETTINGS) {
                SettingsScreen(
                    onOpenOnboarding = { navController.navigate(Destinations.ONBOARDING) },
                    onOpenMemory = { navController.navigate(Destinations.MEMORY) },
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
            composable(Destinations.TERMS) {
                TermsScreen(onBack = { navController.popBackStack() })
            }
            composable(Destinations.ONBOARDING) {
                OnboardingScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
