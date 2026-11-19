package com.chencohensolutions.knesset.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.coming_soon
import com.chencohensolutions.knesset.resources.tab_committees
import com.chencohensolutions.knesset.resources.tab_home
import com.chencohensolutions.knesset.resources.tab_legislation
import com.chencohensolutions.knesset.resources.tab_people
import com.chencohensolutions.knesset.resources.tab_plenum
import com.chencohensolutions.knesset.ui.settings.SettingsScreen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.reflect.KClass

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: StringResource, val icon: ImageVector)

private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, Res.string.tab_home, Icons.Outlined.Today),
    Tab(LegislationRoute, LegislationRoute::class, Res.string.tab_legislation, Icons.Outlined.Gavel),
    Tab(PlenumRoute, PlenumRoute::class, Res.string.tab_plenum, Icons.Outlined.HowToVote),
    Tab(CommitteesRoute, CommitteesRoute::class, Res.string.tab_committees, Icons.Outlined.AccountBalance),
    Tab(PeopleRoute, PeopleRoute::class, Res.string.tab_people, Icons.Outlined.Groups),
)

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val onTabRoot = tabs.any { tab -> destination?.hasRoute(tab.routeClass) == true }
    Scaffold(
        bottomBar = {
            if (onTabRoot) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable<HomeRoute> { Placeholder(Res.string.tab_home) }
            composable<LegislationRoute> { Placeholder(Res.string.tab_legislation) }
            composable<PlenumRoute> { Placeholder(Res.string.tab_plenum) }
            composable<CommitteesRoute> { Placeholder(Res.string.tab_committees) }
            composable<PeopleRoute> { Placeholder(Res.string.tab_people) }
            composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun Placeholder(title: StringResource) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(title) + " · " + stringResource(Res.string.coming_soon))
    }
}
