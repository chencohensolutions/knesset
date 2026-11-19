package com.chencohensolutions.knesset.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chencohensolutions.knesset.core.platform.Sharer
import com.chencohensolutions.knesset.core.platform.UrlOpener
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.open_on_site
import com.chencohensolutions.knesset.resources.share
import com.chencohensolutions.knesset.resources.show_all_n
import com.chencohensolutions.knesset.resources.show_all
import com.chencohensolutions.knesset.resources.empty
import androidx.compose.foundation.layout.Box
import com.chencohensolutions.knesset.ui.LocalNav
import com.chencohensolutions.knesset.ui.components.DataText
import com.chencohensolutions.knesset.ui.components.DetailTopBar
import com.chencohensolutions.knesset.ui.components.DocumentRow
import com.chencohensolutions.knesset.ui.components.ErrorState
import com.chencohensolutions.knesset.ui.components.ItemRow
import com.chencohensolutions.knesset.ui.components.LoadingState
import com.chencohensolutions.knesset.ui.components.OfflineBanner
import com.chencohensolutions.knesset.ui.components.StatusChip
import com.chencohensolutions.knesset.ui.components.VoteBar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Extra top bar actions, such as the favorite star (supplied by the favorites feature). */
typealias DetailActions = @Composable (DetailContent) -> Unit

/** The detail body with its loading, error and refresh states, under any top bar. */
@Composable
fun DetailStateContent(vm: DetailViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        DetailState.Loading -> LoadingState(modifier.fillMaxSize())
        is DetailState.Failed -> ErrorState(s.error, onRetry = vm::refresh, modifier = modifier.fillMaxSize())
        is DetailState.Ready -> PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = vm::refresh, modifier = modifier.fillMaxSize()) {
            DetailBody(s.content, s.offline)
        }
    }
}

@Composable
fun DetailScreen(vm: DetailViewModel, fallbackTitle: String, extraActions: DetailActions = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sharer = koinInject<Sharer>()
    val opener = koinInject<UrlOpener>()
    val ready = state as? DetailState.Ready
    Scaffold(
        topBar = {
            DetailTopBar(ready?.content?.title ?: fallbackTitle) {
                ready?.content?.let { c ->
                    extraActions(c)
                    IconButton(onClick = { sharer.share(listOfNotNull(c.title, c.shareUrl ?: c.webUrl).joinToString("\n")) }) {
                        Icon(Icons.Outlined.Share, stringResource(Res.string.share))
                    }
                    if (c.webUrl != null) {
                        IconButton(onClick = { opener.open(c.webUrl) }) {
                            Icon(Icons.Outlined.OpenInBrowser, stringResource(Res.string.open_on_site))
                        }
                    }
                }
            }
        },
    ) { padding -> DetailStateContent(vm, Modifier.padding(padding)) }
}

@Composable
private fun DetailBody(c: DetailContent, offline: Boolean) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (offline) item { OfflineBanner() }
        if (c.title.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DataText(c.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    StatusChip(c.status)
                    if (c.subtitle != null) DataText(c.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        sections(c.sections)
        if (c.tabs.isNotEmpty()) {
            item {
                PrimaryScrollableTabRow(selectedTabIndex = tab.coerceIn(0, c.tabs.lastIndex), edgePadding = 8.dp) {
                    c.tabs.forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(t.title)) })
                    }
                }
            }
            val current = c.tabs[tab.coerceIn(0, c.tabs.lastIndex)]
            if (current.sections.all { it.isEmpty }) item { com.chencohensolutions.knesset.ui.components.EmptyState(Modifier.fillMaxWidth().padding(top = 24.dp)) }
            sections(current.sections)
        }
    }
}

private const val COLLAPSED_ROWS = 10

private fun LazyListScope.sections(sections: List<Section>) {
    sections.filterNot { it.isEmpty }.forEach { section ->
        item(contentType = "section") { SectionCard(section) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun SectionCard(section: Section) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(bottom = 8.dp)) {
            val title = section.title?.let { stringResource(it) }
            when (section) {
                is Section.Facts -> {
                    title?.let { SectionTitle(it) }
                    section.facts.forEach { f ->
                        val nav = LocalNav.current
                        val opener = koinInject<UrlOpener>()
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(stringResource(f.label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
                            if (f.route != null || f.url != null) {
                                DataText(
                                    f.value,
                                    Modifier.weight(0.6f).clickable(role = Role.Button) { if (f.route != null) nav.navigate(f.route) else opener.open(f.url!!) },
                                    style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                DataText(f.value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                is Section.Text -> {
                    title?.let { SectionTitle(it) }
                    DataText(section.text.orEmpty(), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
                }
                is Section.Links -> {
                    val nav = LocalNav.current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        title?.let { Box(Modifier.weight(1f)) { SectionTitle(if (section.countInTitle && section.showAll == null) "$it (${section.items.size})" else it) } }
                        if (section.showAll != null) TextButton(onClick = { nav.navigate(section.showAll) }) { Text(stringResource(Res.string.show_all)) }
                    }
                    if (section.items.isEmpty()) {
                        Text(stringResource(Res.string.empty), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Collapsible(section.items.size) { limit ->
                        section.items.take(limit).forEachIndexed { i, item ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            ItemRow(
                                item.title, item.subtitle, item.status, initials = item.initials, route = item.route, url = item.url,
                                below = item.bar?.let { b -> { VoteBar(b.first, b.second, b.third) } },
                            )
                        }
                    }
                }
                is Section.Groups -> {
                    var alternate by rememberSaveable(section.title?.key) { mutableStateOf(false) }
                    title?.let { SectionTitle(it) }
                    if (section.alternateLabel != null && section.alternate.isNotEmpty()) {
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(!alternate, { alternate = false }, { Text(section.primaryLabel?.let { stringResource(it) } ?: "") })
                            FilterChip(alternate, { alternate = true }, { Text(stringResource(section.alternateLabel)) })
                        }
                    }
                    val groups = if (alternate) section.alternate else section.groups
                    groups.forEach { (name, items) ->
                        DataText("$name (${items.size})", Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), style = MaterialTheme.typography.titleSmall)
                        Collapsible(items.size) { limit ->
                            items.take(limit).forEach { item ->
                                ItemRow(item.title, item.subtitle, item.status, initials = item.initials, route = item.route, url = item.url)
                            }
                        }
                    }
                }
                is Section.Documents -> {
                    title?.let { SectionTitle("$it (${section.docs.size})") }
                    Collapsible(section.docs.size) { limit -> section.docs.take(limit).forEach { DocumentRow(it) } }
                }
                is Section.Tally -> {
                    title?.let { SectionTitle(it) }
                    val t = section.tally
                    VoteBar(t.forCount, t.againstCount, t.abstainCount, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        t.byResult.forEach { (label, n) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(n.toString(), style = MaterialTheme.typography.headlineSmall)
                                DataText(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                is Section.Actions -> {
                    val opener = koinInject<UrlOpener>()
                    title?.let { SectionTitle(it) }
                    FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        section.links.forEach { a -> OutlinedButton(onClick = { opener.open(a.url) }) { Text(stringResource(a.label)) } }
                    }
                }
            }
        }
    }
}

/** Shows the first rows and a "show all" button for long sections. */
@Composable
private fun Collapsible(size: Int, content: @Composable (limit: Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    content(if (expanded) size else COLLAPSED_ROWS)
    if (!expanded && size > COLLAPSED_ROWS) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(Res.string.show_all_n, size))
        }
    }
}
