package com.chencohensolutions.knesset.ui.detail

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chencohensolutions.knesset.core.platform.Sharer
import com.chencohensolutions.knesset.core.platform.UrlOpener
import com.chencohensolutions.knesset.domain.VoteTally
import com.chencohensolutions.knesset.resources.*
import com.chencohensolutions.knesset.ui.LocalNav
import com.chencohensolutions.knesset.ui.components.AppCard
import com.chencohensolutions.knesset.ui.components.Avatar
import com.chencohensolutions.knesset.ui.components.ChoicePill
import com.chencohensolutions.knesset.ui.components.DataText
import com.chencohensolutions.knesset.ui.components.DocumentRow
import com.chencohensolutions.knesset.ui.components.EmptyState
import com.chencohensolutions.knesset.ui.components.ErrorState
import com.chencohensolutions.knesset.ui.components.GradientHeader
import com.chencohensolutions.knesset.ui.components.HemicycleChart
import com.chencohensolutions.knesset.ui.components.ItemRow
import com.chencohensolutions.knesset.ui.components.LoadingState
import com.chencohensolutions.knesset.ui.components.OfflineBanner
import com.chencohensolutions.knesset.ui.components.PillTabs
import com.chencohensolutions.knesset.ui.components.StageTracker
import com.chencohensolutions.knesset.ui.components.StatusPill
import com.chencohensolutions.knesset.ui.components.VoteBar
import com.chencohensolutions.knesset.ui.components.VoteSummary
import com.chencohensolutions.knesset.ui.components.appearOnceSection
import com.chencohensolutions.knesset.ui.components.pressable
import com.chencohensolutions.knesset.ui.theme.AppTheme
import com.chencohensolutions.knesset.ui.theme.Radius
import com.chencohensolutions.knesset.ui.theme.Space
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Extra top bar actions, such as the favorite star (supplied by the favorites feature). */
typealias DetailActions = @Composable (DetailContent) -> Unit

/** The detail body with its loading, error and refresh states, under any header. Skeletons cross-fade to content. */
@Composable
fun DetailStateContent(vm: DetailViewModel, modifier: Modifier = Modifier, showHeader: Boolean = true) {
    val state by vm.state.collectAsStateWithLifecycle()
    Crossfade(state::class, animationSpec = tween(200), label = "detail", modifier = modifier.fillMaxSize()) { _ ->
        when (val s = state) {
            DetailState.Loading -> LoadingState(Modifier.fillMaxSize())
            is DetailState.Failed -> ErrorState(s.error, onRetry = vm::refresh, modifier = Modifier.fillMaxSize())
            is DetailState.Ready -> PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                DetailBody(s.content, s.offline, showHeader)
            }
        }
    }
}

@Composable
fun DetailScreen(vm: DetailViewModel, fallbackTitle: String, extraActions: DetailActions = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sharer = koinInject<Sharer>()
    val opener = koinInject<UrlOpener>()
    val nav = LocalNav.current
    val ready = state as? DetailState.Ready
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        containerColor = AppTheme.colors.background,
        topBar = {
            // The full title appears once, here, up to 4 lines, and collapses to one line (docs 3.2).
            GradientHeader(ready?.content?.title ?: fallbackTitle, AppTheme.section, behavior, onBack = { nav.popBackStack() }, expandedLines = 4) {
                ready?.content?.let { c ->
                    extraActions(c)
                    IconButton(onClick = { sharer.share(listOfNotNull(c.title, c.shareUrl ?: c.webUrl).joinToString("\n")) }) {
                        Icon(Icons.Outlined.Share, stringResource(Res.string.share), tint = Color.White)
                    }
                    if (c.webUrl != null) {
                        IconButton(onClick = { opener.open(c.webUrl) }) {
                            Icon(Icons.Outlined.OpenInBrowser, stringResource(Res.string.open_on_site), tint = Color.White)
                        }
                    }
                }
            }
        },
    ) { padding -> DetailStateContent(vm, Modifier.padding(padding), showHeader = false) }
}

