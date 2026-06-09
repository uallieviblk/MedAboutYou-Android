// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.data.remote.BarcodeLookup
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.uallsi.medaboutyou.ui.calendar.CalendarScreen
import com.uallsi.medaboutyou.ui.dashboard.InsightsScreen
import com.uallsi.medaboutyou.ui.detail.DetailScreen
import com.uallsi.medaboutyou.ui.schedules.SchedulesScreen
import com.uallsi.medaboutyou.ui.search.ScanScreen
import com.uallsi.medaboutyou.ui.search.SearchScreen
import com.uallsi.medaboutyou.ui.settings.SettingsScreen
import com.uallsi.medaboutyou.ui.today.TodayScreen

/** Bottom-nav destinations. Search, Statistics and Settings are sub-pages. */
private enum class Tab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    TODAY("today", R.string.nav_today, Icons.Filled.Home, Icons.Outlined.Home),
    CALENDAR("calendar", R.string.nav_calendar, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    SCHEDULES("schedules", R.string.nav_schedules, Icons.Filled.Medication, Icons.Outlined.Medication),
}

/** Full-screen routes reached from the Schedules FAB or the overflow menu. */
private val SUB_PAGES = setOf("search", "insights", "settings")

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
    // Detail and the camera scanner are full-screen; hide app chrome there.
    val immersive = currentRoute == "detail" || currentRoute == "scan"
    val subPage = currentRoute in SUB_PAGES

    val title = stringResource(
        when (currentRoute) {
            "search" -> R.string.title_search
            Tab.CALENDAR.route -> R.string.title_calendar
            Tab.SCHEDULES.route -> R.string.title_schedules
            "insights" -> R.string.title_insights
            "settings" -> R.string.title_settings
            else -> R.string.title_today
        }
    )

    val layoutType =
        if (immersive) NavigationSuiteType.None
        else NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())

    val tabLabels = Tab.entries.associateWith { stringResource(it.labelRes) }

    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = layoutType,
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                val selected = currentRoute == tab.route
                val label = tabLabels.getValue(tab)
                item(
                    selected = selected,
                    onClick = {
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = label) },
                    label = { Text(label) },
                )
            }
        },
    ) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        var menuOpen by remember { mutableStateOf(false) }
        Scaffold(
            modifier = if (immersive) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                if (!immersive) {
                    LargeTopAppBar(
                        title = { Text(title) },
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            if (subPage) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                            }
                        },
                        actions = {
                            // Statistics + Settings live in an overflow menu on the
                            // main tabs (they're no longer bottom-nav destinations).
                            if (!subPage) {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more))
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.nav_insights)) },
                                        leadingIcon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                                        onClick = { menuOpen = false; navController.navigate("insights") },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_settings)) },
                                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                        onClick = { menuOpen = false; navController.navigate("settings") },
                                    )
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
                composable("search") {
                    SearchScreen(
                        onOpenMedicine = { med ->
                            Selection.medicine = med
                            navController.navigate("detail")
                        },
                        onCustomMedicine = { typedName ->
                            // Create a user/custom therapy from the typed text; the
                            // schedule dialog opens on Calendar with the name editable.
                            CalendarPrefill.name = typedName
                            CalendarPrefill.source = com.uallsi.medaboutyou.model.Source.EMA
                            CalendarPrefill.extId = ""
                            navController.popBackStack(route = "search", inclusive = true)
                            navController.navigate(Tab.CALENDAR.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onScan = { navController.navigate("scan") },
                        prefillQuery = ScanPrefill.query,
                        onConsumePrefill = { ScanPrefill.query = null },
                    )
                }
                composable("scan") {
                    ScanScreen(
                        onResult = { raw ->
                            ScanPrefill.query = BarcodeLookup.toQuery(raw)
                            navController.popBackStack()
                        },
                        onClose = { navController.popBackStack() },
                    )
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
                                CalendarPrefill.source = m.source
                                CalendarPrefill.extId = m.extId
                                // Drop detail + search so we land cleanly on Calendar.
                                navController.popBackStack(route = "search", inclusive = true)
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
                        prefillSource = CalendarPrefill.source,
                        prefillExtId = CalendarPrefill.extId,
                        onConsumePrefill = { CalendarPrefill.name = null },
                    )
                }
                composable(Tab.SCHEDULES.route) {
                    SchedulesScreen(onAddMedicine = { navController.navigate("search") })
                }
                composable("insights") { InsightsScreen() }
                composable("settings") { SettingsScreen() }
            }
        }
    }
}

/** One-shot holder for the medicine identity to prefill the new-schedule dialog. */
object CalendarPrefill {
    @Volatile
    var name: String? = null

    @Volatile
    var source: com.uallsi.medaboutyou.model.Source = com.uallsi.medaboutyou.model.Source.EMA

    @Volatile
    var extId: String = ""
}

/** One-shot holder for a scanned package code, consumed by Search as an AIFA query. */
object ScanPrefill {
    @Volatile
    var query: String? = null
}
