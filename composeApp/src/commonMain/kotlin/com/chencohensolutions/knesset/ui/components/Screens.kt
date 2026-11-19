package com.chencohensolutions.knesset.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.chencohensolutions.knesset.ui.theme.AppTheme
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chencohensolutions.knesset.data.repo.Api
import com.chencohensolutions.knesset.data.repo.KnessetContext
import com.chencohensolutions.knesset.data.repo.Loader
import com.chencohensolutions.knesset.data.repo.LookupRepo
import com.chencohensolutions.knesset.ui.detail.DetailContent
import com.chencohensolutions.knesset.ui.detail.DetailViewModel
import com.chencohensolutions.knesset.ui.list.ListSpec
import com.chencohensolutions.knesset.ui.list.ListViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun <R> rememberListVm(
    spec: ListSpec<R>,
    key: String,
    initialQuery: String = "",
    initialChoices: Map<String, Set<String>> = emptyMap(),
): ListViewModel<R> {
    val api = koinInject<Api>()
    val ctx = koinInject<KnessetContext>()
    val lookups = koinInject<LookupRepo>()
    return viewModel(key = key) { ListViewModel(spec, api, ctx, lookups, initialQuery, initialChoices) }
}

@Composable
fun rememberDetailVm(key: String, build: suspend Loader.(LookupRepo) -> DetailContent): DetailViewModel {
    val api = koinInject<Api>()
    val lookups = koinInject<LookupRepo>()
    return viewModel(key = key) { DetailViewModel(api) { build(lookups) } }
}

/** A root tab: gradient hero header that collapses with scroll, then pill tabs over a swipeable pager (docs 3.1, 3.4). */
@Composable
fun SectionTabsScreen(title: StringResource, sections: List<Pair<StringResource, @Composable () -> Unit>>) {
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var saved by rememberSaveable { mutableIntStateOf(0) }
    val pager = rememberPagerState(initialPage = saved) { sections.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { saved = pager.currentPage }
    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        containerColor = AppTheme.colors.background,
        topBar = { GradientHeader(stringResource(title), AppTheme.section, behavior, actions = { RootActions() }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (sections.size > 1) {
                PillTabs(sections.map { stringResource(it.first) }, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
            }
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 0, key = { it }) { page -> sections[page].second() }
        }
    }
}
