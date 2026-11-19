package com.chencohensolutions.knesset.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chencohensolutions.knesset.data.repo.KnessetContext
import com.chencohensolutions.knesset.resources.startup_failed
import com.chencohensolutions.knesset.resources.search
import com.chencohensolutions.knesset.resources.favorites
import com.chencohensolutions.knesset.resources.data_explorer
import com.chencohensolutions.knesset.resources.about
import com.chencohensolutions.knesset.ui.components.ErrorState
import com.chencohensolutions.knesset.ui.components.LoadingState
import com.chencohensolutions.knesset.ui.components.RootTopBar
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
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
import com.chencohensolutions.knesset.resources.tab_committees
import com.chencohensolutions.knesset.resources.tab_home
import com.chencohensolutions.knesset.resources.tab_legislation
import com.chencohensolutions.knesset.resources.tab_people
import com.chencohensolutions.knesset.resources.tab_plenum
import com.chencohensolutions.knesset.ui.settings.SettingsScreen
import com.chencohensolutions.knesset.ui.legislation.BillScreen
import com.chencohensolutions.knesset.ui.legislation.LawScreen
import com.chencohensolutions.knesset.ui.legislation.LegislationScreen
import com.chencohensolutions.knesset.ui.legislation.SecondaryLawScreen
import androidx.navigation.toRoute
import com.chencohensolutions.knesset.ui.settings.AboutScreen
import com.chencohensolutions.knesset.ui.lobbyists.LobbyistScreen
import com.chencohensolutions.knesset.ui.explorer.ExplorerItemScreen
import com.chencohensolutions.knesset.ui.explorer.ExplorerScreen
import com.chencohensolutions.knesset.ui.explorer.ExplorerSetScreen
import com.chencohensolutions.knesset.ui.home.HomeScreen
import com.chencohensolutions.knesset.ui.favorites.FavoritesScreen
import com.chencohensolutions.knesset.ui.search.SearchScreen
import com.chencohensolutions.knesset.ui.components.KindListScreen
import com.chencohensolutions.knesset.ui.committees.CommitteeScreen
import com.chencohensolutions.knesset.ui.committees.CommitteeSessionScreen
import com.chencohensolutions.knesset.ui.committees.CommitteesScreen
import com.chencohensolutions.knesset.ui.plenum.MotionScreen
import com.chencohensolutions.knesset.ui.plenum.PlenumScreen
import com.chencohensolutions.knesset.ui.plenum.QuestionScreen
import com.chencohensolutions.knesset.ui.plenum.SittingScreen
import com.chencohensolutions.knesset.ui.plenum.VoteScreen
import com.chencohensolutions.knesset.ui.people.FactionScreen
import com.chencohensolutions.knesset.ui.people.MemberScreen
import com.chencohensolutions.knesset.ui.people.MinistryScreen
import com.chencohensolutions.knesset.ui.people.PeopleScreen
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
    val ctx = koinInject<KnessetContext>()
    val startup by ctx.startup.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (startup !is KnessetContext.Startup.Ready) ctx.start() }
    when (val s = startup) {
        KnessetContext.Startup.Loading -> Scaffold { LoadingState(Modifier.padding(it).fillMaxSize()) }
        is KnessetContext.Startup.Failed -> Scaffold { padding ->
            Column(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.startup_failed), style = MaterialTheme.typography.titleMedium)
                ErrorState(s.error, onRetry = { scope.launch { ctx.start() } }, modifier = Modifier)
            }
        }
        KnessetContext.Startup.Ready -> MainScaffold()
    }
}

@Composable
private fun MainScaffold() {
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
      CompositionLocalProvider(LocalNav provides nav) {
        NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable<HomeRoute> { HomeScreen() }
            composable<LegislationRoute> { LegislationScreen() }
            composable<BillRoute> { BillScreen(it.toRoute<BillRoute>().id) }
            composable<LawRoute> { LawScreen(it.toRoute<LawRoute>().id) }
            composable<SecondaryLawRoute> { SecondaryLawScreen(it.toRoute<SecondaryLawRoute>().id) }
            composable<PlenumRoute> { PlenumScreen() }
            composable<VoteRoute> { VoteScreen(it.toRoute<VoteRoute>().id) }
            composable<SittingRoute> { SittingScreen(it.toRoute<SittingRoute>().id) }
            composable<MotionRoute> { MotionScreen(it.toRoute<MotionRoute>().id) }
            composable<QuestionRoute> { QuestionScreen(it.toRoute<QuestionRoute>().id) }
            composable<CommitteesRoute> { CommitteesScreen() }
            composable<CommitteeRoute> { CommitteeScreen(it.toRoute<CommitteeRoute>().id) }
            composable<CommitteeSessionRoute> { CommitteeSessionScreen(it.toRoute<CommitteeSessionRoute>().id) }
            composable<PeopleRoute> { PeopleScreen() }
            composable<MemberRoute> { MemberScreen(it.toRoute<MemberRoute>().id) }
            composable<FactionRoute> { FactionScreen(it.toRoute<FactionRoute>().id) }
            composable<MinistryRoute> { MinistryScreen(it.toRoute<MinistryRoute>().id) }
            composable<SearchRoute> { SearchScreen(it.toRoute<SearchRoute>().query) }
            composable<KindListRoute> { val r = it.toRoute<KindListRoute>(); KindListScreen(r.kind, r.query) }
            composable<FavoritesRoute> { FavoritesScreen() }
            composable<ExplorerRoute> { ExplorerScreen() }
            composable<ExplorerSetRoute> { ExplorerSetScreen(it.toRoute<ExplorerSetRoute>().name) }
            composable<ExplorerItemRoute> { val r = it.toRoute<ExplorerItemRoute>(); ExplorerItemScreen(r.name, r.id) }
            composable<AboutRoute> { AboutScreen() }
            composable<LobbyistRoute> { LobbyistScreen(it.toRoute<LobbyistRoute>().id) }
            composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
        }
      }
    }
}
