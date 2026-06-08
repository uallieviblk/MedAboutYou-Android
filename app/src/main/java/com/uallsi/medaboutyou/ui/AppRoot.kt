package com.uallsi.medaboutyou.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.uallsi.medaboutyou.ui.calendar.CalendarScreen
import com.uallsi.medaboutyou.ui.dashboard.InsightsScreen
import com.uallsi.medaboutyou.ui.detail.DetailScreen
import com.uallsi.medaboutyou.ui.search.SearchScreen
import com.uallsi.medaboutyou.ui.settings.SettingsScreen
import com.uallsi.medaboutyou.ui.today.TodayScreen

private enum class Tab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    TODAY("today", "Today", Icons.Filled.Home, Icons.Outlined.Home),
    SEARCH("search", "Search", Icons.Filled.Search, Icons.Outlined.Search),
    CALENDAR("calendar", "Calendar", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    INSIGHTS("insights", "Insights", Icons.Filled.BarChart, Icons.Outlined.BarChart),
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3AdaptiveNavigationSuiteApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)
@Composable
fun AppRoot(modifier: Modifier = Modifier, startTab: String = Tab.TODAY.route) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    // Detail is a full-screen route with its own top bar; hide app chrome there.
    val immersive = currentRoute == "detail"

    val title = when (currentRoute) {
        Tab.SEARCH.route -> "Find a medicine"
        Tab.CALENDAR.route -> "Calendar"
        Tab.INSIGHTS.route -> "Adherence & refills"
        "settings" -> "Settings"
        else -> "Today"
    }

    val layoutType =
        if (immersive) NavigationSuiteType.None
        else NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())

    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = layoutType,
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                val selected = currentRoute == tab.route
                item(
                    selected = selected,
                    onClick = {
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                )
            }
        },
    ) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = if (immersive) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                if (!immersive) {
                    LargeTopAppBar(
                        title = { Text(title) },
                        scrollBehavior = scrollBehavior,
                        actions = {
                            if (currentRoute != "settings") {
                                IconButton(onClick = { navController.navigate("settings") }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                }
                            }
                        },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startTab,
                modifier = Modifier.padding(padding),
            ) {
                composable(Tab.TODAY.route) { TodayScreen() }
                composable(Tab.SEARCH.route) {
                    SearchScreen(onOpenMedicine = { med ->
                        Selection.medicine = med
                        navController.navigate("detail")
                    })
                }
                composable("detail") {
                    val med = Selection.medicine
                    if (med == null) {
                        navController.popBackStack()
                    } else {
                        DetailScreen(
                            medicine = med,
                            onBack = { navController.popBackStack() },
                            onSchedule = { m ->
                                CalendarPrefill.name = m.name
                                navController.navigate(Tab.CALENDAR.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                }
                composable(Tab.CALENDAR.route) {
                    CalendarScreen(
                        prefillName = CalendarPrefill.name,
                        onConsumePrefill = { CalendarPrefill.name = null },
                    )
                }
                composable(Tab.INSIGHTS.route) { InsightsScreen() }
                composable("settings") { SettingsScreen() }
            }
        }
    }
}

/** One-shot holder for the medicine name to prefill the new-schedule dialog. */
object CalendarPrefill {
    @Volatile
    var name: String? = null
}