@Composable
private fun DetailBody(c: DetailContent, offline: Boolean, showTitle: Boolean) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Space.m, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        if (offline) item { OfflineBanner() }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                if (c.avatar != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                        Avatar(c.avatar.first, c.avatar.second, size = 72.dp, ring = true)
                        Column {
                            DataText(c.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                            if (c.subtitle != null) DataText(c.subtitle, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        }
                    }
                } else {
                    if (showTitle && c.title.isNotEmpty()) DataText(c.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    if (c.status != null || c.subtitle != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs), itemVerticalAlignment = Alignment.CenterVertically) {
                            StatusPill(c.status)
                            if (c.subtitle != null) DataText(c.subtitle, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        }
                    }
                }
                c.stage?.let { (reached, stopped) ->
                    AppCard(Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(Space.l)) {
                            StageTracker(
                                listOf(
                                    stringResource(Res.string.stage_preliminary), stringResource(Res.string.stage_first),
                                    stringResource(Res.string.stage_committee), stringResource(Res.string.stage_final), stringResource(Res.string.stage_published),
                                ),
                                reached, stopped,
                            )
                        }
                    }
                }
            }
        }
        sections(c.sections)
        if (c.tabs.isNotEmpty()) {
            item { PillTabs(c.tabs.map { stringResource(it.title) }, tab.coerceIn(0, c.tabs.lastIndex)) { tab = it } }
            val current = c.tabs[tab.coerceIn(0, c.tabs.lastIndex)]
            if (current.sections.all { it.isEmpty }) item { EmptyState(Modifier.fillMaxWidth().padding(top = Space.xl)) }
            sections(current.sections)
        }
    }
}

private const val COLLAPSED_ROWS = 5

private fun LazyListScope.sections(sections: List<Section>) {
    sections.filterNot { it.isEmpty }.forEachIndexed { i, section ->
        item(contentType = "section") { Box(Modifier.appearOnceSection(i)) { SectionCard(section) } }
    }
}

/** Text placeholders the data layer uses for UI strings it cannot resolve itself. */
@Composable
private fun localized(text: String): String = when (text) {
    com.chencohensolutions.knesset.ui.people.VIDEOS_DISCLAIMER_MARKER -> stringResource(Res.string.videos_disclaimer)
    com.chencohensolutions.knesset.ui.people.VIDEOS_COMMITTEES_MARKER -> stringResource(Res.string.videos_open)
    com.chencohensolutions.knesset.ui.committees.WATCH_MARKER -> stringResource(Res.string.watch)
    else -> text
}

@Composable
private fun SectionHeader(title: String?, count: Int?, showAll: Any?) {
    if (title == null && showAll == null) return
    val nav = LocalNav.current
    Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.s, top = Space.m), verticalAlignment = Alignment.CenterVertically) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (count != null && count > 0) {
            Box(Modifier.padding(start = Space.s).clip(Radius.pill).background(AppTheme.container).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = AppTheme.accent)
            }
        }
        Box(Modifier.weight(1f))
        if (showAll != null) TextButton(onClick = { nav.navigate(showAll) }) { Text(stringResource(Res.string.show_all), color = AppTheme.accent) }
    }
}

