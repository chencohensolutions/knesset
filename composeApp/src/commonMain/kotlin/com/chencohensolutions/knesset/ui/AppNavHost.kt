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
import com.chencohensolutions.knesset.ui.video.MemberVideosScreen
import com.chencohensolutions.knesset.ui.video.VideoPlayerScreen
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import com.chencohensolutions.knesset.ui.components.FloatingNavBar
import com.chencohensolutions.knesset.ui.components.NavItem
import com.chencohensolutions.knesset.ui.theme.AppTheme
import com.chencohensolutions.knesset.ui.theme.LocalSection
import com.chencohensolutions.knesset.ui.theme.Motion
import com.chencohensolutions.knesset.ui.theme.Section

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

/** The section hue for each destination (docs 2.1). */
private fun sectionOf(entry: NavBackStackEntry?): Section {
    val d = entry?.destination ?: return Section.HOME
    fun has(vararg c: KClass<*>) = c.any { d.hasRoute(it) }
    return when {
        has(LegislationRoute::class, BillRoute::class, LawRoute::class, SecondaryLawRoute::class) -> Section.LEGISLATION
        has(PlenumRoute::class, VoteRoute::class, SittingRoute::class, MotionRoute::class, QuestionRoute::class, VideoPlayerRoute::class) -> Section.PLENUM
        has(CommitteesRoute::class, CommitteeRoute::class, CommitteeSessionRoute::class) -> Section.COMMITTEES
        has(PeopleRoute::class, MemberRoute::class, FactionRoute::class, MinistryRoute::class, MemberVideosRoute::class, LobbyistRoute::class) -> Section.PEOPLE
        else -> Section.HOME
    }
}

private fun isTabRoot(entry: NavBackStackEntry): Boolean = tabs.any { entry.destination.hasRoute(it.routeClass) }

@Composable
private fun MainScaffold() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val onTabRoot = tabs.any { tab -> destination?.hasRoute(tab.routeClass) == true }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val shift = with(density) { 30.dp.roundToPx() }
    // A forward push starts offset toward the end side (the right in English, the left in Hebrew) and
    // slides to place; back reverses it (docs 5.1).
    val dir = if (rtl) -1 else 1
    val emphasized = tween<Float>(Motion.MEDIUM, easing = Motion.emphasized)
    val emphasizedInt = tween<androidx.compose.ui.unit.IntOffset>(Motion.MEDIUM, easing = Motion.emphasized)
    Scaffold(
        containerColor = AppTheme.colors.background,
        bottomBar = {
            AnimatedVisibility(onTabRoot, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                FloatingNavBar(tabs.mapIndexed { i, tab ->
                    NavItem(
                        label = stringResource(tab.label),
                        icon = tab.icon,
                        section = Section.entries[i],
                        selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                })
            }
        },
    ) { padding ->
      CompositionLocalProvider(LocalNav provides nav) {
        NavHost(
            nav, startDestination = HomeRoute,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = {
                if (isTabRoot(initialState) && isTabRoot(targetState)) fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = 0.96f)
                else slideInHorizontally(emphasizedInt) { dir * shift } + fadeIn(emphasized)
            },
            exitTransition = {
                if (isTabRoot(initialState) && isTabRoot(targetState)) fadeOut(tween(90))
                else slideOutHorizontally(emphasizedInt) { -dir * shift } + fadeOut(emphasized)
            },
            popEnterTransition = { slideInHorizontally(emphasizedInt) { -dir * shift } + fadeIn(emphasized) },
            popExitTransition = { slideOutHorizontally(emphasizedInt) { dir * shift } + fadeOut(emphasized) },
        ) {
            screen<HomeRoute> { HomeScreen() }
            screen<LegislationRoute> { LegislationScreen() }
            screen<BillRoute> { BillScreen(it.toRoute<BillRoute>().id) }
            screen<LawRoute> { LawScreen(it.toRoute<LawRoute>().id) }
            screen<SecondaryLawRoute> { SecondaryLawScreen(it.toRoute<SecondaryLawRoute>().id) }
            screen<PlenumRoute> { PlenumScreen() }
            screen<VoteRoute> { VoteScreen(it.toRoute<VoteRoute>().id) }
            screen<SittingRoute> { SittingScreen(it.toRoute<SittingRoute>().id) }
            screen<MotionRoute> { MotionScreen(it.toRoute<MotionRoute>().id) }
            screen<QuestionRoute> { QuestionScreen(it.toRoute<QuestionRoute>().id) }
            screen<CommitteesRoute> { CommitteesScreen() }
            screen<CommitteeRoute> { CommitteeScreen(it.toRoute<CommitteeRoute>().id) }
            screen<CommitteeSessionRoute> { CommitteeSessionScreen(it.toRoute<CommitteeSessionRoute>().id) }
            screen<PeopleRoute> { PeopleScreen() }
            screen<MemberRoute> { MemberScreen(it.toRoute<MemberRoute>().id) }
            screen<MemberVideosRoute> { MemberVideosScreen(it.toRoute<MemberVideosRoute>().id) }
            screen<VideoPlayerRoute> { val r = it.toRoute<VideoPlayerRoute>(); VideoPlayerScreen(r.url, r.title, r.fallbackUrl) }
            screen<FactionRoute> { FactionScreen(it.toRoute<FactionRoute>().id) }
            screen<MinistryRoute> { MinistryScreen(it.toRoute<MinistryRoute>().id) }
            screen<SearchRoute> { SearchScreen(it.toRoute<SearchRoute>().query) }
            screen<KindListRoute> { val r = it.toRoute<KindListRoute>(); KindListScreen(r.kind, r.query) }
            screen<FavoritesRoute> { FavoritesScreen() }
            screen<ExplorerRoute> { ExplorerScreen() }
            screen<ExplorerSetRoute> { ExplorerSetScreen(it.toRoute<ExplorerSetRoute>().name) }
            screen<ExplorerItemRoute> { val r = it.toRoute<ExplorerItemRoute>(); ExplorerItemScreen(r.name, r.id) }
            screen<AboutRoute> { AboutScreen() }
            screen<DownloadsRoute> { com.chencohensolutions.knesset.ui.video.DownloadsScreen() }
            screen<LobbyistRoute> { LobbyistScreen(it.toRoute<LobbyistRoute>().id) }
            screen<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
        }
      }
    }
}

/** A destination that provides its section hue to everything inside it. */
private inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable (NavBackStackEntry) -> Unit) {
    composable<T> { entry ->
        CompositionLocalProvider(LocalSection provides sectionOf(entry)) { content(entry) }
    }
}
