package com.chencohensolutions.knesset.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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

/** A root tab with a row of section tabs under the top bar. */
@Composable
fun SectionTabsScreen(title: StringResource, sections: List<Pair<StringResource, @Composable () -> Unit>>) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(topBar = { RootTopBar(stringResource(title)) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (sections.size > 1) {
                PrimaryTabRow(selectedTabIndex = selected) {
                    sections.forEachIndexed { i, (label, _) ->
                        Tab(selected = selected == i, onClick = { selected = i }, text = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    }
                }
            }
            sections[selected].second()
        }
    }
}