@Composable
private fun SectionCard(section: Section) {
    val title = section.title?.let { stringResource(it) }
    if (section is Section.Links && section.carousel) {
        Column {
            SectionHeader(title, null, section.showAll)
            Carousel(section.items)
        }
        return
    }
    AppCard(Modifier.fillMaxWidth().padding(horizontal = Space.gutter)) {
        Column(Modifier.padding(bottom = Space.s).animateContentSize()) {
            when (section) {
                is Section.Facts -> {
                    SectionHeader(title, null, null)
                    FactTiles(section.facts)
                }
                is Section.Text -> {
                    SectionHeader(title, null, null)
                    DataText(localized(section.text.orEmpty()), Modifier.padding(horizontal = Space.l, vertical = Space.s), style = MaterialTheme.typography.bodyMedium)
                }
                is Section.Links -> {
                    SectionHeader(title, if (section.countInTitle && section.showAll == null) section.items.size else null, section.showAll)
                    if (section.items.isEmpty()) {
                        Text(stringResource(Res.string.empty), Modifier.padding(horizontal = Space.l, vertical = Space.s), color = AppTheme.colors.textSecondary)
                    }
                    Collapsible(section.items.size) { limit ->
                        Column(Modifier.padding(horizontal = Space.xs)) {
                            section.items.take(limit).forEach { item ->
                                val video = item.route as? com.chencohensolutions.knesset.ui.VideoPlayerRoute
                                ItemRow(
                                    localized(item.title), item.subtitle, item.status, initials = item.initials, route = item.route, url = item.url,
                                    below = item.bar?.let { b -> { VoteBar(b.first, b.second, b.third) } },
                                    trailing = video?.let { v -> { com.chencohensolutions.knesset.ui.video.DownloadIconButton(v.url, item.title) } },
                                )
                            }
                        }
                    }
                }
                is Section.Groups -> GroupsBody(section, title)
                is Section.Documents -> {
                    SectionHeader(title, section.docs.size, null)
                    Collapsible(section.docs.size) { limit -> Column(Modifier.padding(horizontal = Space.xs)) { section.docs.take(limit).forEach { DocumentRow(it) } } }
                }
                is Section.Tally -> {
                    SectionHeader(title, null, null)
                    Column(Modifier.padding(horizontal = Space.l, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.l)) {
                        VoteSummary(section.tally)
                        if (section.seatResults.isNotEmpty()) {
                            val c = AppTheme.colors
                            HemicycleChart(section.seatResults.map {
                                when (it) {
                                    VoteTally.FOR -> c.positive
                                    VoteTally.AGAINST -> c.negative
                                    VoteTally.ABSTAIN -> c.caution
                                    else -> c.neutral.copy(alpha = 0.5f)
                                }
                            })
                        }
                    }
                }
                is Section.Actions -> {
                    val opener = koinInject<UrlOpener>()
                    SectionHeader(title, null, null)
                    FlowRow(Modifier.padding(horizontal = Space.l, vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        section.links.forEach { a -> FilledTonalButton(onClick = { opener.open(a.url) }) { Text(stringResource(a.label)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupsBody(section: Section.Groups, title: String?) {
    var alternate by rememberSaveable(section.title?.key) { mutableStateOf(false) }
    SectionHeader(title, null, null)
    if (section.alternateLabel != null && section.alternate.isNotEmpty()) {
        Row(Modifier.padding(horizontal = Space.l, vertical = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            ChoicePill(section.primaryLabel?.let { stringResource(it) } ?: "", !alternate, { alternate = false })
            ChoicePill(stringResource(section.alternateLabel), alternate, { alternate = true })
        }
    }
    val groups = if (alternate) section.alternate else section.groups
    groups.forEach { (name, items) ->
        DataText("$name · ${items.size}", Modifier.padding(start = Space.l, end = Space.l, top = Space.m), style = MaterialTheme.typography.titleSmall, color = AppTheme.accent)
        Collapsible(items.size) { limit ->
            Column(Modifier.padding(horizontal = Space.xs)) {
                items.take(limit).forEach { item -> ItemRow(item.title, item.subtitle, item.status, initials = item.initials, route = item.route, url = item.url) }
            }
        }
    }
}

/** Label above value in a two-column grid; long values span both columns; links are accent rows (docs 3.9). */
@Composable
private fun FactTiles(facts: List<Fact>) {
    val nav = LocalNav.current
    val opener = koinInject<UrlOpener>()
    val c = AppTheme.colors
    val (short, long) = facts.partition { it.value.length <= 24 && it.route == null && it.url == null }
    Column(Modifier.padding(horizontal = Space.m, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        short.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                pair.forEach { f -> Tile(f, Modifier.weight(1f)) }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        long.forEach { f ->
            if (f.route != null || f.url != null) {
                Row(
                    Modifier.fillMaxWidth().clip(Radius.tile).background(AppTheme.container)
                        .pressable { if (f.route != null) nav.navigate(f.route) else opener.open(f.url!!) }
                        .padding(Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(f.label), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                        DataText(f.value, style = MaterialTheme.typography.bodyMedium, color = AppTheme.accent)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = AppTheme.accent)
                }
            } else Tile(f, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Tile(f: Fact, modifier: Modifier) {
    val c = AppTheme.colors
    Column(modifier.clip(Radius.tile).background(c.surfaceMuted.copy(alpha = if (c.dark) 1f else 0.6f)).padding(Space.m)) {
        Text(stringResource(f.label), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        DataText(f.value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Compact cards that scroll sideways and snap (Home). */
@Composable
private fun Carousel(items: List<LinkItem>) {
    val nav = LocalNav.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(androidx.compose.foundation.lazy.rememberLazyListState()),
    ) {
        items(items) { item ->
            AppCard(Modifier.width(260.dp), onClick = item.route?.let { r -> { nav.navigate(r) } }) {
                Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    item.subtitle?.let { DataText(it, style = MaterialTheme.typography.labelLarge, color = AppTheme.accent, maxLines = 1) }
                    DataText(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
                }
            }
        }
    }
}

/** Shows the first rows and expands in place (docs 3.10). */
@Composable
private fun Collapsible(size: Int, content: @Composable (limit: Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    content(if (expanded) size else COLLAPSED_ROWS)
    if (!expanded && size > COLLAPSED_ROWS) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.padding(horizontal = Space.s)) {
            Text(stringResource(Res.string.show_all_n, size), color = AppTheme.accent)
        }
    }
}
